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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Implementation of {@link ITestSuite} */
public class AtestRunner extends ITestSuite {

    private static final Pattern CONFIG_RE =
            Pattern.compile(".*/(?<config>[^/]+).config", Pattern.CASE_INSENSITIVE);

    @Option(name = "test-info", description = "Test info of the test to run.")
    private List<String> mTestInfo = new ArrayList<>();

    public IConfigurationFactory loadConfigFactory() {
        return ConfigurationFactory.getInstance();
    }

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
        for (String testInfo : mTestInfo) {
            if (configs.contains(testInfo)) {
                CLog.d("Found %s", testInfo);
                try {
                    IConfiguration testConfig =
                            configFactory.createConfigurationFromArgs(new String[] {testInfo});
                    configMap.put(testInfo, testConfig);
                } catch (ConfigurationException | NoClassDefFoundError e) {
                    // Do not print the stack it's too verbose.
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
    private String canonicalizeConfigName(String originalName) {
        Matcher match = CONFIG_RE.matcher(originalName);
        if (match.find()) {
            return match.group("config");
        }
        return originalName;
    }
}
