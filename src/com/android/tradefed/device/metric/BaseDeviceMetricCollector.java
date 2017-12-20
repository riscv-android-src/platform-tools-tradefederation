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
package com.android.tradefed.device.metric;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import java.util.List;
import java.util.Map;

/**
 * Base implementation of {@link IMetricCollector} that allows to start and stop collection on
 * {@link #onTestRunStart(DeviceMetricData)} and {@link #onTestRunEnd(DeviceMetricData, Map)}.
 */
public class BaseDeviceMetricCollector implements IMetricCollector {

    private IInvocationContext mContext;
    private ITestInvocationListener mForwarder;
    private DeviceMetricData mRunData;
    private DeviceMetricData mTestData;
    private String mTag;

    @Override
    public ITestInvocationListener init(
            IInvocationContext context, ITestInvocationListener listener) {
        mContext = context;
        mForwarder = listener;
        return this;
    }

    @Override
    public final List<ITestDevice> getDevices() {
        return mContext.getDevices();
    }

    @Override
    public final List<IBuildInfo> getBuildInfos() {
        return mContext.getBuildInfos();
    }

    @Override
    public final ITestInvocationListener getInvocationListener() {
        return mForwarder;
    }

    @Override
    public void onTestRunStart(DeviceMetricData runData) {
        // Does nothing
    }

    @Override
    public void onTestRunEnd(
            DeviceMetricData runData, final Map<String, String> currentRunMetrics) {
        // Does nothing
    }

    @Override
    public void onTestStart(DeviceMetricData testData) {
        // Does nothing
    }

    @Override
    public void onTestEnd(
            DeviceMetricData testData, final Map<String, String> currentTestCaseMetrics) {
        // Does nothing
    }

    /** =================================== */
    /** Invocation Listeners for forwarding */
    @Override
    public final void invocationStarted(IInvocationContext context) {
        mForwarder.invocationStarted(context);
    }

    @Override
    public final void invocationFailed(Throwable cause) {
        mForwarder.invocationFailed(cause);
    }

    @Override
    public final void invocationEnded(long elapsedTime) {
        mForwarder.invocationEnded(elapsedTime);
    }

    @Override
    public final void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        mForwarder.testLog(dataName, dataType, dataStream);
    }

    /** Test run callbacks */
    @Override
    public final void testRunStarted(String runName, int testCount) {
        mRunData = new DeviceMetricData();
        try {
            onTestRunStart(mRunData);
        } catch (Throwable t) {
            // Prevent exception from messing up the status reporting.
            CLog.e(t);
        }
        mForwarder.testRunStarted(runName, testCount);
    }

    @Override
    public final void testRunFailed(String errorMessage) {
        mForwarder.testRunFailed(errorMessage);
    }

    @Override
    public final void testRunStopped(long elapsedTime) {
        mForwarder.testRunStopped(elapsedTime);
    }

    @Override
    public final void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        try {
            onTestRunEnd(mRunData, runMetrics);
            mRunData.addToMetrics(runMetrics);
        } catch (Throwable t) {
            // Prevent exception from messing up the status reporting.
            CLog.e(t);
        }
        mForwarder.testRunEnded(elapsedTime, runMetrics);
    }

    /** Test cases callbacks */
    @Override
    public final void testStarted(TestIdentifier test) {
        testStarted(test, System.currentTimeMillis());
    }

    @Override
    public final void testStarted(TestIdentifier test, long startTime) {
        mTestData = new DeviceMetricData();
        try {
            onTestStart(mTestData);
        } catch (Throwable t) {
            // Prevent exception from messing up the status reporting.
            CLog.e(t);
        }
        mForwarder.testStarted(test, startTime);
    }

    @Override
    public final void testFailed(TestIdentifier test, String trace) {
        mForwarder.testFailed(test, trace);
    }

    @Override
    public final void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        testEnded(test, System.currentTimeMillis(), testMetrics);
    }

    @Override
    public final void testEnded(
            TestIdentifier test, long endTime, Map<String, String> testMetrics) {
        try {
            onTestEnd(mTestData, testMetrics);
            mTestData.addToMetrics(testMetrics);
        } catch (Throwable t) {
            // Prevent exception from messing up the status reporting.
            CLog.e(t);
        }
        mForwarder.testEnded(test, endTime, testMetrics);
    }

    @Override
    public final void testAssumptionFailure(TestIdentifier test, String trace) {
        mForwarder.testAssumptionFailure(test, trace);
    }

    @Override
    public final void testIgnored(TestIdentifier test) {
        mForwarder.testIgnored(test);
    }

    /**
     * Sets the {@code mTag} of the collector. It can be used to specify the interval of the
     * collector.
     *
     * @param tag the unique identifier of the collector.
     */
    public void setTag(String tag) {
        mTag = tag;
    }

    /**
     * Returns the identifier {@code mTag} of the collector.
     *
     * @return mTag, the unique identifier of the collector.
     */
    public String getTag() {
        return mTag;
    }
}
