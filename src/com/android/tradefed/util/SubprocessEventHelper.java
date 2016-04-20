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

import com.android.tradefed.log.LogUtil.CLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Helper to serialize/deserialize the events to be passed to the log.
 */
public class SubprocessEventHelper {
    private static final String CLASSNAME_KEY = "className";
    private static final String TESTNAME_KEY = "testName";
    private static final String TRACE_KEY = "trace";
    private static final String CAUSE_KEY = "cause";
    private static final String RUNNAME_KEY = "runName";
    private static final String TESTCOUNT_KEY = "testCount";
    private static final String TIME_KEY = "time";
    private static final String REASON_KEY = "reason";

    /**
     * Helper for testRunStarted information
     */
    public static class TestRunStartedEventInfo {
        public String mRunName = null;
        public Integer mTestCount = null;

        public TestRunStartedEventInfo(String runName, int testCount) {
            mRunName = runName;
            mTestCount = testCount;
        }

        public TestRunStartedEventInfo(JSONObject jsonObject) throws JSONException {
            mRunName = jsonObject.getString(RUNNAME_KEY);
            mTestCount = jsonObject.getInt(TESTCOUNT_KEY);
        }

        @Override
        public String toString() {
            JSONObject tags = new JSONObject();
            try {
                if (mRunName != null) {
                    tags.put(RUNNAME_KEY, mRunName);
                }
                if (mTestCount != null) {
                    tags.put(TESTCOUNT_KEY, mTestCount.intValue());
                }
            } catch (JSONException e) {
                CLog.e(e);
            }
            return tags.toString();
        }
    }

    /**
     * Helper for testRunFailed information
     */
    public static class TestRunFailedEventInfo {
        public String mReason = null;

        public TestRunFailedEventInfo(String reason) {
            mReason = reason;
        }

        public TestRunFailedEventInfo(JSONObject jsonObject) throws JSONException {
            mReason = jsonObject.getString(REASON_KEY);
        }

        @Override
        public String toString() {
            JSONObject tags = new JSONObject();
            try {
                if (mReason != null) {
                    tags.put(REASON_KEY, mReason);
                }
            } catch (JSONException e) {
                CLog.e(e);
            }
            return tags.toString();
        }
    }

    /**
     * Helper for testRunEnded Information.
     */
    public static class TestRunEndedEventInfo {
        public Long mTime = null;
        public Map<String, String> mRunMetrics = null;

        public TestRunEndedEventInfo(Long time, Map<String, String> runMetrics) {
            mTime = time;
            mRunMetrics = runMetrics;
        }

        public TestRunEndedEventInfo(JSONObject jsonObject) throws JSONException {
            mTime = jsonObject.getLong(TIME_KEY);
            jsonObject.remove(TIME_KEY);
            Iterator<?> i = jsonObject.keys();
            mRunMetrics = new HashMap<String, String>();
            while(i.hasNext()) {
                String key = (String) i.next();
                mRunMetrics.put(key, jsonObject.get(key).toString());
            }
        }

        @Override
        public String toString() {
            JSONObject tags = null;
            try {
                if (mRunMetrics != null) {
                    tags = new JSONObject(mRunMetrics);
                } else {
                    tags = new JSONObject();
                }
                if (mTime != null) {
                    tags.put(TIME_KEY, mTime.longValue());
                }
            } catch (JSONException e) {
                CLog.e(e);
            }
            return tags.toString();
        }
    }

    /**
     * Helper for InvocationFailed information.
     */
    public static class InvocationFailedEventInfo {
        public Throwable mCause = null;

        public InvocationFailedEventInfo(Throwable cause) {
            mCause = cause;
        }

        public InvocationFailedEventInfo(JSONObject jsonObject) throws JSONException {
            String stack = jsonObject.getString("cause");
            mCause = new Throwable(stack);
        }

        @Override
        public String toString() {
            JSONObject tags = new JSONObject();
            try {
                if (mCause != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    mCause.printStackTrace(pw);
                    tags.put(CAUSE_KEY, sw.toString());
                }
            } catch (JSONException e) {
                CLog.e(e);
            }
            return tags.toString();
        }
    }

    /**
     * Base Helper for testStarted and TestIgnored information.
     */
    public static class BaseTestEventInfo {
        public String mClassName = null;
        public String mTestName = null;

        public BaseTestEventInfo(String className, String testName) {
            mClassName = className;
            mTestName = testName;
        }

        public BaseTestEventInfo(JSONObject jsonObject) throws JSONException {
            mClassName = jsonObject.getString(CLASSNAME_KEY);
            jsonObject.remove(CLASSNAME_KEY);
            mTestName = jsonObject.getString(TESTNAME_KEY);
            jsonObject.remove(TESTNAME_KEY);
        }

        protected JSONObject getNewJson() {
            return new JSONObject();
        }

        @Override
        public String toString() {
            JSONObject tags = null;
            try {
                tags = getNewJson();
                if (mClassName != null) {
                    tags.put(CLASSNAME_KEY, mClassName);
                }
                if (mTestName != null) {
                    tags.put(TESTNAME_KEY, mTestName);
                }
            } catch (JSONException e) {
                CLog.e(e);
            }
            return tags.toString();
        }
    }

    /**
     * Helper for testFailed information.
     */
    public static class FailedTestEventInfo extends BaseTestEventInfo {
        public String mTrace = null;

        public FailedTestEventInfo(String className, String testName, String trace) {
            super(className, testName);
            mTrace = trace;
        }

        public FailedTestEventInfo(JSONObject jsonObject) throws JSONException {
            super(jsonObject);
            mTrace = jsonObject.getString(TRACE_KEY);
        }

        @Override
        public String toString() {
            JSONObject tags = null;
            try {
                tags = new JSONObject(super.toString());
                if (mTrace != null) {
                    tags.put(TRACE_KEY, mTrace);
                }
            } catch (JSONException e) {
                CLog.e(e);
            }
            return tags.toString();
        }
    }

    /**
     * Helper for testEnded information.
     */
    public static class TestEndedEventInfo extends BaseTestEventInfo {
        public Map<String, String> mRunMetrics = null;

        public TestEndedEventInfo(String className, String testName,
                Map<String, String> runMetrics) {
            super(className, testName);
            mRunMetrics = runMetrics;
        }

        public TestEndedEventInfo(JSONObject jsonObject) throws JSONException {
            super(jsonObject);
            Iterator<?> i = jsonObject.keys();
            mRunMetrics = new HashMap<String, String>();
            while(i.hasNext()) {
                String key = (String) i.next();
                mRunMetrics.put(key, jsonObject.get(key).toString());
            }
        }

        @Override
        protected JSONObject getNewJson() {
            if (mRunMetrics != null) {
                return new JSONObject(mRunMetrics);
            } else {
                return new JSONObject();
            }
        }
    }
}
