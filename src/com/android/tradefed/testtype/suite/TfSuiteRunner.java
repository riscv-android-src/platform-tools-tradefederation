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

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Implementation of {@link ITestSuite} which will load tests from TF jars res/config/suite/
 * folder.
 */
public class TfSuiteRunner extends ITestSuite {
    private static final String SUITE_PREFIX = "suite/";

    @Option(name = "run-suite-tag", description = "The tag that must be run.",
            mandatory = true)
    private String mSuiteTag = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkedHashMap<String, IConfiguration> loadTests() {
        LinkedHashMap <String, IConfiguration> configMap =
                new LinkedHashMap<String, IConfiguration>();
        IConfigurationFactory configFactory = ConfigurationFactory.getInstance();
        // We only list configuration under config/suite/ in order to have a smaller search.
        List<String> configs = configFactory.getConfigList(SUITE_PREFIX);
        for (String configName : configs) {
            try {
                IConfiguration testConfig =
                        configFactory.createConfigurationFromArgs(new String[]{configName});
                if (testConfig.getConfigurationDescription().getSuiteTags().contains(mSuiteTag)) {
                    configMap.put(configName, testConfig);
                }
            } catch (ConfigurationException e) {
                CLog.e("Configuration '%s' cannot be loaded, ignoring.", configName);
                CLog.e(e);
            }
        }
        return configMap;
    }
}
