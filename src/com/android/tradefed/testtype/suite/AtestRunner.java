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

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.ConfigurationUtil;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.AbiUtils;

import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.gson.Gson;


/** Implementation of {@link ITestSuite} */
public class AtestRunner extends ITestSuite {

    private static final Pattern CONFIG_RE =
            Pattern.compile(".*/(?<config>[^/]+).config", Pattern.CASE_INSENSITIVE);

    protected class TestInfo {
        protected String test = null;
        protected String[] filters = null;
    }

    @Option(name = "test-info-file", description = "File with info about the tests to run.")
    private String mTestInfoFile;

    @Option(
        name = "wait-for-debugger",
        description = "For InstrumentationTests, we pass debug to the instrumentation run."
    )
    private boolean mDebug = false;

    @Option(
        name = "disable-target-preparers",
        description = "Skip the target preparer steps enumerated in test config."
    )
    private boolean mSkipInstall = false;

    @Option(
        name = "abi-name",
        description =
                "Abi to pass to tests that require an ABI. The device default will be used if not specified."
    )
    private String mabiName;

    @Override
    public LinkedHashMap<String, IConfiguration> loadTests() {
        LinkedHashMap<String, IConfiguration> configMap =
                new LinkedHashMap<String, IConfiguration>();
        IConfigurationFactory configFactory = loadConfigFactory();
        List<String> configs = configFactory.getConfigList(null, false);
        if (getBuildInfo() instanceof IDeviceBuildInfo) {
            IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) getBuildInfo();
            File testsDir = deviceBuildInfo.getTestsDir();
            if (testsDir != null) {
                CLog.d(
                        "Loading extra test configs from the tests directory: %s",
                        testsDir.getAbsolutePath());
                List<File> extraTestCasesDirs = Arrays.asList(testsDir);
                List<String> builtConfigs =
                        new ArrayList<String>(
                                ConfigurationUtil.getConfigNamesFromDirs(null, extraTestCasesDirs));
                for (String configName : builtConfigs) {
                    configs.add(canonicalizeConfigName(configName));
                }
            }
        }
        TestInfo[] testInfos = loadTestInfoFile(mTestInfoFile);
        IAbi abi = getAbi();
        for (TestInfo testInfo : testInfos) {
            try {
                CLog.d("Adding testConfig: %s", testInfo.test);
                IConfiguration testConfig =
                        configFactory.createConfigurationFromArgs(new String[] {testInfo.test});
                for (String filter : testInfo.filters) {
                    addFilter(testConfig, filter);
                }
                if (mSkipInstall) {
                    disableTargetPreparers(testConfig);
                }
                if (mDebug) {
                    addDebugger(testConfig);
                }
                setTestAbi(testConfig, abi);
                configMap.put(testInfo.test, testConfig);
            } catch (ConfigurationException | NoClassDefFoundError e) {
                CLog.e(
                        "Skipping configuration '%s', because of loading ERROR: %s",
                        testInfo.test, e);
            }
        }
        return configMap;
    }

    /**
     * Non-integrated modules have full file paths as their name, .e.g /foo/bar/name.config, but all
     * we want is the name.
     */
    public String canonicalizeConfigName(String originalName) {
        Matcher match = CONFIG_RE.matcher(originalName);
        if (match.find()) {
            return match.group("config");
        }
        return originalName;
    }

    /** Return a ConfigurationFactory instance. Organized this way for testing purposes. */
    public IConfigurationFactory loadConfigFactory() {
        return ConfigurationFactory.getInstance();
    }

    /**
     * Load the TestInfo data from the test_info.json file.
     *
     * @param filePath The path to the json file containing test info.
     * @return Array of TestInfo instances.
     */
    public TestInfo[] loadTestInfoFile(String filePath) {
        CLog.d("Loading test info file: %s", filePath);
        TestInfo[] testInfos = new TestInfo[1];
        try {
            Gson gson = new Gson();
            testInfos = gson.fromJson(new FileReader(filePath), TestInfo[].class);
        } catch (FileNotFoundException e) {
            CLog.e("Aborting all tests, could not find test_info file: %s", filePath);
            CLog.d("Error: %e", e);
        }
        return testInfos;
    }

    /**
     * Add filter to the tests in an IConfiguration.
     *
     * @param testConfig The configuration containing tests to filter.
     * @param filter The filter to add to the tests in the testConfig.
     */
    public void addFilter(IConfiguration testConfig, String filter) {
        List<IRemoteTest> tests = testConfig.getTests();
        for (IRemoteTest test : tests) {
            if (test instanceof ITestFilterReceiver) {
                CLog.d(
                        "%s:%s - Applying filter (%s)",
                        testConfig.getName(), test.getClass().getSimpleName(), filter);
                ((ITestFilterReceiver) test).addIncludeFilter(filter);
            } else {
                CLog.e(
                        "Test Class (%s) does not support filtering. Cannot apply filter: %s.\n"
                                + "Please update test to use a class that implements ITestFilterReceiver. Running entire"
                                + "test module instead.",
                        test.getClass().getSimpleName(), filter);
            }
        }
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
    private void disableTargetPreparers(IConfiguration testConfig) {
        for (ITargetPreparer targetPreparer : testConfig.getTargetPreparers()) {
            CLog.d(
                    "%s: Disabling Target Preparer (%s)",
                    testConfig.getName(), targetPreparer.getClass().getSimpleName());
            targetPreparer.setDisable(true);
        }
    }
}
