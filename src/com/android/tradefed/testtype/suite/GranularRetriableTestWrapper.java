/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.metric.CollectorHelper;
import com.android.tradefed.device.metric.IMetricCollector;
import com.android.tradefed.device.metric.IMetricCollectorReceiver;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogSaverResultForwarder;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.retry.BaseRetryDecision;
import com.android.tradefed.testtype.retry.IRetryDecision;
import com.android.tradefed.testtype.retry.MergeStrategy;
import com.android.tradefed.testtype.retry.RetryStrategy;
import com.android.tradefed.util.StreamUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A wrapper class works on the {@link IRemoteTest} to granulate the IRemoteTest in testcase level.
 * An IRemoteTest can contain multiple testcases. Previously, these testcases are treated as a
 * whole: When IRemoteTest runs, all testcases will run. Some IRemoteTest (The ones that implements
 * ITestFilterReceiver) can accept a whitelist of testcases and only run those testcases. This class
 * takes advantage of the existing feature and provides a more flexible way to run test suite.
 *
 * <ul>
 *   <li> Single testcase can be retried multiple times (within the same IRemoteTest run) to reduce
 *       the non-test-error failure rates.
 *   <li> The retried testcases are dynamically collected from previous run failures.
 * </ul>
 *
 * <p>Note:
 *
 * <ul>
 *   <li> The prerequisite to run a subset of test cases is that the test type should implement the
 *       interface {@link ITestFilterReceiver}.
 *   <li> X is customized max retry number.
 * </ul>
 */
public class GranularRetriableTestWrapper implements IRemoteTest, ITestCollector {

    private IRemoteTest mTest;
    private List<IMetricCollector> mRunMetricCollectors;
    private TestFailureListener mFailureListener;
    private IInvocationContext mModuleInvocationContext;
    private IConfiguration mModuleConfiguration;
    private ModuleListener mMainGranularRunListener;
    private RetryLogSaverResultForwarder mRetryAttemptForwarder;
    private List<ITestInvocationListener> mModuleLevelListeners;
    private ILogSaver mLogSaver;
    private String mModuleId;
    private int mMaxRunLimit;

    private boolean mCollectTestsOnly = false;

    // Tracking of the metrics
    /** How much time are we spending doing the retry attempts */
    private long mRetryTime = 0L;
    /** The number of test cases that passed after a failed attempt */
    private long mSuccessRetried = 0L;
    /** The number of test cases that remained failed after all retry attempts */
    private long mFailedRetried = 0L;
    /** Store the test that successfully re-run and at which attempt they passed */
    private Map<String, Integer> mAttemptSuccess = new HashMap<>();

    private RetryStrategy mRetryStrategy = RetryStrategy.NO_RETRY;
    private boolean mRebootAtLastRetry = false;

    public GranularRetriableTestWrapper(
            IRemoteTest test,
            ITestInvocationListener mainListener,
            TestFailureListener failureListener,
            List<ITestInvocationListener> moduleLevelListeners,
            int maxRunLimit) {
        mTest = test;
        mMainGranularRunListener = new ModuleListener(mainListener);
        mFailureListener = failureListener;
        mModuleLevelListeners = moduleLevelListeners;
        mMaxRunLimit = maxRunLimit;
    }

    /**
     * Set the {@link ModuleDefinition} name as a {@link GranularRetriableTestWrapper} attribute.
     *
     * @param moduleId the name of the moduleDefinition.
     */
    public void setModuleId(String moduleId) {
        mModuleId = moduleId;
    }

    /**
     * Set the {@link ModuleDefinition} RunStrategy as a {@link GranularRetriableTestWrapper}
     * attribute.
     *
     * @param skipTestCases whether the testcases should be skipped.
     */
    public void setMarkTestsSkipped(boolean skipTestCases) {
        mMainGranularRunListener.setMarkTestsSkipped(skipTestCases);
    }

    /**
     * Set the {@link ModuleDefinition}'s runMetricCollector as a {@link
     * GranularRetriableTestWrapper} attribute.
     *
     * @param runMetricCollectors A list of MetricCollector for the module.
     */
    public void setMetricCollectors(List<IMetricCollector> runMetricCollectors) {
        mRunMetricCollectors = runMetricCollectors;
    }

    /**
     * Set the {@link ModuleDefinition}'s ModuleConfig as a {@link GranularRetriableTestWrapper}
     * attribute.
     *
     * @param moduleConfiguration Provide the module metrics.
     */
    public void setModuleConfig(IConfiguration moduleConfiguration) {
        mModuleConfiguration = moduleConfiguration;
    }

    /**
     * Set the {@link IInvocationContext} as a {@link GranularRetriableTestWrapper} attribute.
     *
     * @param moduleInvocationContext The wrapper uses the InvocationContext to initialize the
     *     MetricCollector when necessary.
     */
    public void setInvocationContext(IInvocationContext moduleInvocationContext) {
        mModuleInvocationContext = moduleInvocationContext;
    }

    /**
     * Set the Module's {@link ILogSaver} as a {@link GranularRetriableTestWrapper} attribute.
     *
     * @param logSaver The listeners for each test run should save the logs.
     */
    public void setLogSaver(ILogSaver logSaver) {
        mLogSaver = logSaver;
    }

    /** Sets the {@link RetryStrategy} to be used when retrying. */
    public final void setRetryStrategy(RetryStrategy retryStrategy) {
        mRetryStrategy = retryStrategy;
    }

    /** Sets the flag to reboot devices at the last intra-module retry. */
    public final void setRebootAtLastRetry(boolean rebootAtLastRetry) {
        mRebootAtLastRetry = rebootAtLastRetry;
    }

    /**
     * Initialize a new {@link ModuleListener} for each test run.
     *
     * @return a {@link ITestInvocationListener} listener which contains the new {@link
     *     ModuleListener}, the main {@link ITestInvocationListener} and main {@link
     *     TestFailureListener}, and wrapped by RunMetricsCollector and Module MetricCollector (if
     *     not initialized).
     */
    private ITestInvocationListener initializeListeners() {
        List<ITestInvocationListener> currentTestListener = new ArrayList<>();
        // Add all the module level listeners, including TestFailureListener
        if (mModuleLevelListeners != null) {
            currentTestListener.addAll(mModuleLevelListeners);
        }
        currentTestListener.add(mMainGranularRunListener);

        mRetryAttemptForwarder = new RetryLogSaverResultForwarder(mLogSaver, currentTestListener);
        ITestInvocationListener runListener = mRetryAttemptForwarder;
        if (mFailureListener != null) {
            mFailureListener.setLogger(mRetryAttemptForwarder);
            currentTestListener.add(mFailureListener);
        }

        // The module collectors itself are added: this list will be very limited.
        // We clone them since the configuration object is shared across shards.
        for (IMetricCollector collector :
                CollectorHelper.cloneCollectors(mModuleConfiguration.getMetricCollectors())) {
            if (collector.isDisabled()) {
                CLog.d("%s has been disabled. Skipping.", collector);
            } else {
                runListener = collector.init(mModuleInvocationContext, runListener);
            }
        }

        return runListener;
    }

    /**
     * Schedule a series of {@link IRemoteTest#run(ITestInvocationListener)}.
     *
     * @param listener The ResultForwarder listener which contains a new moduleListener for each
     *     run.
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mMainGranularRunListener.setCollectTestsOnly(mCollectTestsOnly);
        ITestInvocationListener allListeners = initializeListeners();
        // First do the regular run, not retried.
        intraModuleRun(allListeners);

        if (mMaxRunLimit <= 1) {
            return;
        }

        // Deal with retried attempted
        long startTime = System.currentTimeMillis();
        Set<TestDescription> previousFailedTests = new HashSet<>();

        try {
            CLog.d("Starting intra-module retry.");
            for (int attemptNumber = 1; attemptNumber < mMaxRunLimit; attemptNumber++) {
                IRetryDecision retryDecision = new BaseRetryDecision();
                boolean retry =
                        retryDecision.shouldRetry(
                                mRetryStrategy,
                                mTest,
                                mMainGranularRunListener.getTestRunForAttempts(attemptNumber - 1));
                if (!retry) {
                    return;
                }
                // Reboot device at the last intra-module retry if reboot-at-last-retry is set.
                if (mRebootAtLastRetry && (attemptNumber == (mMaxRunLimit-1))) {
                    for (ITestDevice device : mModuleInvocationContext.getDevices()) {
                        if (!(device.getIDevice() instanceof StubDevice)) {
                            CLog.i("Rebooting device: %s at the last intra-module retry.",
                                    device.getSerialNumber());
                            device.reboot();
                        }
                    }
                }
                // Run the tests again
                intraModuleRun(allListeners);

                Set<TestDescription> lastRun = getFailedTestCases(attemptNumber);
                if (shouldHandleFailure(mRetryStrategy)) {
                    // Evaluate success from what we just ran
                    Set<TestDescription> diff = Sets.difference(previousFailedTests, lastRun);
                    mSuccessRetried += diff.size();
                    final int currentAttempt = attemptNumber;
                    diff.forEach((desc) -> mAttemptSuccess.put(desc.toString(), currentAttempt));
                    previousFailedTests = lastRun;
                }
            }
        } finally {
            if (previousFailedTests != null) {
                mFailedRetried += previousFailedTests.size();
            }
            // Track how long we spend in retry
            mRetryTime = System.currentTimeMillis() - startTime;
        }
    }

    /**
     * If the strategy needs to handle some failures return True. If it needs to retry no matter
     * what like {@link RetryStrategy#ITERATIONS} returns False.
     */
    private boolean shouldHandleFailure(RetryStrategy retryStrategy) {
        return RetryStrategy.RETRY_ANY_FAILURE.equals(retryStrategy);
    }

    /**
     * Collect failed test cases from listener.
     *
     * @param attemptNumber the 0-indexed integer indicating which attempt to gather failed cases.
     */
    private Set<TestDescription> getFailedTestCases(int attemptNumber) {
        Set<TestDescription> failedTestCases = new HashSet<TestDescription>();
        for (String runName : mMainGranularRunListener.getTestRunNames()) {
            TestRunResult run =
                    mMainGranularRunListener.getTestRunAtAttempt(runName, attemptNumber);
            if (run != null) {
                failedTestCases.addAll(run.getFailedTests());
            }
        }
        return failedTestCases;
    }

    /** The workflow for each individual {@link IRemoteTest} run. */
    private final void intraModuleRun(ITestInvocationListener runListener)
            throws DeviceNotAvailableException {
        try {
            List<IMetricCollector> clonedCollectors = cloneCollectors(mRunMetricCollectors);
            if (mTest instanceof IMetricCollectorReceiver) {
                ((IMetricCollectorReceiver) mTest).setMetricCollectors(clonedCollectors);
                // If test can receive collectors then let it handle how to set them up
                mTest.run(runListener);
            } else {
                // Module only init the collectors here to avoid triggering the collectors when
                // replaying the cached events at the end. This ensures metrics are capture at
                // the proper time in the invocation.
                for (IMetricCollector collector : clonedCollectors) {
                    if (collector.isDisabled()) {
                        CLog.d("%s has been disabled. Skipping.", collector);
                    } else {
                        runListener = collector.init(mModuleInvocationContext, runListener);
                    }
                }
                mTest.run(runListener);
            }
        } catch (RuntimeException | AssertionError re) {
            CLog.e("Module '%s' - test '%s' threw exception:", mModuleId, mTest.getClass());
            CLog.e(re);
            CLog.e("Proceeding to the next test.");
            runListener.testRunFailed(StreamUtil.getStackTrace(re));
        } catch (DeviceUnresponsiveException due) {
            // being able to catch a DeviceUnresponsiveException here implies that recovery was
            // successful, and test execution should proceed to next module.
            CLog.w(
                    "Ignored DeviceUnresponsiveException because recovery was "
                            + "successful, proceeding with next module. Stack trace:");
            CLog.w(due);
            CLog.w("Proceeding to the next test.");
            runListener.testRunFailed(due.getMessage());
        } catch (DeviceNotAvailableException dnae) {
            // TODO: See if it's possible to report IReportNotExecuted
            runListener.testRunFailed(
                    "Run in progress was not completed due to: " + dnae.getMessage());
            // Device Not Available Exception are rethrown.
            throw dnae;
        } finally {
            mRetryAttemptForwarder.incrementAttempt();
        }
    }

    /** Get the merged TestRunResults from each {@link IRemoteTest} run. */
    public final List<TestRunResult> getFinalTestRunResults() {
        MergeStrategy strategy = MergeStrategy.getMergeStrategy(mRetryStrategy);
        mMainGranularRunListener.setMergeStrategy(strategy);
        return mMainGranularRunListener.getMergedTestRunResults();
    }

    @VisibleForTesting
    Map<String, List<TestRunResult>> getTestRunResultCollected() {
        Map<String, List<TestRunResult>> runResultMap = new LinkedHashMap<>();
        for (String runName : mMainGranularRunListener.getTestRunNames()) {
            runResultMap.put(runName, mMainGranularRunListener.getTestRunAttempts(runName));
        }
        return runResultMap;
    }

    @VisibleForTesting
    List<IMetricCollector> cloneCollectors(List<IMetricCollector> originalCollectors) {
        return CollectorHelper.cloneCollectors(originalCollectors);
    }

    /** Check if any testRunResult has ever failed. This check is used for bug report only. */
    public boolean hasFailed() {
        return mMainGranularRunListener.hasFailed();
    }

    /**
     * Calculate the number of testcases in the {@link IRemoteTest}. This value distincts the same
     * testcases that are rescheduled multiple times.
     */
    public final int getExpectedTestsCount() {
        return mMainGranularRunListener.getExpectedTests();
    }

    /** Returns the elapsed time in retry attempts. */
    public final long getRetryTime() {
        return mRetryTime;
    }

    /** Returns the number of tests we managed to change status from failed to pass. */
    public final long getRetrySuccess() {
        return mSuccessRetried;
    }

    /** Returns the number of tests we couldn't change status from failed to pass. */
    public final long getRetryFailed() {
        return mFailedRetried;
    }

    /** Returns the listener containing all the results. */
    public ModuleListener getResultListener() {
        return mMainGranularRunListener;
    }

    /** Returns the attempts that turned into success. */
    public Map<String, Integer> getAttemptSuccessStats() {
        return mAttemptSuccess;
    }

    /** Forwarder that also handles passing the current attempt we are at. */
    private class RetryLogSaverResultForwarder extends LogSaverResultForwarder {

        private int mAttemptNumber = 0;

        public RetryLogSaverResultForwarder(
                ILogSaver logSaver, List<ITestInvocationListener> listeners) {
            super(logSaver, listeners);
        }

        @Override
        public void testRunStarted(String runName, int testCount) {
            super.testRunStarted(runName, testCount, mAttemptNumber);
        }

        @Override
        public void testRunStarted(String runName, int testCount, int attemptNumber) {
            if (attemptNumber != mAttemptNumber) {
                CLog.w(
                        "Test reported an attempt %s, while the suite is at attempt %s",
                        attemptNumber, mAttemptNumber);
            }
            // We enforce our attempt number
            super.testRunStarted(runName, testCount, mAttemptNumber);
        }

        /** Increment the attempt number. */
        public void incrementAttempt() {
            mAttemptNumber++;
        }
    }

    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }
}
