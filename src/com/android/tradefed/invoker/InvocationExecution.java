/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.invoker;

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.device.metric.IMetricCollectorReceiver;
import com.android.tradefed.invoker.TestInvocation.Stage;
import com.android.tradefed.invoker.shard.IShardHelper;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.suite.checker.ISystemStatusCheckerReceiver;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.IHostCleaner;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.multi.IMultiTargetPreparer;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IMultiDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.TimeUtil;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;
import java.util.ListIterator;

/**
 * Class that describes all the invocation steps: build download, target_prep, run tests, clean up.
 * Can be extended to override the default behavior of some steps. Order of the steps is driven by
 * {@link TestInvocation}.
 */
public class InvocationExecution implements IInvocationExecution {

    @Override
    public void cleanUpBuilds(IInvocationContext context, IConfiguration config) {
        // Ensure build infos are always cleaned up at the end of invocation.
        for (String cleanUpDevice : context.getDeviceConfigNames()) {
            if (context.getBuildInfo(cleanUpDevice) != null) {
                try {
                    config.getDeviceConfigByName(cleanUpDevice)
                            .getBuildProvider()
                            .cleanUp(context.getBuildInfo(cleanUpDevice));
                } catch (RuntimeException e) {
                    // We catch an simply log exception in cleanUp to avoid missing any final
                    // step of the invocation.
                    CLog.e(e);
                }
            }
        }
    }

    @Override
    public boolean shardConfig(
            IConfiguration config, IInvocationContext context, IRescheduler rescheduler) {
        return createShardHelper().shardConfig(config, context, rescheduler);
    }

    /** Create an return the {@link IShardHelper} to be used. */
    @VisibleForTesting
    protected IShardHelper createShardHelper() {
        return GlobalConfiguration.getInstance().getShardingStrategy();
    }

    @Override
    public void doSetup(
            IInvocationContext context,
            IConfiguration config,
            final ITestInvocationListener listener)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        try {
            // TODO: evaluate doing device setup in parallel
            for (String deviceName : context.getDeviceConfigNames()) {
                ITestDevice device = context.getDevice(deviceName);
                CLog.d("Starting setup for device: '%s'", device.getSerialNumber());
                if (device instanceof ITestLoggerReceiver) {
                    ((ITestLoggerReceiver) context.getDevice(deviceName)).setTestLogger(listener);
                }
                if (!config.getCommandOptions().shouldSkipPreDeviceSetup()) {
                    device.preInvocationSetup(context.getBuildInfo(deviceName));
                }
                for (ITargetPreparer preparer :
                        config.getDeviceConfigByName(deviceName).getTargetPreparers()) {
                    // do not call the preparer if it was disabled
                    if (preparer.isDisabled()) {
                        CLog.d("%s has been disabled. skipping.", preparer);
                        continue;
                    }
                    if (preparer instanceof ITestLoggerReceiver) {
                        ((ITestLoggerReceiver) preparer).setTestLogger(listener);
                    }
                    CLog.d(
                            "starting preparer '%s' on device: '%s'",
                            preparer, device.getSerialNumber());
                    preparer.setUp(device, context.getBuildInfo(deviceName));
                    CLog.d(
                            "done with preparer '%s' on device: '%s'",
                            preparer, device.getSerialNumber());
                }
                CLog.d("Done with setup of device: '%s'", device.getSerialNumber());
            }
            // After all the individual setup, make the multi-devices setup
            for (IMultiTargetPreparer multipreparer : config.getMultiTargetPreparers()) {
                // do not call the preparer if it was disabled
                if (multipreparer.isDisabled()) {
                    CLog.d("%s has been disabled. skipping.", multipreparer);
                    continue;
                }
                if (multipreparer instanceof ITestLoggerReceiver) {
                    ((ITestLoggerReceiver) multipreparer).setTestLogger(listener);
                }
                CLog.d("Starting multi target preparer '%s'", multipreparer);
                multipreparer.setUp(context);
                CLog.d("done with multi target preparer '%s'", multipreparer);
            }
            if (config.getProfiler() != null) {
                config.getProfiler().setUp(context);
            }
        } finally {
            // Note: These metrics are handled in a try in case of a kernel reset or device issue.
            // Setup timing metric. It does not include flashing time on boot tests.
            long setupDuration = System.currentTimeMillis() - start;
            context.addInvocationTimingMetric(IInvocationContext.TimingEvent.SETUP, setupDuration);
            CLog.d("Setup duration: %s'", TimeUtil.formatElapsedTime(setupDuration));
            // Upload the setup logcat after setup is complete.
            for (String deviceName : context.getDeviceConfigNames()) {
                reportLogs(context.getDevice(deviceName), listener, Stage.SETUP);
            }
        }
    }

    @Override
    public void doTeardown(IInvocationContext context, IConfiguration config, Throwable exception)
            throws Throwable {
        Throwable throwable = null;

        List<IMultiTargetPreparer> multiPreparers = config.getMultiTargetPreparers();
        ListIterator<IMultiTargetPreparer> iterator =
                multiPreparers.listIterator(multiPreparers.size());
        while (iterator.hasPrevious()) {
            IMultiTargetPreparer multipreparer = iterator.previous();
            CLog.d("Starting multi target tearDown '%s'", multipreparer);
            multipreparer.tearDown(context, throwable);
            CLog.d("Done with multi target tearDown '%s'", multipreparer);
        }

        // Clear wifi settings, to prevent wifi errors from interfering with teardown process.
        for (String deviceName : context.getDeviceConfigNames()) {
            ITestDevice device = context.getDevice(deviceName);
            device.clearLastConnectedWifiNetwork();
            List<ITargetPreparer> preparers =
                    config.getDeviceConfigByName(deviceName).getTargetPreparers();
            ListIterator<ITargetPreparer> itr = preparers.listIterator(preparers.size());
            while (itr.hasPrevious()) {
                ITargetPreparer preparer = itr.previous();
                if (preparer instanceof ITargetCleaner) {
                    ITargetCleaner cleaner = (ITargetCleaner) preparer;
                    // do not call the cleaner if it was disabled
                    if (cleaner.isDisabled()) {
                        CLog.d("%s has been disabled. skipping.", cleaner);
                        continue;
                    }
                    if (cleaner != null) {
                        try {
                            CLog.d(
                                    "starting tearDown '%s' on device: '%s'",
                                    preparer, device.getSerialNumber());
                            cleaner.tearDown(device, context.getBuildInfo(deviceName), exception);
                            CLog.d(
                                    "done with tearDown '%s' on device: '%s'",
                                    preparer, device.getSerialNumber());
                        } catch (Throwable e) {
                            // We catch it and rethrow later to allow each targetprep to be attempted.
                            // Only the last one will be thrown but all should be logged.
                            CLog.e("Deferring throw for: %s", e);
                            throwable = e;
                        }
                    }
                }
            }
            // Extra tear down step for the device
            if (!config.getCommandOptions().shouldSkipPreDeviceSetup()) {
                device.postInvocationTearDown();
            }
        }

        if (throwable != null) {
            throw throwable;
        }
    }

    @Override
    public void doCleanUp(IInvocationContext context, IConfiguration config, Throwable exception) {
        for (String deviceName : context.getDeviceConfigNames()) {
            List<ITargetPreparer> preparers =
                    config.getDeviceConfigByName(deviceName).getTargetPreparers();
            ListIterator<ITargetPreparer> itr = preparers.listIterator(preparers.size());
            while (itr.hasPrevious()) {
                ITargetPreparer preparer = itr.previous();
                if (preparer instanceof IHostCleaner) {
                    IHostCleaner cleaner = (IHostCleaner) preparer;
                    if (cleaner != null) {
                        cleaner.cleanUp(context.getBuildInfo(deviceName), exception);
                    }
                }
            }
        }
    }

    @Override
    public void runTests(
            IInvocationContext context, IConfiguration config, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // Wrap collectors in each other and collection will be sequential
        ITestInvocationListener listenerWithCollectors = listener;
        for (IMetricCollector collector : config.getMetricCollectors()) {
            listenerWithCollectors = collector.init(context, listenerWithCollectors);
        }

        for (IRemoteTest test : config.getTests()) {
            // For compatibility of those receivers, they are assumed to be single device alloc.
            if (test instanceof IDeviceTest) {
                ((IDeviceTest) test).setDevice(context.getDevices().get(0));
            }
            if (test instanceof IBuildReceiver) {
                ((IBuildReceiver) test).setBuild(context.getBuildInfo(context.getDevices().get(0)));
            }
            if (test instanceof ISystemStatusCheckerReceiver) {
                ((ISystemStatusCheckerReceiver) test)
                        .setSystemStatusChecker(config.getSystemStatusCheckers());
            }

            // TODO: consider adding receivers for only the list of ITestDevice and IBuildInfo.
            if (test instanceof IMultiDeviceTest) {
                ((IMultiDeviceTest) test).setDeviceInfos(context.getDeviceBuildMap());
            }
            if (test instanceof IInvocationContextReceiver) {
                ((IInvocationContextReceiver) test).setInvocationContext(context);
            }
            if (test instanceof IMetricCollectorReceiver) {
                ((IMetricCollectorReceiver) test).setMetricCollectors(config.getMetricCollectors());
                // If test can receive collectors then let it handle the how to set them up
                test.run(listener);
            } else {
                test.run(listenerWithCollectors);
            }
        }
    }

    private void reportLogs(ITestDevice device, ITestInvocationListener listener, Stage stage) {
        if (device == null) {
            return;
        }
        // non stub device
        if (!(device.getIDevice() instanceof StubDevice)) {
            try (InputStreamSource logcatSource = device.getLogcat()) {
                device.clearLogcat();
                String name = TestInvocation.getDeviceLogName(stage);
                listener.testLog(name, LogDataType.LOGCAT, logcatSource);
            }
        }
        // emulator logs
        if (device.getIDevice() != null && device.getIDevice().isEmulator()) {
            try (InputStreamSource emulatorOutput = device.getEmulatorOutput()) {
                // TODO: Clear the emulator log
                String name = TestInvocation.getEmulatorLogName(stage);
                listener.testLog(name, LogDataType.TEXT, emulatorOutput);
            }
        }
    }
}
