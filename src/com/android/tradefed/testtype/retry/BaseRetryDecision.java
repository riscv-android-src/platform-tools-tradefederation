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

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base implementation of {@link IRetryDecision}. Base implementation only take local signals into
 * account.
 */
public class BaseRetryDecision implements IRetryDecision {

    private RetryStrategy mRetryStrategy;
    private IRemoteTest mCurrentlyConsideredTest;
    private RetryStatsHelper mStatistics;

    /** Constructor for the retry decision, always based on the {@link RetryStrategy}. */
    public BaseRetryDecision(RetryStrategy strategy) {
        mRetryStrategy = strategy;
    }

    @Override
    public boolean shouldRetry(IRemoteTest test, List<TestRunResult> previousResults) {
        // Keep track of some results for the test in progress for statistics purpose.
        if (test != mCurrentlyConsideredTest) {
            mCurrentlyConsideredTest = test;
            mStatistics = new RetryStatsHelper();
        }

        switch (mRetryStrategy) {
            case NO_RETRY:
                // Return directly if we are not considering retry at all.
                return false;
            case ITERATIONS:
                // For iterations, retry directly, we have nothing to setup
                return true;
            case RERUN_UNTIL_FAILURE:
                // For retrying until failure, if any failures occurred, skip retry.
                return !hasAnyFailures(previousResults);
            default:
                // Continue the logic for retry the failures.
                break;
        }

        mStatistics.addResultsFromRun(previousResults);
        if (!(test instanceof ITestFilterReceiver)) {
            CLog.d(
                    "%s does not implement ITestFilterReceiver, thus cannot work with auto-retry.",
                    test);
            return false;
        }

        // TODO(b/77548917): Right now we only support ITestFilterReceiver. We should expect to
        // support ITestFile*Filter*Receiver in the future.
        ITestFilterReceiver filterableTest = (ITestFilterReceiver) test;
        return handleRetryFailures(filterableTest, previousResults);
    }

    @Override
    public void addLastAttempt(List<TestRunResult> lastResults) {
        mStatistics.addResultsFromRun(lastResults);
    }

    @Override
    public RetryStatistics getRetryStats() {
        return mStatistics.calculateStatistics();
    }

    /** Returns the set of failed test cases that should be retried. */
    public static Set<TestDescription> getFailedTestCases(List<TestRunResult> previousResults) {
        Set<TestDescription> failedTestCases = new HashSet<TestDescription>();
        for (TestRunResult run : previousResults) {
            if (run != null) {
                failedTestCases.addAll(run.getFailedTests());
            }
        }
        return failedTestCases;
    }

    private boolean handleRetryFailures(
            ITestFilterReceiver test, List<TestRunResult> previousResults) {
        if (hasRunFailures(previousResults)) {
            return true;
        }

        // In case of test case failure, we retry with filters.
        Set<TestDescription> previousFailedTests = getFailedTestCases(previousResults);
        if (!previousFailedTests.isEmpty()) {
            CLog.d("Retrying the test case failure.");
            addRetriedTestsToIncludeFilters(test, previousFailedTests);
            return true;
        }

        CLog.d("No test run or test case failures. No need to retry.");
        return false;
    }

    /** Returns true if there are any failures in the previous results. */
    private boolean hasAnyFailures(List<TestRunResult> previousResults) {
        for (TestRunResult run : previousResults) {
            if (run != null && (run.isRunFailure() || run.hasFailedTests())) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if there are any run failures in the previous results. */
    private boolean hasRunFailures(List<TestRunResult> previousResults) {
        for (TestRunResult run : previousResults) {
            if (run != null && run.isRunFailure()) {
                return true;
            }
        }
        return false;
    }

    /** Set the filters on the test runner for the retry. */
    private void addRetriedTestsToIncludeFilters(
            ITestFilterReceiver test, Set<TestDescription> testDescriptions) {
        // Limit the re-run to the failure we include, so clear filters then put our failures
        test.clearIncludeFilters();
        for (TestDescription testCase : testDescriptions) {
            String filter = testCase.toString();
            test.addIncludeFilter(filter);
        }
    }
}
