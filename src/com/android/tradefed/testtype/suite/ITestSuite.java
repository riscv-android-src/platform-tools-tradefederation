/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.testtype.suite;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.suite.checker.ISystemStatusChecker;
import com.android.tradefed.suite.checker.ISystemStatusCheckerReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.util.StreamUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * Abstract class used to run Test Suite. This class provide the base of how the Suite will be run.
 * Each implementation can define the list of tests via the {@link #loadTests()} method.
 */
public abstract class ITestSuite implements IRemoteTest, IDeviceTest, IBuildReceiver,
        ISystemStatusCheckerReceiver, IStrictShardableTest {

    @Option(name = "bugreport-on-failure",
            description = "Take a bugreport on every test failure. Warning: This may require a lot"
                    + "of storage space of the machine running the tests.")
    private boolean mBugReportOnFailure = false;

    @Option(name = "logcat-on-failure",
            description = "Take a logcat snapshot on every test failure.")
    private boolean mLogcatOnFailure = false;

    @Option(name = "logcat-on-failure-size",
            description = "The max number of logcat data in bytes to capture when "
            + "--logcat-on-failure is on. Should be an amount that can comfortably fit in memory.")
    private int mMaxLogcatBytes = 500 * 1024; // 500K

    @Option(name = "screenshot-on-failure",
            description = "Take a screenshot on every test failure.")
    private boolean mScreenshotOnFailure = false;

    @Option(name = "reboot-on-failure",
            description = "Reboot the device after every test failure.")
    private boolean mRebootOnFailure = false;

    @Option(name = "reboot-per-module",
            description = "Reboot the device before every module run.")
    private boolean mRebootPerModule = false;

    @Option(name = "skip-all-system-status-check",
            description = "Whether all system status check between modules should be skipped")
    private boolean mSkipAllSystemStatusCheck = false;

    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private List<ISystemStatusChecker> mSystemStatusCheckers;

    // Sharding attributes
    private int mShardCount = 1;
    private int mShardIndex = 0;

    /**
     * Abstract method to load the tests configuration that will be run. Each tests is defined by
     * a {@link IConfiguration} and a unique name under which it will report results.
     */
    public abstract LinkedHashMap<String, IConfiguration> loadTests();

    /**
     * Return an instance of the class implementing {@link ITestSuite}.
     */
    private ITestSuite createInstance() {
        try {
            return this.getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Split the list of tests to run however the implementation see fit. Sharding needs to be
     * consistent. It is acceptable to return an empty list if no tests can be run in the shard.
     * <p/>
     * Implement this in order to provide a test suite specific sharding. The default
     * implementation strictly split by number of tests which is not always optimal.
     *
     * @param fullList the initial full list of {@link ModuleDefinition} containing all the tests
     *        that need to run.
     * @param shardCount the total number of shard that need to run.
     * @param shardIndex the index of the current shard that needs to run.
     * @return a list of {@link ModuleDefinition} that need to run in the current shard.
     */
    public List<ModuleDefinition> shardModules(List<ModuleDefinition> fullList,
            int shardCount, int shardIndex) {
        if (shardCount == 1) {
            // Not sharded
            return fullList;
        }
        if (shardIndex >= fullList.size()) {
            // Return empty list when we don't have enough tests for all the shards.
            return new ArrayList<ModuleDefinition>();
        }
        int numPerShard = (int) Math.ceil(fullList.size() / (float)shardCount);
        if (shardIndex == shardCount - 1) {
            // last shard take everything remaining.
            return fullList.subList(shardIndex * numPerShard, fullList.size());
        }
        return fullList.subList(shardIndex * numPerShard, numPerShard + (shardIndex * numPerShard));
    }

    /**
     * Generic run method for all test loaded from {@link #loadTests()}.
     */
    @Override
    final public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        LinkedHashMap<String, IConfiguration> runConfig = loadTests();
        // TODO: validate the xml configuration are "suite" ready: build provider may not be
        // expected to run, etc.
        List<ModuleDefinition> runModules = new ArrayList<>();
        for (Entry<String, IConfiguration> config : runConfig.entrySet()) {
            // TODO: Create on ModuleDefinition per IRemoteTest for easier sharding, but that
            // requires, copying the target_prep to have different instances.
            ModuleDefinition module = new ModuleDefinition(config.getKey(),
                    config.getValue().getTests(), config.getValue().getTargetPreparers());
            module.setDevice(mDevice);
            module.setBuild(mBuildInfo);
            runModules.add(module);
        }

        runModules = shardModules(runModules, mShardCount, mShardIndex);
        if (runModules.isEmpty()) {
            CLog.i("No tests to be run in shard %d out of %d", (mShardIndex + 1), mShardCount);
            return;
        }

        /**
         * Setup a special result forwarded to take actions on test failures.
         */
        listener = new TestFailureListener(listener, getDevice(), mBugReportOnFailure,
                mLogcatOnFailure, mScreenshotOnFailure, mRebootOnFailure, mMaxLogcatBytes);

        // run all modules
        for (ModuleDefinition module : runModules) {
            if (mRebootPerModule) {
                if ("user".equals(mDevice.getProperty("ro.build.type"))) {
                    CLog.e("reboot-per-module should only be used during development, "
                            + "this is a\" user\" build device");
                } else {
                    CLog.d("Rebooting device before starting next module");
                    mDevice.reboot();
                }
            }

            if (!mSkipAllSystemStatusCheck) {
                runPreModuleCheck(module.getId(), mSystemStatusCheckers, mDevice, listener);
            }
            try {
                module.run(listener);
            } catch (DeviceUnresponsiveException due) {
                // being able to catch a DeviceUnresponsiveException here implies that recovery
                // was successful, and test execution should proceed to next module
                ByteArrayOutputStream stack = new ByteArrayOutputStream();
                due.printStackTrace(new PrintWriter(stack, true));
                StreamUtil.close(stack);
                CLog.w("Ignored DeviceUnresponsiveException because recovery was successful, "
                        + "proceeding with next module. Stack trace: %s",
                        stack.toString());
                CLog.w("This may be due to incorrect timeout setting on module %s",
                        module.getId());
            }
            if (!mSkipAllSystemStatusCheck) {
                runPostModuleCheck(module.getId(), mSystemStatusCheckers, mDevice, listener);
            }
        }
    }

    /**
     * Helper to run the System Status checkers preExecutionChecks defined for the test and log
     * their failures.
     */
    private void runPreModuleCheck(String moduleName, List<ISystemStatusChecker> checkers,
            ITestDevice device, ITestLogger logger) throws DeviceNotAvailableException {
        CLog.i("Running system status checker before module execution: %s", moduleName);
        List<String> failures = new ArrayList<>();
        for (ISystemStatusChecker checker : checkers) {
            boolean result = checker.preExecutionCheck(device);
            if (!result) {
                failures.add(checker.getClass().getCanonicalName());
                CLog.w("System status checker [%s] failed", checker.getClass().getCanonicalName());
            }
        }
        if (!failures.isEmpty()) {
            CLog.w("There are failed system status checkers: %s capturing a bugreport",
                    failures.toString());
            InputStreamSource bugSource = device.getBugreport();
            logger.testLog(String.format("bugreport-checker-pre-module-%s", moduleName),
                    LogDataType.BUGREPORT, bugSource);
            bugSource.cancel();
        }
    }

    /**
     * Helper to run the System Status checkers postExecutionCheck defined for the test and log
     * their failures.
     */
    private void runPostModuleCheck(String moduleName, List<ISystemStatusChecker> checkers,
            ITestDevice device, ITestLogger logger) throws DeviceNotAvailableException {
        CLog.i("Running system status checker after module execution: %s", moduleName);
        List<String> failures = new ArrayList<>();
        for (ISystemStatusChecker checker : checkers) {
            boolean result = checker.postExecutionCheck(device);
            if (!result) {
                failures.add(checker.getClass().getCanonicalName());
                CLog.w("System status checker [%s] failed", checker.getClass().getCanonicalName());
            }
        }
        if (!failures.isEmpty()) {
            CLog.w("There are failed system status checkers: %s capturing a bugreport",
                    failures.toString());
            InputStreamSource bugSource = device.getBugreport();
            logger.testLog(String.format("bugreport-checker-post-module-%s", moduleName),
                    LogDataType.BUGREPORT, bugSource);
            bugSource.cancel();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IRemoteTest getTestShard(int shardCount, int shardIndex) {
        ITestSuite test = createInstance();
        test.mShardCount = shardCount;
        test.mShardIndex = shardIndex;
        OptionCopier.copyOptionsNoThrow(this, test);
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Implementation of {@link ITestSuite} may require the build info to load the tests.
     */
    public IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSystemStatusChecker(List<ISystemStatusChecker> systemCheckers) {
        mSystemStatusCheckers = systemCheckers;
    }
}
