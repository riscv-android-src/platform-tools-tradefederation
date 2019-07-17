/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.testtype.retry;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.ResultAndLogForwarder;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.result.retry.ISupportGranularResults;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Special forwarder that aggregates the results when needed, based on the retry strategy that was
 * taken.
 */
public class ResultAggregator extends CollectingTestListener {

    /* Forwarder to ALL result reporters */
    private ResultAndLogForwarder mAllForwarder;
    /* Forwarder to result reporters that only support aggregated results */
    private ResultAndLogForwarder mAggregatedForwarder;
    /* Forwarder to result reporters that support the attempt reporting */
    private ResultAndLogForwarder mDetailedForwarder;
    private RetryStrategy mRetryStrategy;
    // Track whether or not a module was started.
    private boolean mModuleInProgress = false;
    // Stores the results from non-module test runs until they are ready to be replayed.
    private List<TestRunResult> mPureRunResults = new ArrayList<>();

    public ResultAggregator(List<ITestInvocationListener> listeners, RetryStrategy strategy) {
        mAllForwarder = new ResultAndLogForwarder(listeners);

        List<ITestInvocationListener> supportDetails =
                listeners
                        .stream()
                        .filter(
                                i ->
                                        ((i instanceof ISupportGranularResults)
                                                && ((ISupportGranularResults) i)
                                                        .supportGranularResults()))
                        .collect(Collectors.toList());
        List<ITestInvocationListener> noSupportDetails =
                listeners
                        .stream()
                        .filter(
                                i ->
                                        !(i instanceof ISupportGranularResults)
                                                || !((ISupportGranularResults) i)
                                                        .supportGranularResults())
                        .collect(Collectors.toList());

        mAggregatedForwarder = new ResultAndLogForwarder(noSupportDetails);
        mDetailedForwarder = new ResultAndLogForwarder(supportDetails);

        mRetryStrategy = strategy;
        MergeStrategy mergeStrategy = MergeStrategy.getMergeStrategy(mRetryStrategy);
        setMergeStrategy(mergeStrategy);
    }

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        mAllForwarder.invocationStarted(context);
    }

    /** {@inheritDoc} */
    @Override
    public void invocationFailed(Throwable cause) {
        super.invocationFailed(cause);
        mAllForwarder.invocationFailed(cause);
    }

    /** {@inheritDoc} */
    @Override
    public void invocationEnded(long elapsedTime) {
        if (!mPureRunResults.isEmpty()) {
            forwardTestRunResults(mPureRunResults, mAggregatedForwarder);
            mPureRunResults.clear();
        }
        super.invocationEnded(elapsedTime);
        mAllForwarder.invocationEnded(elapsedTime);
    }

    /** {@inheritDoc} */
    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        if (!mPureRunResults.isEmpty()) {
            forwardTestRunResults(mPureRunResults, mAggregatedForwarder);
            mPureRunResults.clear();
        }

        mModuleInProgress = true;
        super.testModuleStarted(moduleContext);
        mAllForwarder.testModuleStarted(moduleContext);
    }

    /** {@inheritDoc} */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        super.setLogSaver(logSaver);
        mAllForwarder.setLogSaver(logSaver);
    }

    // ====== Forwarders to the detailed result reporters

    @Override
    public void testRunStarted(String name, int testCount, int attemptNumber, long startTime) {
        if (!mPureRunResults.isEmpty() && !mPureRunResults.get(0).getName().equals(name)) {
            forwardTestRunResults(mPureRunResults, mAggregatedForwarder);
            mPureRunResults.clear();
        }
        super.testRunStarted(name, testCount, attemptNumber, startTime);
        mDetailedForwarder.testRunStarted(name, testCount, attemptNumber, startTime);
    }

    @Override
    public void testRunFailed(String errorMessage) {
        super.testRunFailed(errorMessage);
        mDetailedForwarder.testRunFailed(errorMessage);
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        super.testStarted(test, startTime);
        mDetailedForwarder.testStarted(test, startTime);
    }

    @Override
    public void testIgnored(TestDescription test) {
        super.testIgnored(test);
        mDetailedForwarder.testIgnored(test);
    }

    @Override
    public void testAssumptionFailure(TestDescription test, String trace) {
        super.testAssumptionFailure(test, trace);
        mDetailedForwarder.testAssumptionFailure(test, trace);
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        super.testFailed(test, trace);
        mDetailedForwarder.testFailed(test, trace);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        super.testEnded(test, endTime, testMetrics);
        mDetailedForwarder.testEnded(test, endTime, testMetrics);
    }

    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        super.logAssociation(dataName, logFile);
        mDetailedForwarder.logAssociation(dataName, logFile);
    }

    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {
        super.testLogSaved(dataName, dataType, dataStream, logFile);
        mDetailedForwarder.testLogSaved(dataName, dataType, dataStream, logFile);
    }

    // ===== Forwarders to the aggregated reporters.

    @Override
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        mDetailedForwarder.testRunEnded(elapsedTime, runMetrics);

        // If we are not a module and we reach here. This allows to support non-suite scenarios
        if (!mModuleInProgress) {
            // We can't forward yet otherwise we might not have aggregated simple runs.
            mPureRunResults.add(getCurrentRunResults());
        }
    }

    @Override
    public void testModuleEnded() {
        mModuleInProgress = false;
        super.testModuleEnded();
        // We still forward the testModuleEnd to the detailed reporters
        mDetailedForwarder.testModuleEnded();

        List<TestRunResult> mergedResults = getMergedTestRunResults();
        Set<String> resultNames = new HashSet<>();
        int expectedTestCount = 0;
        for (TestRunResult result : mergedResults) {
            expectedTestCount += result.getExpectedTestCount();
            resultNames.add(result.getName());
        }

        // Forward all the results aggregated
        mAggregatedForwarder.testRunStarted(
                getCurrentRunResults().getName(),
                expectedTestCount,
                /* Attempt*/ 0,
                /* Start Time */ getCurrentRunResults().getStartTime());
        for (TestRunResult runResult : mergedResults) {
            forwardTestResults(runResult.getTestResults(), mAggregatedForwarder);
            if (runResult.isRunFailure()) {
                mAggregatedForwarder.testRunFailed(runResult.getRunFailureMessage());
            }
        }
        // Provide a strong association of the run to its logs.
        for (Entry<String, LogFile> logFile :
                getCurrentRunResults().getRunLoggedFiles().entrySet()) {
            mAggregatedForwarder.logAssociation(logFile.getKey(), logFile.getValue());
        }
        mAggregatedForwarder.testRunEnded(
                getCurrentRunResults().getElapsedTime(),
                getCurrentRunResults().getRunProtoMetrics());
        mAggregatedForwarder.testModuleEnded();
        // Ensure we don't carry results from one module to another.
        for (String name : resultNames) {
            clearResultsForName(name);
        }
    }

    private void forwardTestResults(
            Map<TestDescription, TestResult> testResults, ITestInvocationListener listener) {
        for (Map.Entry<TestDescription, TestResult> testEntry : testResults.entrySet()) {
            listener.testStarted(testEntry.getKey(), testEntry.getValue().getStartTime());
            switch (testEntry.getValue().getStatus()) {
                case FAILURE:
                    listener.testFailed(testEntry.getKey(), testEntry.getValue().getStackTrace());
                    break;
                case ASSUMPTION_FAILURE:
                    listener.testAssumptionFailure(
                            testEntry.getKey(), testEntry.getValue().getStackTrace());
                    break;
                case IGNORED:
                    listener.testIgnored(testEntry.getKey());
                    break;
                case INCOMPLETE:
                    listener.testFailed(
                            testEntry.getKey(), "Test did not complete due to exception.");
                    break;
                default:
                    break;
            }
            // Provide a strong association of the test to its logs.
            for (Entry<String, LogFile> logFile :
                    testEntry.getValue().getLoggedFiles().entrySet()) {
                if (listener instanceof ILogSaverListener) {
                    ((ILogSaverListener) listener)
                            .logAssociation(logFile.getKey(), logFile.getValue());
                }
            }
            listener.testEnded(
                    testEntry.getKey(),
                    testEntry.getValue().getEndTime(),
                    testEntry.getValue().getProtoMetrics());
        }
    }

    /**
     * Helper method to forward the results from multiple attempts of the same Test Run (same name).
     */
    private void forwardTestRunResults(List<TestRunResult> results, ILogSaverListener listener) {
        TestRunResult result =
                TestRunResult.merge(results, MergeStrategy.getMergeStrategy(mRetryStrategy));

        listener.testRunStarted(
                result.getName(), result.getExpectedTestCount(), 0, result.getStartTime());
        forwardTestResults(result.getTestResults(), listener);
        if (result.isRunFailure()) {
            listener.testRunFailed(result.getRunFailureMessage());
        }
        // Provide a strong association of the run to its logs.
        for (Entry<String, LogFile> logFile : result.getRunLoggedFiles().entrySet()) {
            listener.logAssociation(logFile.getKey(), logFile.getValue());
        }
        listener.testRunEnded(result.getElapsedTime(), result.getRunProtoMetrics());
        // Ensure we don't keep track of the results we just forwarded
        clearResultsForName(result.getName());
    }
}
