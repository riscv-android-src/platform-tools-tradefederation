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
package com.android.tradefed.result;

import com.android.loganalysis.item.JavaCrashItem;
import com.android.loganalysis.item.LogcatItem;
import com.android.loganalysis.parser.LogcatParser;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Special ResultForwarder that extract a logcat crash and adds it to the failure message if it
 * detects a crash condition.
 */
public class LogcatCrashResultForwarder extends ResultForwarder {

    /** Special error message from the instrumentation when something goes wrong on device side. */
    public static final String ERROR_MESSAGE = "Process crashed.";

    private Long mStartTime = null;
    private Long mLastStartTime = null;
    private ITestDevice mDevice;
    private LogcatItem mLogcatItem = null;

    public LogcatCrashResultForwarder(ITestDevice device, ITestInvocationListener... listeners) {
        super(listeners);
        mDevice = device;
    }

    @Override
    public void testStarted(TestDescription test, long startTime) {
        mStartTime = startTime;
        super.testStarted(test, startTime);
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        // If the test case was detected as crashing the instrumentation, we had the crash to it.
        if (trace.contains(ERROR_MESSAGE) && mStartTime != null) {
            mLogcatItem = extractLogcat(mDevice, mStartTime);
            trace = addJavaCrashToString(mLogcatItem, trace);
        }
        super.testFailed(test, trace);
    }

    @Override
    public void testEnded(TestDescription test, long endTime, Map<String, String> testMetrics) {
        super.testEnded(test, endTime, testMetrics);
        mLastStartTime = mStartTime;
        mStartTime = null;
    }

    @Override
    public void testRunFailed(String errorMessage) {
        // Also add the failure to the run failure if the testFailed generated it.
        if (mLogcatItem != null) {
            super.testRunFailed(addJavaCrashToString(mLogcatItem, errorMessage));
            mLogcatItem = null;
            return;
        } else if (errorMessage.contains(ERROR_MESSAGE) && mLastStartTime != null) {
            // If the test did not generate the crash but a crash is seen, only add it to the run
            // failure message.
            mLogcatItem = extractLogcat(mDevice, mLastStartTime);
            errorMessage = addJavaCrashToString(mLogcatItem, errorMessage);
        }
        super.testRunFailed(errorMessage);
        mLogcatItem = null;
    }

    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        mLastStartTime = null;
    }

    /**
     * Extract a formatted object from the logcat snippet.
     *
     * @param device The device from which to pull the logcat.
     * @param startTime The beginning time of the last tests.
     * @return A {@link LogcatItem} that contains the information inside the logcat.
     */
    private LogcatItem extractLogcat(ITestDevice device, long startTime) {
        try (InputStreamSource logSource = device.getLogcatSince(startTime)) {
            String message = StreamUtil.getStringFromStream(logSource.createInputStream());
            LogcatParser parser = new LogcatParser();
            List<String> lines = Arrays.asList(message.split("\n"));
            return parser.parse(lines);
        } catch (IOException e) {
            CLog.e(e);
        }
        return null;
    }

    /** Append the Java crash information to the failure message. */
    private String addJavaCrashToString(LogcatItem item, String errorMsg) {
        if (item == null) {
            return errorMsg;
        }
        List<JavaCrashItem> crashes = item.getJavaCrashes();
        if (!crashes.isEmpty()) {
            for (JavaCrashItem c : crashes) {
                errorMsg =
                        String.format(
                                "%s\nCrash Message:%s\n%s", errorMsg, c.getMessage(), c.getStack());
            }
        }
        return errorMsg;
    }
}
