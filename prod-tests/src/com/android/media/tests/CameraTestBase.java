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

package com.android.media.tests;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.StubTestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Camera test base class
 *
 * Camera2StressTest, CameraStartupTest, Camera2LatencyTest and CameraPerformanceTest use this base
 * class for Camera ivvavik and later.
 */
public class CameraTestBase implements IDeviceTest, IRemoteTest {

    private static final String LOG_TAG = CameraTestBase.class.getSimpleName();

    @Option(name = "test-package", description = "Test package to run.")
    private String mTestPackage = "com.google.android.camera";

    @Option(name = "test-class", description = "Test class to run.")
    private String mTestClass = null;

    @Option(name = "test-methods", description = "Test method to run. May be repeated.")
    private Collection<String> mTestMethods = new ArrayList<String>();

    @Option(name = "test-runner", description = "Test runner for test instrumentation.")
    private String mTestRunner = "android.test.InstrumentationTestRunner";

    @Option(name = "test-timeout-ms", description = "Max time allowed in ms for a test run.")
    private int mTestTimeoutMs = 60 * 60 * 1000; // 1 hour

    @Option(name = "ru-key", description = "Result key to use when posting to the dashboard.")
    private String mRuKey = null;

    @Option(name = "logcat-on-failure", description =
            "take a logcat snapshot on every test failure.")
    private boolean mLogcatOnFailure = false;

    @Option(name = "instrumentation-arg",
            description = "Additional instrumentation arguments to provide.")
    private Map<String, String> mInstrArgMap = new HashMap<String, String>();

    private ITestDevice mDevice = null;

    // A base listener to collect the results from each test run. Fatal error or failures will be
    // forwarded to other listeners.
    private AbstractCollectingListener mCollectingListener = null;

    private long mStartTimeMs = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // ignore
    }

    /**
     * Run Camera instrumentation test with a default listener.
     *
     * @param listener the ITestInvocationListener of test results
     * @throws DeviceNotAvailableException
     */
    protected void runInstrumentationTest(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        if (mCollectingListener == null) {
            mCollectingListener = new DefaultCollectingListener(listener);

        }
        runInstrumentationTest(listener, mCollectingListener);
    }

    /**
     * Run Camera instrumentation test with a listener to gather the metrics from the individual
     * test runs.
     *
     * @param listener the ITestInvocationListener of test results
     * @param collectingListener the {@link CollectingTestListener} to collect the metrics from
     *                           test runs
     * @throws DeviceNotAvailableException
     */
    protected void runInstrumentationTest(ITestInvocationListener listener,
            AbstractCollectingListener collectingListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(collectingListener);
        mCollectingListener = collectingListener;

        InstrumentationTest instr = new InstrumentationTest();
        instr.setDevice(getDevice());
        instr.setPackageName(getTestPackage());
        instr.setRunnerName(getTestRunner());
        instr.setClassName(getTestClass());
        instr.setTestTimeout(getTestTimeoutMs());
        instr.setShellTimeout(getTestTimeoutMs());
        instr.setLogcatOnFailure(mLogcatOnFailure);
        for (Map.Entry<String, String> entry : mInstrArgMap.entrySet()) {
            instr.addInstrumentationArg(entry.getKey(), entry.getValue());
        }

        mStartTimeMs = System.currentTimeMillis();
        listener.testRunStarted(getRuKey(), 1);

        // Run tests.
        if (mTestMethods.size() > 0) {
            CLog.d(String.format("The number of test methods is: %d", mTestMethods.size()));
            for (String testName : mTestMethods) {
                instr.setMethodName(testName);
                instr.run(mCollectingListener);
            }
        } else {
            instr.run(mCollectingListener);
        }

        // Delegate a post process after test run ends to subclasses.
        handleTestRunEnded(listener, mCollectingListener.getAggregatedMetrics());
    }

    /**
     * Report the start of an camera test run ended. Intended only when subclasses handle
     * the aggregated results at the end of test run. {@link ITestInvocationListener#testRunEnded}
     * should be called to report end of test run in the method.
     *
     * @param listener - the ITestInvocationListener of test results
     * @param collectedMetrics - the metrics aggregated during the individual test.
     */
    protected void handleTestRunEnded(ITestInvocationListener listener,
            Map<String, String> collectedMetrics) {

        // Report metrics at the end of test run.
        listener.testRunEnded(getTestDurationMs(), collectedMetrics);
    }

    /**
     * A base listener to collect the results from each test run. Fatal error or failures will be
     * forwarded to other listeners.
     */
    protected abstract class AbstractCollectingListener extends StubTestInvocationListener {

        private ITestInvocationListener mListener = null;
        private Map<String, String> mMetrics = new HashMap<String, String>();
        private Map<String, String> mFatalErrors = new HashMap<String, String>();
        private int mTestCount = 0;

        private static final String INCOMPLETE_TEST_ERR_MSG_PREFIX =
                "Test failed to run to completion. Reason: 'Instrumentation run failed";

        public AbstractCollectingListener(ITestInvocationListener listener) {
            mListener = listener;
        }

        /**
         * Report the end of an individual camera test and delegate handling the collected metrics
         * to subclasses.
         *
         * @param test identifies the test
         * @param testMetrics a {@link Map} of the metrics emitted
         */
        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            handleCollectedMetrics(test, testMetrics);
            mListener.testEnded(test, testMetrics);
        }

        /**
         * Override only when subclasses parse the raw metrics passed from Camera instrumentation
         * tests, then update the getAggregatedMetrics with parsed data to post to dashboard.
         *
         * @param test identifies the test
         * @param testMetrics a {@link Map} of the metrics emitted
         */
        abstract public void handleCollectedMetrics(TestIdentifier test,
                Map<String, String> testMetrics);

        @Override
        public void testStarted(TestIdentifier test) {
            ++mTestCount;
            mListener.testStarted(test);
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            // If the test failed to run to complete, this is an exceptional case.
            // Let this test run fail so that it can rerun.
            if (trace.startsWith(INCOMPLETE_TEST_ERR_MSG_PREFIX)) {
                mFatalErrors.put(test.getTestName(), trace);
                CLog.d("Test (%s) failed due to fatal error : %s", test.getTestName(), trace);
            }
            mListener.testFailed(test, trace);
        }

        @Override
        public void invocationFailed(Throwable cause) {
            mListener.invocationFailed(cause);
        }

        public Map<String, String> getAggregatedMetrics() {
            return mMetrics;
        }

        /**
         * Determine that the test run failed with fatal errors.
         *
         * @return True if test run has a failure due to fatal error.
         */
        public boolean hasTestRunFatalError() {
            return (mTestCount > 0 && mFatalErrors.size() > 0);
        }

        public Map<String, String> getFatalErrors() {
            return mFatalErrors;
        }

        public String getErrorMessage() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> error : mFatalErrors.entrySet()) {
                sb.append(error.getKey());
                sb.append(" : ");
                sb.append(error.getValue());
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    protected class DefaultCollectingListener extends AbstractCollectingListener {

        public DefaultCollectingListener(ITestInvocationListener listener) {
            super(listener);
        }

        public void handleCollectedMetrics(TestIdentifier test, Map<String, String> testMetrics) {
            for (Map.Entry<String, String> metric : testMetrics.entrySet()) {
                getAggregatedMetrics().put(test.getTestName(), metric.getValue());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Get the duration of Camera test instrumentation in milliseconds.
     *
     * @return the duration of Camera instrumentation test until it is called.
     */
    public long getTestDurationMs() {
        return System.currentTimeMillis() - mStartTimeMs;
    }

    public String getTestPackage() {
        return mTestPackage;
    }

    public void setTestPackage(String testPackage) {
        mTestPackage = testPackage;
    }

    public String getTestClass() {
        return mTestClass;
    }

    public void setTestClass(String testClass) {
        mTestClass = testClass;
    }

    public String getTestRunner() {
        return mTestRunner;
    }

    public void setTestRunner(String testRunner) {
        mTestRunner = testRunner;
    }

    public int getTestTimeoutMs() {
        return mTestTimeoutMs;
    }

    public void setTestTimeoutMs(int testTimeoutMs) {
        mTestTimeoutMs = testTimeoutMs;
    }

    public String getRuKey() {
        return mRuKey;
    }

    public void setRuKey(String ruKey) {
        mRuKey = ruKey;
    }

    public AbstractCollectingListener getCollectingListener() {
        return mCollectingListener;
    }

    public void setLogcatOnFailure(boolean logcatOnFailure) {
        mLogcatOnFailure = logcatOnFailure;
    }
}
