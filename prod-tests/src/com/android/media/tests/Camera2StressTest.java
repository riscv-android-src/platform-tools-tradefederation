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
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.StubTestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Camera2 stress test
 * Since Camera stress test can drain the battery seriously. Need to split
 * the test suite into separate test invocation for each test method.
 * <p/>
 */
@OptionClass(alias = "camera2-stress")
public class Camera2StressTest implements IDeviceTest, IRemoteTest {

    @Option(name = "testPackage", description = "Test package to run.")
    private String mTestPackage = "com.google.android.camera";

    @Option(name = "testClass", description = "Test class to run.")
    private String mTestClass = "com.android.camera.stress.GoogleCameraStressTest";

    @Option(name = "testMethod", description = "Test method to run. May be repeated.")
    private Collection<String> mTestMethods = new ArrayList<String>();

    @Option(name = "testRunner", description = "Test runner for test instrumentation.")
    private String mTestRunner = "android.test.InstrumentationTestRunner";

    @Option(name = "test-timeout-ms", description = "Max time allowed in ms for a test run.")
    private int mTestTimeoutMs = 6 * 60 * 60 * 1000; // 6 hours

    private String mRuKey = "camera_app_stress";
    private ITestDevice mDevice = null;


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
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        CollectingListener collectingListener = new CollectingListener();
        runTest(collectingListener);
        postMetrics(listener, collectingListener.mStdout);
    }

    /***
     * Start the instrumentation test.
     * @param listener
     * @throws DeviceNotAvailableException
     */
    private void runTest(ITestInvocationListener listener) throws DeviceNotAvailableException {
        InstrumentationTest instr = new InstrumentationTest();
        instr.setDevice(getDevice());
        instr.setPackageName(mTestPackage);
        instr.setRunnerName(mTestRunner);
        instr.setClassName(mTestClass);
        instr.setTestTimeout(mTestTimeoutMs);
        instr.setShellTimeout(mTestTimeoutMs);
        if (mTestMethods.size() > 0) {
            CLog.d("TEST METHOD > 0");
            for (String testName : mTestMethods) {
                instr.setMethodName(testName);
                instr.run(listener);
            }
        } else {
            instr.run(listener);
        }
    }

    /**
     * A listener to collect the output from test run.
     */
    private class CollectingListener extends StubTestInvocationListener {
        public Map<String, String> mStdout = new HashMap<String, String>();

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            for (Map.Entry<String, String> metric : testMetrics.entrySet()) {
                mStdout.put(test.getTestName(), metric.getValue());
            }
        }
    }

    /**
     * Report run metrics
     *
     * @param listener The {@link ITestInvocationListener} of test results
     * @param metrics The {@link Map} that contains metrics for the given test
     */
    private void postMetrics(ITestInvocationListener listener, Map<String, String> metrics) {
        listener.testRunStarted(mRuKey, 0);
        listener.testRunEnded(0, metrics);
        CLog.v("test metric", metrics.toString());
    }
}
