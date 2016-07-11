/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.ExistingBuildProvider;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildProvider;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.command.ICommandOptions;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.ILogRegistry;
import com.android.tradefed.log.LogRegistry;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.IShardableListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogSaverResultForwarder;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.DeviceFailedToBootError;
import com.android.tradefed.targetprep.IHostCleaner;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.INativeDeviceTest;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.testtype.IRetriableTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;

import junit.framework.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of {@link ITestInvocation}.
 * <p/>
 * Loads major objects based on {@link IConfiguration}
 *   - retrieves build
 *   - prepares target
 *   - runs tests
 *   - reports results
 */
public class TestInvocation implements ITestInvocation {

    static final String TRADEFED_LOG_NAME = "host_log";
    static final String DEVICE_LOG_NAME = "device_logcat";
    static final String EMULATOR_LOG_NAME = "emulator_log";
    static final String BUILD_ERROR_BUGREPORT_NAME = "build_error_bugreport";
    static final String DEVICE_UNRESPONSIVE_BUGREPORT_NAME = "device_unresponsive_bugreport";
    static final String INVOCATION_ENDED_BUGREPORT_NAME = "invocation_ended_bugreport";
    static final String TARGET_SETUP_ERROR_BUGREPORT_NAME = "target_setup_error_bugreport";
    static final String BATT_TAG = "[battery level]";

    private String mStatus = "(not invoked)";

    /**
     * A {@link ResultForwarder} for forwarding resumed invocations.
     * <p/>
     * It filters the invocationStarted event for the resumed invocation, and sums the invocation
     * elapsed time
     */
    private static class ResumeResultForwarder extends ResultForwarder {

        long mCurrentElapsedTime;

        /**
         * @param listeners
         */
        public ResumeResultForwarder(List<ITestInvocationListener> listeners,
                long currentElapsedTime) {
            super(listeners);
            mCurrentElapsedTime = currentElapsedTime;
        }

        @Override
        public void invocationStarted(IBuildInfo buildInfo) {
            // ignore
        }

        @Override
        public void invocationEnded(long newElapsedTime) {
            super.invocationEnded(mCurrentElapsedTime + newElapsedTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invoke(ITestDevice device, IConfiguration config, IRescheduler rescheduler,
            ITestInvocationListener... extraListeners)
            throws DeviceNotAvailableException, Throwable {
        List<ITestInvocationListener> allListeners = new ArrayList<ITestInvocationListener>(
                config.getTestInvocationListeners().size() + extraListeners.length);
        allListeners.addAll(config.getTestInvocationListeners());
        allListeners.addAll(Arrays.asList(extraListeners));
        ITestInvocationListener listener = new LogSaverResultForwarder(config.getLogSaver(),
                allListeners);

        IBuildInfo info = null;

        try {
            mStatus = "fetching build";
            config.getLogOutput().init();
            getLogRegistry().registerLogger(config.getLogOutput());
            device.clearLastConnectedWifiNetwork();
            device.setOptions(config.getDeviceOptions());
            if (config.getDeviceOptions().isLogcatCaptureEnabled()) {
                device.startLogcat();
            }
            String cmdLineArgs = config.getCommandLine();
            if (cmdLineArgs != null) {
                CLog.i("Invocation was started with cmd: %s", cmdLineArgs);
            }
            if (config.getBuildProvider() instanceof IDeviceBuildProvider) {
                info = ((IDeviceBuildProvider)config.getBuildProvider()).getBuild(device);
            } else {
                info = config.getBuildProvider().getBuild();
            }
            if (info != null) {
                updateBuild(info, config);
                injectBuild(info, config.getTests());
                if (shardConfig(config, info, rescheduler)) {
                    CLog.i("Invocation for %s has been sharded, rescheduling",
                            device.getSerialNumber());
                } else {
                    updateConfigIfSharded(config);
                    device.setRecovery(config.getDeviceRecovery());
                    performInvocation(config, device, info, rescheduler, listener);
                    return;
                }
            } else {
                mStatus = "(no build to test)";
                CLog.d("No build to test");
                rescheduleTest(config, rescheduler);
                // save current log contents to global log
                getLogRegistry().dumpToGlobalLog(config.getLogOutput());
            }
        } catch (BuildRetrievalError e) {
            CLog.e(e);
            // report an empty invocation, so this error is sent to listeners
            startInvocation(config, device, e.getBuildInfo(), listener);
            // don't want to use #reportFailure, since that will call buildNotTested
            listener.invocationFailed(e);
            reportLogs(device, listener, config.getLogOutput());
            listener.invocationEnded(0);
            return;
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            // ensure we always deregister the logger
            device.stopLogcat();
            getLogRegistry().unregisterLogger();
            config.getLogOutput().closeLog();
        }
    }

    /**
     * Pass the build to any {@link IBuildReceiver} tests
     * @param buildInfo
     * @param tests
     */
    private void injectBuild(IBuildInfo buildInfo, List<IRemoteTest> tests) {
        for (IRemoteTest test : tests) {
            if (test instanceof IBuildReceiver) {
                ((IBuildReceiver)test).setBuild(buildInfo);
            }
        }
    }

    /**
     * Attempt to shard the configuration into sub-configurations, to be re-scheduled to run on
     * multiple resources in parallel.
     * <p/>
     * If a shard count is greater than 1, it will simply create configs for each shard by setting
     * shard indices and reschedule them.
     * If a shard count is not set,it would fallback to {@link #legacyShardConfig}.
     *
     * @param config the current {@link IConfiguration}.
     * @param info the {@link IBuildInfo} to test
     * @param rescheduler the {@link IRescheduler}
     * @return true if test was sharded. Otherwise return <code>false</code>
     */
    private boolean shardConfig(IConfiguration config, IBuildInfo info, IRescheduler rescheduler) {
        if (config.getCommandOptions().getShardIndex() != null) {
            // The config is already for a single shard.
            return false;
        }

        mStatus = "sharding";
        if (config.getCommandOptions().getShardCount() == null) {
            return legacyShardConfig(config, info, rescheduler);
        }
        // Schedules shard configs.
        int shardCount = config.getCommandOptions().getShardCount();
        for (int i = 0; i < config.getCommandOptions().getShardCount(); i++) {
            IConfiguration shardConfig = null;
            // Create a deep copy of the configuration.
            try {
                shardConfig = getConfigFactory().createConfigurationFromArgs(
                        QuotationAwareTokenizer.tokenizeLine(config.getCommandLine()));
            } catch (ConfigurationException e) {
                // This must not happen.
                throw new RuntimeException("failed to deep copy a configuration", e);
            }
            shardConfig.setBuildProvider(new ExistingBuildProvider(info.clone(),
                    config.getBuildProvider()));
            shardConfig.getCommandOptions().setShardCount(shardCount);
            shardConfig.getCommandOptions().setShardIndex(i);
            rescheduler.scheduleConfig(shardConfig);
        }
        return true;
    }

    /**
     * Factory method for getting a reference to the {@link IConfigurationFactory}
     *
     * @return the {@link IConfigurationFactory} to use
     */
    protected IConfigurationFactory getConfigFactory() {
        return ConfigurationFactory.getInstance();
    }

    /**
     * Attempt to shard the configuration into sub-configurations, to be re-scheduled to run on
     * multiple resources in parallel.
     * <p/>
     * A successful shard action renders the current config empty, and invocation should not proceed.
     *
     * @see IShardableTest
     * @see IRescheduler
     *
     * @param config the current {@link IConfiguration}.
     * @param info the {@link IBuildInfo} to test
     * @param rescheduler the {@link IRescheduler}
     * @return true if test was sharded. Otherwise return <code>false</code>
     */
    private boolean legacyShardConfig(IConfiguration config, IBuildInfo info, IRescheduler rescheduler) {
        List<IRemoteTest> shardableTests = new ArrayList<IRemoteTest>();
        boolean isSharded = false;
        for (IRemoteTest test : config.getTests()) {
            isSharded |= shardTest(shardableTests, test);
        }
        if (isSharded) {
            // shard this invocation!

            // create the TestInvocationListener that will collect results from all the shards,
            // and forward them to the original set of listeners (minus any ISharddableListeners)
            // once all shards complete
            ShardMasterResultForwarder resultCollector = new ShardMasterResultForwarder(
                    config.getLogSaver(), buildMasterShardListeners(config), shardableTests.size());

            // report invocation started using original buildinfo
            resultCollector.invocationStarted(info);
            for (IRemoteTest testShard : shardableTests) {
                CLog.i("Rescheduling sharded config...");
                IConfiguration shardConfig = config.clone();
                shardConfig.setTest(testShard);
                shardConfig.setBuildProvider(new ExistingBuildProvider(info.clone(),
                        config.getBuildProvider()));

                shardConfig.setTestInvocationListeners(
                        buildShardListeners(resultCollector, config.getTestInvocationListeners()));
                shardConfig.setLogOutput(config.getLogOutput().clone());
                shardConfig.setCommandOptions(config.getCommandOptions().clone());
                // use the same {@link ITargetPreparer}, {@link IDeviceRecovery} etc as original
                // config
                rescheduler.scheduleConfig(shardConfig);
            }
            // clean up original build
            config.getBuildProvider().cleanUp(info);
            return true;
        }
        return false;
    }

    /**
     * Builds the {@link ITestInvocationListener} listeners that will collect the results from
     * all shards. Currently excludes {@link IShardableListener}s.
     */
    private List<ITestInvocationListener> buildMasterShardListeners(IConfiguration config) {
        List<ITestInvocationListener> newListeners = new ArrayList<ITestInvocationListener>();
        for (ITestInvocationListener l : config.getTestInvocationListeners()) {
            if (!(l instanceof IShardableListener)) {
                newListeners.add(l);
            }
        }
        return newListeners;
    }

    /**
     * Builds the list of {@link ITestInvocationListener}s for each shard.
     * Currently includes any {@link IShardableListener}, plus a single listener that will forward
     * results to the master shard collector.
     */
    private List<ITestInvocationListener> buildShardListeners(
            ITestInvocationListener resultCollector, List<ITestInvocationListener> origListeners) {
        List<ITestInvocationListener> shardListeners = new ArrayList<ITestInvocationListener>();
        for (ITestInvocationListener l : origListeners) {
            if (l instanceof IShardableListener) {
                shardListeners.add(((IShardableListener)l).clone());
            }
        }
        ShardListener origConfigListener = new ShardListener(resultCollector);
        shardListeners.add(origConfigListener);
        return shardListeners;
    }

    /**
     * Attempt to shard given {@link IRemoteTest}.
     *
     * @param shardableTests the list of {@link IRemoteTest}s to add to
     * @param test the {@link Test} to shard
     * @return <code>true</code> if test was sharded
     */
    private boolean shardTest(List<IRemoteTest> shardableTests, IRemoteTest test) {
        boolean isSharded = false;
        if (test instanceof IShardableTest) {
            IShardableTest shardableTest = (IShardableTest)test;
            Collection<IRemoteTest> shards = shardableTest.split();
            if (shards != null) {
                shardableTests.addAll(shards);
                isSharded = true;
            }
        }
        if (!isSharded) {
            shardableTests.add(test);
        }
        return isSharded;
    }

    /**
     * Update the {@link IBuildInfo} with additional info from the {@link IConfiguration}.
     *
     * @param info the {@link IBuildInfo}
     * @param config the {@link IConfiguration}
     */
    private void updateBuild(IBuildInfo info, IConfiguration config) {
        if (config.getCommandLine() != null) {
            // TODO: Store this in an invocation metadata class later
            info.addBuildAttribute("command_line_args", config.getCommandLine());
        }
        if (config.getCommandOptions().getShardCount() != null) {
            info.addBuildAttribute("shard_count",
                    config.getCommandOptions().getShardCount().toString());
        }
        if (config.getCommandOptions().getShardIndex() != null) {
            info.addBuildAttribute("shard_index",
                    config.getCommandOptions().getShardIndex().toString());
        }
    }

    /**
     * Updates the {@link IConfiguration} to run a single shard if a shard index is set.
     *
     * @see IStrctiShardableTest
     *
     * @param config the {@link IConfiguration}.
     */
    private void updateConfigIfSharded(IConfiguration config) {
        if (config.getCommandOptions().getShardIndex() == null) {
            return;
        }

        int shardCount = config.getCommandOptions().getShardCount();
        int shardIndex = config.getCommandOptions().getShardIndex();
        List<IRemoteTest> testShards = new ArrayList<IRemoteTest>();
        for (IRemoteTest test : config.getTests()) {
            if (!(test instanceof IStrictShardableTest)) {
                CLog.w("%s is not shardable; the whole test will run in shard 0",
                        test.getClass().getName());
                if (shardIndex == 0) {
                    testShards.add(test);
                }
                continue;
            }
            IRemoteTest testShard = ((IStrictShardableTest) test).getTestShard(shardCount,
                    shardIndex);
            testShards.add(testShard);
        }
        config.setTests(testShards);
    }

    /**
     * Display a log message informing the user of a invocation being started.
     *
     * @param info the {@link IBuildInfo}
     * @param device the {@link ITestDevice}
     * @param config the {@link IConfiguration}
     */
    private void logStartInvocation(IBuildInfo info, ITestDevice device, IConfiguration config) {
        String shardSuffix = "";
        if (config.getCommandOptions().getShardIndex() != null) {
            shardSuffix = String.format(" (shard %d of %d)",
                    config.getCommandOptions().getShardIndex(),
                    config.getCommandOptions().getShardCount());
        }
        StringBuilder msg = new StringBuilder("Starting invocation for '");
        msg.append(info.getTestTag());
        msg.append("'");
        if (!IBuildInfo.UNKNOWN_BUILD_ID.equals(info.getBuildId())) {
            msg.append(" on build ");
            msg.append(getBuildDescription(info));
        }
        for (String buildAttr : info.getBuildAttributes().values()) {
            msg.append(" ");
            msg.append(buildAttr);
        }
        msg.append(shardSuffix);
        msg.append(" on device ");
        msg.append(device.getSerialNumber());
        CLog.logAndDisplay(LogLevel.INFO, msg.toString());
        mStatus = String.format("running %s on build %s", info.getTestTag(),
                getBuildDescription(info)) + shardSuffix;
    }

    /**
     * Returns a user-friendly description of the build
     */
    private String getBuildDescription(IBuildInfo info) {
        return String.format("'%s'", buildSpacedString(info.getBuildBranch(),
                info.getBuildFlavor(), info.getBuildId()));
    }

    /**
     * Helper method for adding space delimited sequence of strings. Will ignore null segments
     */
    private String buildSpacedString(String... segments) {
        StringBuilder sb = new StringBuilder();
        for (String s : segments) {
            if (s != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(s);
            }
        }
        return sb.toString();
    }

    /**
     * Performs the invocation
     *
     * @param config the {@link IConfiguration}
     * @param device the {@link ITestDevice} to use. May be <code>null</code>
     * @param info the {@link IBuildInfo}
     */
    private void performInvocation(IConfiguration config, ITestDevice device, IBuildInfo info,
            IRescheduler rescheduler, ITestInvocationListener listener) throws Throwable {

        boolean resumed = false;
        String bugreportName = null;
        long startTime = System.currentTimeMillis();
        long elapsedTime = -1;
        Throwable exception = null;
        Throwable tearDownException = null;

        info.setDeviceSerial(device.getSerialNumber());
        startInvocation(config, device, info, listener);
        try {
            logDeviceBatteryLevel(device, "initial");
            prepareAndRun(config, device, info, listener);
        } catch (BuildError e) {
            exception = e;
            CLog.w("Build %s failed on device %s. Reason: %s", info.getBuildId(),
                    device.getSerialNumber(), e.toString());
            bugreportName = BUILD_ERROR_BUGREPORT_NAME;
            if (e instanceof DeviceFailedToBootError) {
                device.setRecoveryMode(RecoveryMode.NONE);
            }
            reportFailure(e, listener, config, info, rescheduler);
        } catch (TargetSetupError e) {
            exception = e;
            CLog.e("Caught exception while running invocation");
            CLog.e(e);
            bugreportName = TARGET_SETUP_ERROR_BUGREPORT_NAME;
            reportFailure(e, listener, config, info, rescheduler);
        } catch (DeviceNotAvailableException e) {
            exception = e;
            // log a warning here so its captured before reportLogs is called
            CLog.w("Invocation did not complete due to device %s becoming not available. " +
                    "Reason: %s", device.getSerialNumber(), e.getMessage());
            if ((e instanceof DeviceUnresponsiveException)
                    && TestDeviceState.ONLINE.equals(device.getDeviceState())) {
                // under certain cases it might still be possible to grab a bugreport
                bugreportName = DEVICE_UNRESPONSIVE_BUGREPORT_NAME;
            }
            resumed = resume(config, info, rescheduler, System.currentTimeMillis() - startTime);
            if (!resumed) {
                reportFailure(e, listener, config, info, rescheduler);
            } else {
                CLog.i("Rescheduled failed invocation for resume");
            }
            // Upon reaching here after an exception, it is safe to assume that recovery
            // has already been attempted so we disable it to avoid re-entry during clean up.
            device.setRecoveryMode(RecoveryMode.NONE);
            throw e;
        } catch (RunInterruptedException e) {
            CLog.w("Invocation interrupted");
            reportFailure(e, listener, config, info, rescheduler);
        } catch (AssertionError e) {
            exception = e;
            CLog.e("Caught AssertionError while running invocation: %s", e.toString());
            CLog.e(e);
            reportFailure(e, listener, config, info, rescheduler);
        } catch (Throwable t) {
            exception = t;
            // log a warning here so its captured before reportLogs is called
            CLog.e("Unexpected exception when running invocation: %s", t.toString());
            CLog.e(t);
            reportFailure(t, listener, config, info, rescheduler);
            throw t;
        } finally {
            getRunUtil().allowInterrupt(false);
            if (config.getCommandOptions().takeBugreportOnInvocationEnded()) {
                if (bugreportName != null) {
                    CLog.i("Bugreport to be taken for failure instead of invocation ended.");
                } else {
                    bugreportName = INVOCATION_ENDED_BUGREPORT_NAME;
                }
            }
            if (bugreportName != null) {
                takeBugreport(device, listener, bugreportName);
            }
            mStatus = "tearing down";
            try {
                doTeardown(config, device, info, exception);
            } catch (Throwable e) {
                tearDownException = e;
                CLog.e("Exception when tearing down invocation: %s", tearDownException.toString());
                CLog.e(tearDownException);
                if (exception == null) {
                    // only report when the exception is new during tear down
                    reportFailure(tearDownException, listener, config, info, rescheduler);
                }
            }
            mStatus = "done running tests";
            try {
                // Clean up host.
                doCleanUp(config, info, exception);
                reportLogs(device, listener, config.getLogOutput());
                elapsedTime = System.currentTimeMillis() - startTime;
                if (!resumed) {
                    listener.invocationEnded(elapsedTime);
                }
            } finally {
                config.getBuildProvider().cleanUp(info);
            }
        }
        if (tearDownException != null) {
            // this means a DNAE or RTE has happened during teardown, need to throw
            // if there was a preceding RTE or DNAE stored in 'exception', it would have already
            // been thrown before exiting the previous try...catch...finally block
            throw tearDownException;
        }
    }

    /**
     * Do setup, run the tests, then call tearDown
     */
    private void prepareAndRun(IConfiguration config, ITestDevice device, IBuildInfo info,
            ITestInvocationListener listener) throws Throwable {
        getRunUtil().allowInterrupt(true);
        logDeviceBatteryLevel(device, "initial -> setup");
        doSetup(config, device, info, listener);
        logDeviceBatteryLevel(device, "setup -> test");
        runTests(device, config, listener);
        logDeviceBatteryLevel(device, "after test");
    }

    private void doSetup(IConfiguration config, ITestDevice device, IBuildInfo info,
            final ITestInvocationListener listener)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (device instanceof ITestLoggerReceiver) {
            ((ITestLoggerReceiver) device).setTestLogger(listener);
        }
        device.preInvocationSetup(info);

        for (ITargetPreparer preparer : config.getTargetPreparers()) {
            if (preparer instanceof ITestLoggerReceiver) {
                ((ITestLoggerReceiver) preparer).setTestLogger(listener);
            }
            preparer.setUp(device, info);
        }
    }

    private void doTeardown(IConfiguration config, ITestDevice device, IBuildInfo info,
            Throwable exception) throws Throwable {
        // Clear wifi settings, to prevent wifi errors from interfering with teardown process.
        device.clearLastConnectedWifiNetwork();
        List<ITargetPreparer> preparers = config.getTargetPreparers();
        ListIterator<ITargetPreparer> itr = preparers.listIterator(preparers.size());
        Throwable throwable = null;
        while (itr.hasPrevious()) {
            ITargetPreparer preparer = itr.previous();
            if(preparer instanceof ITargetCleaner) {
                ITargetCleaner cleaner = (ITargetCleaner) preparer;
                if (cleaner != null) {
                    try {
                        cleaner.tearDown(device, info, exception);
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
        device.postInvocationTearDown();

        if (throwable != null) {
            throw throwable;
        }
    }

    private void doCleanUp(IConfiguration config, IBuildInfo info, Throwable exception) {
        List<ITargetPreparer> preparers = config.getTargetPreparers();
        ListIterator<ITargetPreparer> itr = preparers.listIterator(preparers.size());
        while (itr.hasPrevious()) {
            ITargetPreparer preparer = itr.previous();
            if (preparer instanceof IHostCleaner) {
                IHostCleaner cleaner = (IHostCleaner) preparer;
                if (cleaner != null) {
                    cleaner.cleanUp(info, exception);
                }
            }
        }
    }

    /**
     * Starts the invocation.
     * <p/>
     * Starts logging, and informs listeners that invocation has been started.
     *
     * @param config
     * @param device
     * @param info
     */
    private void startInvocation(IConfiguration config, ITestDevice device, IBuildInfo info,
            ITestInvocationListener listener) {
        logStartInvocation(info, device, config);
        listener.invocationStarted(info);
    }

    /**
     * Attempt to reschedule the failed invocation to resume where it left off.
     * <p/>
     * @see IResumableTest
     *
     * @param config
     * @return <code>true</code> if invocation was resumed successfully
     */
    private boolean resume(IConfiguration config, IBuildInfo info, IRescheduler rescheduler,
            long elapsedTime) {
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof IResumableTest) {
                IResumableTest resumeTest = (IResumableTest)test;
                if (resumeTest.isResumable()) {
                    // resume this config if any test is resumable
                    IConfiguration resumeConfig = config.clone();
                    // reuse the same build for the resumed invocation
                    IBuildInfo clonedBuild = info.clone();
                    resumeConfig.setBuildProvider(new ExistingBuildProvider(clonedBuild,
                            config.getBuildProvider()));
                    // create a result forwarder, to prevent sending two invocationStarted events
                    resumeConfig.setTestInvocationListener(new ResumeResultForwarder(
                            config.getTestInvocationListeners(), elapsedTime));
                    resumeConfig.setLogOutput(config.getLogOutput().clone());
                    resumeConfig.setCommandOptions(config.getCommandOptions().clone());
                    boolean canReschedule = rescheduler.scheduleConfig(resumeConfig);
                    if (!canReschedule) {
                        CLog.i("Cannot reschedule resumed config for build %s. Cleaning up build.",
                                info.getBuildId());
                        resumeConfig.getBuildProvider().cleanUp(clonedBuild);
                    }
                    // FIXME: is it a bug to return from here, when we may not have completed the
                    // FIXME: config.getTests iteration?
                    return canReschedule;
                }
            }
        }
        return false;
    }

    private void reportFailure(Throwable exception, ITestInvocationListener listener,
            IConfiguration config, IBuildInfo info, IRescheduler rescheduler) {
        listener.invocationFailed(exception);
        if (!(exception instanceof BuildError) && !(exception.getCause() instanceof BuildError)) {
            config.getBuildProvider().buildNotTested(info);
            rescheduleTest(config, rescheduler);
        }
    }

    private void rescheduleTest(IConfiguration config, IRescheduler rescheduler) {
        for (IRemoteTest test : config.getTests()) {
            if (!config.getCommandOptions().isLoopMode() && test instanceof IRetriableTest &&
                    ((IRetriableTest) test).isRetriable()) {
                rescheduler.rescheduleCommand();
                return;
            }
        }
    }

    private void reportLogs(ITestDevice device, ITestInvocationListener listener,
            ILeveledLogOutput logger) {
        InputStreamSource logcatSource = null;
        InputStreamSource globalLogSource = logger.getLog();
        InputStreamSource emulatorOutput = null;
        if (device != null) {
            logcatSource = device.getLogcat();
            if (device.getIDevice() != null && device.getIDevice().isEmulator()) {
                emulatorOutput = device.getEmulatorOutput();
            }
        }

        if (logcatSource != null) {
            listener.testLog(DEVICE_LOG_NAME, LogDataType.LOGCAT, logcatSource);
        }
        if (emulatorOutput != null) {
            listener.testLog(EMULATOR_LOG_NAME, LogDataType.TEXT, emulatorOutput);
        }
        listener.testLog(TRADEFED_LOG_NAME, LogDataType.TEXT, globalLogSource);

        // Clean up after our ISSen
        if (logcatSource != null) {
            logcatSource.cancel();
        }
        if (emulatorOutput != null) {
            emulatorOutput.cancel();
        }
        globalLogSource.cancel();
    }

    private void takeBugreport(ITestDevice device, ITestInvocationListener listener,
            String bugreportName) {
        if (device == null) {
            return;
        }
        if (device.getIDevice() instanceof StubDevice) {
            return;
        }

        InputStreamSource bugreport = device.getBugreport();
        try {
            listener.testLog(bugreportName, LogDataType.BUGREPORT, bugreport);
        } finally {
            bugreport.cancel();
        }
    }

    /**
     * Gets the {@link ILogRegistry} to use.
     * <p/>
     * Exposed for unit testing.
     */
    ILogRegistry getLogRegistry() {
        return LogRegistry.getLogRegistry();
    }

    /**
     * Utility method to fetch the default {@link IRunUtil} singleton
     * <p />
     * Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Runs the test.
     *
     * @param device the {@link ITestDevice} to run tests on
     * @param config the {@link IConfiguration} to run
     * @param listener the {@link ITestInvocationListener} of test results
     * @throws DeviceNotAvailableException
     */
    private void runTests(ITestDevice device, IConfiguration config,
            ITestInvocationListener listener) throws DeviceNotAvailableException {
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof IDeviceTest) {
                ((IDeviceTest)test).setDevice(device);
            }
            if (test instanceof INativeDeviceTest) {
                ((INativeDeviceTest)test).setDevice(device);
            }
            test.run(listener);
        }
    }

    @Override
    public String toString() {
        return mStatus;
    }

    private void logDeviceBatteryLevel(ITestDevice testDevice, String event) {
        if (testDevice == null) {
            return;
        }
        IDevice device = testDevice.getIDevice();
        if (device == null) {
            return;
        }
        try {
            CLog.v("%s - %s - %d%%", BATT_TAG, event,
                    device.getBattery(500, TimeUnit.MILLISECONDS).get());
            return;
        } catch (InterruptedException | ExecutionException e) {
            // fall through
        }

        CLog.v("Failed to get battery level");
    }
}
