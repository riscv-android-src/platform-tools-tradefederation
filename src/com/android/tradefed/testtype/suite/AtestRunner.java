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
package com.android.tradefed.testtype.suite;

import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.SubprocessResultsReporter;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.AbiUtils;

import java.util.LinkedHashMap;
import java.util.List;


/** Implementation of {@link ITestSuite} */
public class AtestRunner extends BaseTestSuite {

    @Option(
        name = "wait-for-debugger",
        description = "For InstrumentationTests, we pass debug to the instrumentation run."
    )
    private boolean mDebug = false;

    @Option(
        name = "disable-target-preparers",
        description =
                "Skip the target preparer steps enumerated in test config. Skips the teardown step "
                        + "as well."
    )
    private boolean mSkipSetUp = false;

    @Option(name = "disable-teardown", description = "Skip the teardown of the target preparers.")
    private boolean mSkipTearDown = false;

    @Option(
        name = "abi-name",
        description =
                "Abi to pass to tests that require an ABI. The device default will be used if not specified."
    )
    private String mabiName;

    @Option(
        name = "subprocess-report-port",
        description = "the port where to connect to send the" + "events."
    )
    private Integer mReportPort = null;

    @Override
    public LinkedHashMap<String, IConfiguration> loadTests() {
        LinkedHashMap<String, IConfiguration> configMap = super.loadTests();
        IAbi abi = getAbi();
        for (IConfiguration testConfig : configMap.values()) {
            if (mSkipSetUp || mSkipTearDown) {
                disableTargetPreparers(testConfig, mSkipSetUp, mSkipTearDown);
            }
            if (mDebug) {
                addDebugger(testConfig);
            }
            setTestAbi(testConfig, abi);
        }

        return configMap;
    }

    /** Return a ConfigurationFactory instance. Organized this way for testing purposes. */
    public IConfigurationFactory loadConfigFactory() {
        return ConfigurationFactory.getInstance();
    }

    /** {@inheritDoc} */
    @Override
    protected List<ITestInvocationListener> createModuleListeners() {
        List<ITestInvocationListener> listeners = super.createModuleListeners();
        if (mReportPort != null) {
            SubprocessResultsReporter subprocessResult = new SubprocessResultsReporter();
            OptionCopier.copyOptionsNoThrow(this, subprocessResult);
            listeners.add(subprocessResult);
        }
        return listeners;
    }

    /**
     * Helper to create the IAbi instance to pass to tests that implement IAbiReceiver.
     *
     * @return IAbi instance to use, may be null if not provided and device is unreachable.
     */
    private IAbi getAbi() {
        if (mabiName == null) {
            if (getDevice() == null) {
                return null;
            }
            try {
                mabiName = AbiFormatter.getDefaultAbi(getDevice(), "");
            } catch (DeviceNotAvailableException e) {
                return null;
            }
        }
        return new Abi(mabiName, AbiUtils.getBitness(mabiName));
    }

    /**
     * Set ABI of tests and target preparers that require it to default ABI of device.
     *
     * @param testConfig The configuration to set the ABI for.
     * @param abi The IAbi instance to pass to setAbi() of tests and target preparers.
     */
    private void setTestAbi(IConfiguration testConfig, IAbi abi) {
        if (abi == null) {
            return;
        }
        List<IRemoteTest> tests = testConfig.getTests();
        for (IRemoteTest test : tests) {
            if (test instanceof IAbiReceiver) {
                ((IAbiReceiver) test).setAbi(abi);
            }
        }
        for (ITargetPreparer targetPreparer : testConfig.getTargetPreparers()) {
            if (targetPreparer instanceof IAbiReceiver) {
                ((IAbiReceiver) targetPreparer).setAbi(abi);
            }
        }
    }

    /** Helper to attach the debugger to any Instrumentation tests in the config. */
    private void addDebugger(IConfiguration testConfig) {
        for (IRemoteTest test : testConfig.getTests()) {
            if (test instanceof InstrumentationTest) {
                ((InstrumentationTest) test).setDebug(true);
            }
        }
    }

    /** Helper to disable TargetPreparers of a test. */
    private void disableTargetPreparers(
            IConfiguration testConfig, boolean skipSetUp, boolean skipTearDown) {
        for (ITargetPreparer targetPreparer : testConfig.getTargetPreparers()) {
            if (skipSetUp) {
                CLog.d(
                        "%s: Disabling Target Preparer (%s)",
                        testConfig.getName(), targetPreparer.getClass().getSimpleName());
                targetPreparer.setDisable(true);
            } else if (skipTearDown) {
                CLog.d(
                        "%s: Disabling Target Preparer TearDown (%s)",
                        testConfig.getName(), targetPreparer.getClass().getSimpleName());
                targetPreparer.setDisableTearDown(true);
            }
        }
    }
}
