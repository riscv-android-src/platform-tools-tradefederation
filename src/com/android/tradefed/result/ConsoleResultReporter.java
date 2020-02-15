/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result reporter to print the test results to the console.
 *
 * <p>Prints each test run, each test case, and test metrics, test logs, and test file locations.
 *
 * <p>
 */
@OptionClass(alias = "console-result-reporter")
public class ConsoleResultReporter extends TestResultListener
        implements ILogSaverListener, ITestInvocationListener {

    private static final SimpleDateFormat sTimeStampFormat = new SimpleDateFormat("HH:mm:ss");

    @Option(
            name = "suppress-passed-tests",
            description =
                    "For functional tests, ommit summary for "
                            + "passing tests, only print failed and ignored ones")
    private boolean mSuppressPassedTest = false;

    private final PrintStream mStream;
    private Set<LogFile> mLogFiles = new LinkedHashSet<>();
    private String mTestTag;
    private CountingTestResultListener mResultCountListener = new CountingTestResultListener();

    public ConsoleResultReporter() {
        this(System.out);
    }

    ConsoleResultReporter(PrintStream outputStream) {
        mStream = outputStream;
    }

    @Override
    public void invocationStarted(IInvocationContext context) {
        mTestTag = context.getTestTag();
    }

    @Override
    public void testResult(TestDescription test, TestResult result) {
        mResultCountListener.testResult(test, result);
        if (mSuppressPassedTest && result.getStatus() == TestStatus.PASSED) {
            return;
        }
        print(getTestSummary(test, result));
    }

    @Override
    public void testRunFailed(String errorMessage) {
        print(String.format("%s: run failed: %s\n", mTestTag, errorMessage));
    }

    /** {@inheritDoc} */
    @Override
    public void invocationEnded(long elapsedTime) {
        int[] results = mResultCountListener.getResultCounts();
        StringBuilder sb = new StringBuilder();
        sb.append(mTestTag);
        sb.append(" results: ");
        sb.append(mResultCountListener.getTotalTests());
        sb.append(" Tests ");
        sb.append(results[TestStatus.PASSED.ordinal()]);
        sb.append(" Passed ");
        if (results[TestStatus.FAILURE.ordinal()] > 0) {
            sb.append(results[TestStatus.FAILURE.ordinal()]);
            sb.append(" Failed ");
        }
        if (results[TestStatus.IGNORED.ordinal()] > 0) {
            sb.append(results[TestStatus.IGNORED.ordinal()]);
            sb.append(" Ignored ");
        }
        if (results[TestStatus.ASSUMPTION_FAILURE.ordinal()] > 0) {
            sb.append(results[TestStatus.ASSUMPTION_FAILURE.ordinal()]);
            sb.append(" Assumption failures ");
        }
        if (results[TestStatus.INCOMPLETE.ordinal()] > 0) {
            sb.append(results[TestStatus.INCOMPLETE.ordinal()]);
            sb.append(" Incomplete");
        }
        sb.append("\r\n");
        print(sb.toString());
    }

    /** {@inheritDoc} */
    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        printLog(logFile);
    }

    /** {@inheritDoc} */
    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {
        printLog(logFile);
    }

    private void printLog(LogFile logFile) {
        if (mSuppressPassedTest && !mResultCountListener.hasFailedTests()) {
            // all tests passed, skip logging
            return;
        }
        String logDesc = logFile.getUrl() == null ? logFile.getPath() : logFile.getUrl();
        print("Log: " + logDesc + "\r\n");
    }

    /** Get the test summary as string including test metrics. */
    static String getTestSummary(TestDescription testId, TestResult testResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                String.format(
                        "  %s: %s (%dms)\n",
                        testId.toString(),
                        testResult.getStatus(),
                        testResult.getEndTime() - testResult.getStartTime()));
        String stack = testResult.getStackTrace();
        if (stack != null && !stack.isEmpty()) {
            sb.append("  stack=\n");
            String lines[] = stack.split("\\r?\\n");
            for (String line : lines) {
                sb.append(String.format("    %s\n", line));
            }
        }
        Map<String, String> metrics = testResult.getMetrics();
        if (metrics != null && !metrics.isEmpty()) {
            List<String> metricKeys = new ArrayList<String>(metrics.keySet());
            Collections.sort(metricKeys);
            for (String metricKey : metricKeys) {
                sb.append(String.format("    %s: %s\n", metricKey, metrics.get(metricKey)));
            }
        }

        return sb.toString();
    }

    private void print(String msg) {
        mStream.print(sTimeStampFormat.format(new Date()) + " " + msg);
    }
}
