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
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
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

    private ITestDevice mDevice = null;
    private CollectingListenerBase mCollectingListener = null;
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
            mCollectingListener = new CollectingListenerBase();

        }
        runInstrumentationTest(listener, mCollectingListener);
    }

    /**
     * Run Camera instrumentation test with a listener to gather the metrics from the individual
     * test runs.
     *
     * @param listener the ITestInvocationListener of test results
     * @param collectingListener the listener to collect the metrics from test runs
     * @throws DeviceNotAvailableException
     */
    protected void runInstrumentationTest(ITestInvocationListener listener,
            CollectingListenerBase collectingListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(collectingListener);

        InstrumentationTest instr = new InstrumentationTest();
        instr.setDevice(getDevice());
        instr.setPackageName(getTestPackage());
        instr.setRunnerName(getTestRunner());
        instr.setClassName(getTestClass());
        instr.setTestTimeout(getTestTimeoutMs());
        instr.setShellTimeout(getTestTimeoutMs());

        mStartTimeMs = System.currentTimeMillis();
        listener.testRunStarted(getRuKey(), 1);

        // Run tests.
        if (mTestMethods.size() > 0) {
            CLog.d(String.format("The number of test methods is: %d", mTestMethods.size()));
            for (String testName : mTestMethods) {
                instr.setMethodName(testName);
                instr.run(new ResultForwarder(listener, collectingListener));
            }
        } else {
            instr.run(new ResultForwarder(listener, collectingListener));
        }

        // Delegate a post process after test run ends to subclasses.
        handleTestRunEnded(listener, collectingListener.getMetrics());
    }

    /**
     * Report the start of an camera test run ended. Intended only when subclasses handle
     * the aggregated results at the end of test run. Either
     * {@link ITestInvocationListener#testRunEnded} or {@link ITestInvocationListener#testRunFailed}
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
    protected class CollectingListenerBase extends StubTestInvocationListener {

        private Map<String, String> mMetrics = new HashMap<String, String>();

        public Map<String, String> getMetrics() {
            return mMetrics;
        }

        public void setMetrics(Map<String, String> metrics) {
            mMetrics = metrics;
        }

        /**
         * Report the end of an individual camera test. Intended only when subclasses set the
         * aggregated metrics on purpose.
         *
         * @param test identifies the test
         * @param testMetrics a {@link Map} of the metrics emitted
         */
        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            for (Map.Entry<String, String> metric : testMetrics.entrySet()) {
                getMetrics().put(test.getTestName(), metric.getValue());
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
}
