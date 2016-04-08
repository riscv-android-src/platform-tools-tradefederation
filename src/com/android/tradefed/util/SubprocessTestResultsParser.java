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
package com.android.tradefed.util;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.SubprocessEventHelper.BaseTestEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.FailedTestEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.InvocationFailedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestEndedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunEndedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunFailedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunStartedEventInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extends {@link FileOutputStream} to parse the output before writing to the file so we can
 * generate the test events on the launcher side.
 */
public class SubprocessTestResultsParser {

    private ITestInvocationListener mListener;
    private TestIdentifier currentTest = null;
    private Pattern mPattern = null;
    private Map<String, EventHandler> mHandlerMap = null;

    /** Relevant test status keys. */
    public static class StatusKeys {
        public static final String INVOCATION_FAILED = "INVOCATION_FAILED";
        public static final String TEST_ASSUMPTION_FAILURE = "TEST_ASSUMPTION_FAILURE";
        public static final String TEST_ENDED = "TEST_ENDED";
        public static final String TEST_FAILED = "TEST_FAILED";
        public static final String TEST_IGNORED = "TEST_IGNORED";
        public static final String TEST_STARTED = "TEST_STARTED";
        public static final String TEST_RUN_ENDED = "TEST_RUN_ENDED";
        public static final String TEST_RUN_FAILED = "TEST_RUN_FAILED";
        public static final String TEST_RUN_STARTED = "TEST_RUN_STARTED";
    }

    public SubprocessTestResultsParser(ITestInvocationListener listener) {
        mListener = listener;
        StringBuilder sb = new StringBuilder();
        sb.append(StatusKeys.INVOCATION_FAILED).append("|");
        sb.append(StatusKeys.TEST_ASSUMPTION_FAILURE).append("|");
        sb.append(StatusKeys.TEST_ENDED).append("|");
        sb.append(StatusKeys.TEST_FAILED).append("|");
        sb.append(StatusKeys.TEST_IGNORED).append("|");
        sb.append(StatusKeys.TEST_STARTED).append("|");
        sb.append(StatusKeys.TEST_RUN_ENDED).append("|");
        sb.append(StatusKeys.TEST_RUN_FAILED).append("|");
        sb.append(StatusKeys.TEST_RUN_STARTED);
        String patt = String.format("(.*)(%s)( )(.*)", sb.toString());
        mPattern = Pattern.compile(patt);

        // Create Handler map for each event
        mHandlerMap = new HashMap<String, EventHandler>();
        mHandlerMap.put(StatusKeys.INVOCATION_FAILED, new InvocationFailedEventHandler());
        mHandlerMap.put(StatusKeys.TEST_ASSUMPTION_FAILURE,
                new TestAssumptionFailureEventHandler());
        mHandlerMap.put(StatusKeys.TEST_ENDED, new TestEndedEventHandler());
        mHandlerMap.put(StatusKeys.TEST_FAILED, new TestFailedEventHandler());
        mHandlerMap.put(StatusKeys.TEST_IGNORED, new TestIgnoredEventHandler());
        mHandlerMap.put(StatusKeys.TEST_STARTED, new TestStartedEventHandler());
        mHandlerMap.put(StatusKeys.TEST_RUN_ENDED, new TestRunEndedEventHandler());
        mHandlerMap.put(StatusKeys.TEST_RUN_FAILED, new TestRunFailedEventHandler());
        mHandlerMap.put(StatusKeys.TEST_RUN_STARTED, new TestRunStartedEventHandler());
    }

    public void parseFile(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }
        ArrayList<String> listString = new ArrayList<String>();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                listString.add(line);
            }
            reader.close();
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }
        processNewLines(listString.toArray(new String[listString.size()]));
    }

    /**
     * call parse on each line of the array to extract the events if any.
     */
    public void processNewLines(String[] lines) {
        for (String line : lines) {
            try {
                parse(line);
            } catch (JSONException e) {
                CLog.e("Exception while parsing");
                CLog.e(e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Parse a line, if it matches one of the events, handle it.
     */
    private void parse(String line) throws JSONException {
        Matcher matcher = mPattern.matcher(line);
        if (matcher.find()) {
            EventHandler handler = mHandlerMap.get(matcher.group(2));
            if (handler != null) {
                handler.handleEvent(matcher.group(4));
            } else {
                CLog.w("No handler found matching: %s", matcher.group(2));
            }
        }
    }

    private void checkCurrentTestId(String className, String testName) {
        if (currentTest == null) {
            currentTest = new TestIdentifier(className, testName);
            CLog.w("Calling a test event without having called testStarted.");
        }
    }

    /**
     * Interface for event handling
     */
    interface EventHandler {
        public void handleEvent(String eventJson) throws JSONException;
    }

    private class TestRunStartedEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            TestRunStartedEventInfo rsi = new TestRunStartedEventInfo(new JSONObject(eventJson));
            mListener.testRunStarted(rsi.mRunName, rsi.mTestCount);
        }
    }

    private class TestRunFailedEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            TestRunFailedEventInfo rfi = new TestRunFailedEventInfo(new JSONObject(eventJson));
            mListener.testRunFailed(rfi.mReason);
        }
    }

    private class TestRunEndedEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            try {
                TestRunEndedEventInfo rei = new TestRunEndedEventInfo(new JSONObject(eventJson));
                mListener.testRunEnded(rei.mTime, rei.mRunMetrics);
            } finally {
                currentTest = null;
            }
        }
    }

    private class InvocationFailedEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            InvocationFailedEventInfo ifi =
                    new InvocationFailedEventInfo(new JSONObject(eventJson));
            mListener.invocationFailed(ifi.mCause);
        }
    }

    private class TestStartedEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            BaseTestEventInfo bti = new BaseTestEventInfo(new JSONObject(eventJson));
            currentTest = new TestIdentifier(bti.mClassName, bti.mTestName);
            mListener.testStarted(currentTest);
        }
    }

    private class TestFailedEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            FailedTestEventInfo fti = new FailedTestEventInfo(new JSONObject(eventJson));
            checkCurrentTestId(fti.mClassName, fti.mTestName);
            mListener.testFailed(currentTest, fti.mTrace);
        }
    }

    private class TestEndedEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            try {
                TestEndedEventInfo tei = new TestEndedEventInfo(new JSONObject(eventJson));
                checkCurrentTestId(tei.mClassName, tei.mTestName);
                mListener.testEnded(currentTest, tei.mRunMetrics);
            } finally {
                currentTest = null;
            }
        }
    }

    private class TestIgnoredEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            BaseTestEventInfo baseTestIgnored = new BaseTestEventInfo(new JSONObject(eventJson));
            checkCurrentTestId(baseTestIgnored.mClassName, baseTestIgnored.mTestName);
            mListener.testIgnored(currentTest);
        }
    }

    private class TestAssumptionFailureEventHandler implements EventHandler {
        @Override
        public void handleEvent(String eventJson) throws JSONException {
            FailedTestEventInfo FailedAssumption =
                    new FailedTestEventInfo(new JSONObject(eventJson));
            checkCurrentTestId(FailedAssumption.mClassName, FailedAssumption.mTestName);
            mListener.testAssumptionFailure(currentTest, FailedAssumption.mTrace);
        }
    }
}
