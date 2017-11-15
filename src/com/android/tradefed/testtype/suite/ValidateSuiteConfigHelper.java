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

import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.TextResultReporter;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.multi.IMultiTargetPreparer;

import java.util.List;

/**
 * This class will help validating that the {@link IConfiguration} loaded for the suite are meeting
 * the expected requirements: - No Build providers - No Result reporters
 */
public class ValidateSuiteConfigHelper {

    private ValidateSuiteConfigHelper() {}

    /**
     * Check that a configuration is properly built to run in a suite.
     *
     * @param config a {@link IConfiguration} to be checked if valide for suite.
     * @return True if the config can be run, false otherwise.
     */
    public static boolean validateConfig(IConfiguration config) {
        if (!config.getBuildProvider().getClass().isAssignableFrom(StubBuildProvider.class)) {
            return false;
        }
        // if a multi device config is presented, ensure none of the devices define a build_provider
        for (IDeviceConfiguration deviceConfig : config.getDeviceConfig()) {
            if (!deviceConfig
                    .getBuildProvider()
                    .getClass()
                    .isAssignableFrom(StubBuildProvider.class)) {
                return false;
            }
            if (!checkTargetPrep(config.getTargetPreparers())) {
                return false;
            }
        }
        if (config.getTestInvocationListeners().size() != 1) {
            return false;
        }
        if (!config.getTestInvocationListeners()
                .get(0)
                .getClass()
                .isAssignableFrom(TextResultReporter.class)) {
            return false;
        }
        // Check target preparers
        if (!checkTargetPrep(config.getTargetPreparers())) {
            return false;
        }
        if (!checkTargetPrep(config.getMultiTargetPreparers())) {
            return false;
        }
        if (!config.getMetricCollectors().isEmpty()) {
            CLog.e(
                    "Configuration %s contains a %s which is not allowed in module.",
                    config.getName(), Configuration.DEVICE_METRICS_COLLECTOR_TYPE_NAME);
            return false;
        }
        return true;
    }

    /**
     * Check target_preparer and multi_target_preparer to ensure they do not extends each other as
     * it could lead to some issues.
     */
    private static boolean checkTargetPrep(List<?> targetPrepList) {
        for (Object o : targetPrepList) {
            if (o instanceof ITargetPreparer && o instanceof IMultiTargetPreparer) {
                CLog.d(
                        "%s is extending both target_preparer and multi_target_preparer",
                        o.getClass().getCanonicalName());
                return false;
            }
        }
        return true;
    }
}
