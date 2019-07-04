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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
        super.invocationEnded(elapsedTime);
        mAllForwarder.invocationEnded(elapsedTime);
    }

    /** {@inheritDoc} */
    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
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
        super.testRunStarted(name, testCount, attemptNumber, startTime);
        mDetailedForwarder.testRunStarted(name, testCount, attemptNumber, startTime);
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
    public void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        mDetailedForwarder.testRunEnded(elapsedTime, runMetrics);
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
    public void testModuleEnded() {
        super.testModuleEnded();
        // We still forward the testModuleEnd to the detailed reporters
        mDetailedForwarder.testModuleEnded();

        // Forward all the results aggregated
        mAggregatedForwarder.testRunStarted(
                getCurrentRunResults().getName(),
                getCurrentRunResults().getExpectedTestCount(),
                /* Attempt*/ 0,
                /* Start Time */ getCurrentRunResults().getStartTime());
        for (TestRunResult runResult : getMergedTestRunResults()) {
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
}
