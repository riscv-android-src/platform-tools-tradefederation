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
package com.android.tradefed.result.suite;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.util.TimeUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Collect test results for an entire suite invocation and output the final results. */
public class SuiteResultReporter extends CollectingTestListener {

    private long startTime = 0l;
    private long mElapsedTime = 0l;

    private int mTotalModules = 0;
    private int mCompleteModules = 0;

    private long mTotalTests = 0l;
    private long mPassedTests = 0l;
    private long mFailedTests = 0l;

    private Map<String, Integer> mModuleExpectedTests = new HashMap<>();

    @Override
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        startTime = System.currentTimeMillis();
    }

    @Override
    public void testRunStarted(String name, int numTests) {
        super.testRunStarted(name, numTests);
        if (mModuleExpectedTests.get(name) == null) {
            mModuleExpectedTests.put(name, numTests);
        } else {
            mModuleExpectedTests.put(name, mModuleExpectedTests.get(name) + numTests);
        }
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        mElapsedTime = System.currentTimeMillis() - startTime;

        // finalize and print results - general
        Collection<TestRunResult> results = getRunResults();

        mTotalModules = results.size();

        for (TestRunResult moduleResult : results) {
            if (!moduleResult.isRunFailure()) {
                mCompleteModules++;
            }
            mTotalTests += mModuleExpectedTests.get(moduleResult.getName());
            mPassedTests += moduleResult.getNumTestsInState(TestStatus.PASSED);
            mFailedTests += moduleResult.getNumAllFailedTests();
        }

        CLog.logAndDisplay(LogLevel.INFO, "============== Results ==============");
        CLog.logAndDisplay(LogLevel.INFO, "Run time: %s", TimeUtil.formatElapsedTime(mElapsedTime));
        CLog.logAndDisplay(
                LogLevel.INFO, "%s/%s modules completed", mCompleteModules, mTotalModules);
        CLog.logAndDisplay(
                LogLevel.INFO,
                "%s Pass, %s Failed out of %s tests",
                mPassedTests,
                mFailedTests,
                mTotalTests);
        if (mCompleteModules != mTotalModules) {
            CLog.logAndDisplay(
                    LogLevel.ERROR,
                    "Some modules failed to run to completion, tests counts may be inaccurate.");
        }
    }

    public int getTotalModules() {
        return mTotalModules;
    }

    public int getCompleteModules() {
        return mCompleteModules;
    }

    public long getTotalTests() {
        return mTotalTests;
    }

    public long getPassedTests() {
        return mPassedTests;
    }

    public long getFailedTests() {
        return mFailedTests;
    }
}
