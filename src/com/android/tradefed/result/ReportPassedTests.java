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
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.testtype.suite.ModuleDefinition;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map.Entry;

/** Report in a file possible filters to exclude passed test. */
public class ReportPassedTests extends CollectingTestListener {

    private static final String PASSED_TEST_LOG = "passed_tests";
    private boolean mInvocationFailed = false;
    private ITestLogger mLogger;
    private boolean mModuleInProgress;

    public void setLogger(ITestLogger logger) {
        mLogger = logger;
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        super.testModuleStarted(moduleContext);
        mModuleInProgress = true;
    }

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        if (!mModuleInProgress) {
            // Remove right away any run failure they will be excluded
            if (getCurrentRunResults().isRunFailure()) {
                clearResultsForName(getCurrentRunResults().getName());
            } else if (mInvocationFailed) {
                clearResultsForName(getCurrentRunResults().getName());
            }
        }
    }

    @Override
    public void testModuleEnded() {
        super.testModuleEnded();
        // Remove right away any run failure they will be excluded
        if (getCurrentRunResults().isRunFailure()) {
            clearResultsForName(getCurrentRunResults().getName());
        } else if (mInvocationFailed) {
            clearResultsForName(getCurrentRunResults().getName());
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
            IInvocationContext context = getModuleContextForRunResult(result.getName());
            // If it's a test module
            if (context != null) {
                sb.append(
                        createFilters(
                                result,
                                context.getAttributes()
                                        .getUniqueMap()
                                        .get(ModuleDefinition.MODULE_ID)));
            } else {
                sb.append(createFilters(result, result.getName()));
            }
        }
        if (sb.length() == 0) {
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

    private String createFilters(TestRunResult runResult, String baseName) {
        StringBuilder sb = new StringBuilder();
        if (!runResult.hasFailedTests()) {
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
}
