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
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.SubprocessEventHelper.BaseTestEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.FailedTestEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.InvocationFailedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestEndedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunEndedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunFailedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunStartedEventInfo;
import com.android.tradefed.util.SubprocessTestResultsParser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

/**
 * Implements {@link ITestInvocationListener} to be specified as a result_reporter and forward
 * from the subprocess the results of tests, test runs, test invocations.
 */
public class SubprocessResultsReporter implements ITestInvocationListener, AutoCloseable {

    @Option(name = "subprocess-report-file", description = "the file where to log the events.")
    private File mReportFile = null;

    @Option(name = "subprocess-report-port", description = "the port where to connect to send the"
            + "events.")
    private Integer mReportPort = null;

    private Socket mReportSocket = null;

    private boolean mPrintWarning = true;

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
    public void invocationStarted(IInvocationContext context) {
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
            if (mReportFile.canWrite()) {
                try {
                    try (FileWriter fw = new FileWriter(mReportFile, true)) {
                        String eventLog = String.format("%s %s\n", key, event.toString());
                        fw.append(eventLog);
                        fw.flush();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(
                        String.format("report file: %s is not writable",
                                mReportFile.getAbsolutePath()));
            }
        }
        if(mReportPort != null) {
            try {
                if (mReportSocket == null) {
                    mReportSocket = new Socket("localhost", mReportPort.intValue());
                }
                if (!mReportSocket.isConnected()) {
                    throw new RuntimeException("Reporter Socket is not connected");
                }
                PrintWriter out = new PrintWriter(mReportSocket.getOutputStream(), true);
                String eventLog = String.format("%s %s\n", key, event.toString());
                out.print(eventLog);
                out.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (mReportFile == null && mReportPort == null) {
            if (mPrintWarning) {
                // Only print the warning the first time.
                mPrintWarning = false;
                CLog.w("No report file or socket has been configured, skipping this reporter.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        StreamUtil.close(mReportSocket);
    }
}
