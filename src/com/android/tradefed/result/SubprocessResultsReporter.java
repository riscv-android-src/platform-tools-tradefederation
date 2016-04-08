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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.SubprocessEventHelper.BaseTestEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.FailedTestEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.InvocationFailedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunEndedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunFailedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunStartedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestEndedEventInfo;
import com.android.tradefed.util.SubprocessTestResultsParser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Implements {@link ITestInvocationListener} to be specified as a result_reporter and forward
 * from the subprocess the results of tests, test runs, test invocations.
 */
public class SubprocessResultsReporter implements ITestInvocationListener {

    @Option(name = "subprocess-report-file", description = "the file where to log the events.")
    private String mReportFile = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier testId, String trace) {
        FailedTestEventInfo info =
                new FailedTestEventInfo(testId.getClassName(), testId.getTestName(), trace);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_ASSUMPTION_FAILURE, info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier testId, Map<String, String> metrics) {
        TestEndedEventInfo info =
                new TestEndedEventInfo(testId.getClassName(), testId.getTestName(), metrics);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_ENDED, info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier testId, String reason) {
        FailedTestEventInfo info =
                new FailedTestEventInfo(testId.getClassName(), testId.getTestName(), reason);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_FAILED, info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier testId) {
        BaseTestEventInfo info = new BaseTestEventInfo(testId.getClassName(), testId.getTestName());
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_IGNORED, info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long time, Map<String, String> runMetrics) {
        TestRunEndedEventInfo info = new TestRunEndedEventInfo(time, runMetrics);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_RUN_ENDED, info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String reason) {
        TestRunFailedEventInfo info = new TestRunFailedEventInfo(reason);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_RUN_FAILED, info);
    }

    @Override
    public void testRunStarted(String runName, int testCount) {
        TestRunStartedEventInfo info = new TestRunStartedEventInfo(runName, testCount);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_RUN_STARTED, info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long arg0) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier testId) {
        BaseTestEventInfo info = new BaseTestEventInfo(testId.getClassName(), testId.getTestName());
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_STARTED, info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        // TODO
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        InvocationFailedEventInfo info = new InvocationFailedEventInfo(cause);
        printEvent(SubprocessTestResultsParser.StatusKeys.INVOCATION_FAILED, info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        return null;
    }

    /**
     * Helper to print the event key and then the json object.
     */
    public void printEvent(String key, Object event) {
        if (mReportFile != null) {
            File eventFile = new File(mReportFile);
            if (eventFile.canWrite()) {
                try {
                    FileWriter fw = new FileWriter(eventFile, true);
                    String eventLog = String.format("%s %s\n", key, event.toString());
                    fw.append(eventLog);
                    fw.flush();
                    fw.close();
                } catch (IOException e) {
                    CLog.e(e);
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(
                        String.format("report file: %s is not writable", mReportFile));
            }
        } else {
            CLog.w("No report file has been specified, skipping this reporter.");
        }
    }
}
