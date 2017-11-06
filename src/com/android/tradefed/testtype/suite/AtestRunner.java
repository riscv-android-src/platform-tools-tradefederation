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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.InstrumentationTest;

import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;

/** Implementation of {@link ITestSuite} */
public class AtestRunner extends ITestSuite {

    private static final Pattern CONFIG_RE =
            Pattern.compile(".*/(?<config>[^/]+).config", Pattern.CASE_INSENSITIVE);

    @Option(name = "test-info", description = "Test info of the test to run.")
    private List<String> mTestInfo = new ArrayList<>();

    @Option(
        name = "wait-for-debugger",
        description = "For InstrumentationTests, we pass debug to the instrumentation run."
    )
    private boolean mDebug = false;

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
        CLog.d("Tests: %s", String.join(" ", mTestInfo));
        for (String testInfoString : mTestInfo) {
            HashMap<String, List<String>> testInfo = parseTestInfoParam(testInfoString);
            // "name" has value that is a list of one element.
            String name = testInfo.get("name").get(0);
            if (configs.contains(name)) {
                try {
                    IConfiguration testConfig =
                            configFactory.createConfigurationFromArgs(new String[] {name});
                    for (String filter : testInfo.get("filters")) {
                        addFilter(testConfig, filter);
                    }
                    if (mDebug) {
                        addDebugger(testConfig);
                    }
                    configMap.put(name, testConfig);
                } catch (ConfigurationException | NoClassDefFoundError e) {
                    CLog.e(e);
                    CLog.e("Configuration '%s' cannot be loaded, ignoring.", testInfo);
                }
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
     * Parse the test-info parameter into config name and filters.
     *
     * @param testInfoString The value of the test-info parameter, of form:
     *     module_name:class#method,class#method
     * @return HashMap with keys "name" and "filters"
     */
    public HashMap<String, List<String>> parseTestInfoParam(String testInfoString) {
        CLog.d("Parsing param: %s", testInfoString);
        HashMap<String, List<String>> testInfoMap = new HashMap<>();
        String[] infoParts = testInfoString.split(":");
        testInfoMap.put("name", Arrays.asList(infoParts[0]));
        testInfoMap.put("filters", new ArrayList<>());
        if (infoParts.length > 1) {
            for (String filter : infoParts[1].split(",")) {
                testInfoMap.get("filters").add(filter);
            }
        }
        return testInfoMap;
    }

    /**
     * Add filter to the tests in an IConfiguration.
     *
     * @param testConfig The configuration containing tests to filter.
     * @param filter The filter to add to the tests in the testConfig.
     * @return HashMap with keys "name" and "filters"
     */
    public void addFilter(IConfiguration testConfig, String filter) {
        List<IRemoteTest> tests = testConfig.getTests();
        for (IRemoteTest test : tests) {
            if (test instanceof ITestFilterReceiver) {
                CLog.d("Applying filter: %s", filter);
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

    /** Helper to attach the debugger to any Instrumentation tests in the config. */
    private void addDebugger(IConfiguration testConfig) {
        for (IRemoteTest test : testConfig.getTests()) {
            if (test instanceof InstrumentationTest) {
                ((InstrumentationTest) test).setDebug(true);
            }
        }
    }
}
