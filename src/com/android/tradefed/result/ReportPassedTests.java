/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.testtype.suite.ModuleDefinition;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;

/** Report in a file possible filters to exclude passed test. */
public class ReportPassedTests extends CollectingTestListener implements IConfigurationReceiver {

    private static final String PASSED_TEST_LOG = "passed_tests";
    private boolean mInvocationFailed = false;
    private ITestLogger mLogger;
    private boolean mModuleInProgress;
    private IInvocationContext mContextForEmptyModule;
    private Integer mShardIndex;
    private Set<String> mExtraTestCases = new LinkedHashSet<>();

    public void setLogger(ITestLogger logger) {
        mLogger = logger;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        if (configuration.getCommandOptions().getShardIndex() != null) {
            mShardIndex = configuration.getCommandOptions().getShardIndex();
        }
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        super.testModuleStarted(moduleContext);
        mModuleInProgress = true;
        mContextForEmptyModule = moduleContext;
    }

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        mContextForEmptyModule = null;
        super.testRunEnded(elapsedTime, runMetrics);
        if (!mModuleInProgress) {
            // Remove right away any run failure they will be excluded
            if (getCurrentRunResults().isRunFailure() || mInvocationFailed) {
                gatherPassedTests(
                        getCurrentRunResults(),
                        getBaseName(getCurrentRunResults()), mInvocationFailed);
                clearResultsForName(getCurrentRunResults().getName());
                // Clear the failure for aggregation
                getCurrentRunResults().resetRunFailure();
            }
        }
    }

    @Override
    public void testModuleEnded() {
        if (mContextForEmptyModule != null) {
            // If the module was empty
            String moduleId = mContextForEmptyModule.getAttributes()
                    .getUniqueMap().get(ModuleDefinition.MODULE_ID);
            if (moduleId != null) {
                super.testRunStarted(moduleId, 0);
                super.testRunEnded(0L, new HashMap<String, Metric>());
            }
            mContextForEmptyModule = null;
        }
        super.testModuleEnded();
        // Remove right away any run failure they will be excluded
        if (getCurrentRunResults().isRunFailure() || mInvocationFailed) {
            gatherPassedTests(
                    getCurrentRunResults(), getBaseName(getCurrentRunResults()), mInvocationFailed);
            clearResultsForName(getCurrentRunResults().getName());
            // Clear the failure for aggregation
            getCurrentRunResults().resetRunFailure();
        }
        mModuleInProgress = false;
    }

    @Override
    public void invocationFailed(FailureDescription failure) {
        super.invocationFailed(failure);
        mInvocationFailed = true;
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        createPassedLog();
    }

    private void createPassedLog() {
        if (mLogger == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (TestRunResult result : getMergedTestRunResults()) {
            sb.append(createFilters(result, getBaseName(result), false));
        }
        if (!mExtraTestCases.isEmpty()) {
            sb.append(Joiner.on("\n").join(mExtraTestCases));
            mExtraTestCases.clear();
        }
        if (sb.length() == 0) {
            CLog.d("No new filter for passed_test");
            return;
        }
        testLog(sb.toString());
    }

    @VisibleForTesting
    void testLog(String toBeLogged) {
        try (ByteArrayInputStreamSource source =
                new ByteArrayInputStreamSource(toBeLogged.getBytes())) {
            mLogger.testLog(PASSED_TEST_LOG, LogDataType.PASSED_TESTS, source);
        }
    }

    private String getBaseName(TestRunResult runResult) {
        IInvocationContext context = getModuleContextForRunResult(runResult.getName());
        // If it's a test module
        if (context != null) {
            return context.getAttributes().getUniqueMap().get(ModuleDefinition.MODULE_ID);
        } else {
            return runResult.getName();
        }
    }

    private String createFilters(
            TestRunResult runResult, String baseName, boolean invocationFailure) {
        if (mShardIndex != null) {
            baseName = "shard_" + mShardIndex + " " + baseName;
        }
        StringBuilder sb = new StringBuilder();
        if (!runResult.hasFailedTests() && !runResult.isRunFailure() && !invocationFailure) {
            sb.append(baseName);
            sb.append("\n");
            return sb.toString();
        }
        for (Entry<TestDescription, TestResult> res : runResult.getTestResults().entrySet()) {
            if (TestStatus.FAILURE.equals(res.getValue().getStatus())) {
                continue;
            }
            sb.append(baseName + " " + res.getKey().toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    private void gatherPassedTests(
            TestRunResult runResult, String baseName, boolean invocationFailure) {
        StringBuilder sb = new StringBuilder();
        sb.append(createFilters(runResult, baseName, invocationFailure));
        if (sb.length() == 0L) {
            return;
        }
        mExtraTestCases.add(sb.toString());
    }
}
