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

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.Map;

/**
 * Listener attached to each {@link IRemoteTest} of each module in order to collect the list of
 * results.
 */
public class ModuleListener extends CollectingTestListener {

    private ITestInvocationListener mListener;
    private int mExpectedTestCount = 0;
    private boolean mSkip = false;
    private boolean mTestFailed = false;
    private int mTestsRan = 1;

    /** Constructor. Accept the original listener to forward testLog callback. */
    public ModuleListener(ITestInvocationListener listener) {
        mListener = listener;
        setIsAggregrateMetrics(true);
    }

    /** {@inheritDoc} */
    @Override
    public void testLog(String name, LogDataType type, InputStreamSource stream) {
        CLog.d("ModuleListener.testLog(%s, %s, %s)", name, type.toString(), stream.toString());
        mListener.testLog(name, type, stream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String name, int numTests) {
        if (!hasResultFor(name)) {
            // No results for it yet, brand new set of tests, we expect them all.
            mExpectedTestCount += numTests;
        } else {
            TestRunResult currentResult = getCurrentRunResults();
            // We have results but the run wasn't complete.
            if (!currentResult.isRunComplete()) {
                mExpectedTestCount += numTests;
            }
        }
        super.testRunStarted(name, numTests);
    }

    /** {@inheritDoc} */
    @Override
    public void testStarted(TestIdentifier test, long startTime) {
        CLog.d("ModuleListener.testStarted(%s)", test.toString());
        mTestFailed = false;
        super.testStarted(test, startTime);
        if (mSkip) {
            super.testIgnored(test);
        }
    }

    /** Helper to log the test passed if it didn't fail. */
    private void logTestPassed(String testName) {
        if (!mTestFailed) {
            CLog.logAndDisplay(
                    LogLevel.INFO, "[%d/%d] %s pass", mTestsRan, mExpectedTestCount, testName);
        }
        mTestsRan++;
    }

    /** {@inheritDoc} */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        logTestPassed(test.toString());
        super.testEnded(test, testMetrics);
    }

    /** {@inheritDoc} */
    @Override
    public void testEnded(TestIdentifier test, long endTime, Map<String, String> testMetrics) {
        logTestPassed(test.toString());
        super.testEnded(test, endTime, testMetrics);
    }


    /** {@inheritDoc} */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        if (mSkip) {
            return;
        }
        CLog.logAndDisplay(
                LogLevel.INFO,
                "[%d/%d] %s fail:\n%s",
                mTestsRan,
                mExpectedTestCount,
                test.toString(),
                trace);
        mTestFailed = true;
        super.testFailed(test, trace);
    }

    /** {@inheritDoc} */
    @Override
    public int getNumTotalTests() {
        return mExpectedTestCount;
    }

    /** Whether or not to mark all the test cases skipped. */
    public void setMarkTestsSkipped(boolean skip) {
        mSkip = skip;
    }
}
