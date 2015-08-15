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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Camera app latency test
 *
 * Runs Camera app latency test to measure Camera capture session time and reports the metrics.
 */
@OptionClass(alias = "camera-latency")
public class Camera2LatencyTest implements IDeviceTest, IRemoteTest {

    private static final Pattern STATS_REGEX = Pattern.compile(
        "^(?<latency>[0-9.]+)\\|(?<values>[0-9 .-]+)");

    private static final String TEST_PACKAGE_NAME = "com.google.android.camera";
    private static final String TEST_CLASS_NAME =
            "com.android.camera.latency.CameraCaptureSessionTest";
    private static final String TEST_RUNNER_NAME = "android.test.InstrumentationTestRunner";
    private static final int MAX_TEST_TIMEOUT_MS = 60 * 60 * 1000; // 1 hours
    private static final String RU_KEY = "CameraAppLatency";

    @Option(name = "testMethod", shortName = 'm',
            description = "Test method to run. May be repeated.")
    private Collection<String> mTestMethods = new ArrayList<String>();

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
        InstrumentationTest instr = new InstrumentationTest();
        instr.setDevice(getDevice());
        instr.setPackageName(TEST_PACKAGE_NAME);
        instr.setRunnerName(TEST_RUNNER_NAME);
        instr.setClassName(TEST_CLASS_NAME);
        instr.setTestTimeout(MAX_TEST_TIMEOUT_MS);
        instr.setShellTimeout(MAX_TEST_TIMEOUT_MS);

        listener.testRunStarted(RU_KEY, 0);
        CollectingListener collectingListener = new CollectingListener(listener);

        // Run tests.
        if (mTestMethods.size() > 0) {
            CLog.d(String.format("The number of test methods is: %d", mTestMethods.size()));
            for (String testName : mTestMethods) {
                instr.setMethodName(testName);
                instr.run(collectingListener);
            }
        } else {
            instr.run(collectingListener);
        }

        // Report metrics at the end of test run.
        listener.testRunEnded(0, collectingListener.getFinalMetrics());
    }

    /**
     * A listener to collect the output from test run and fatal errors
     */
    private class CollectingListener extends StubTestInvocationListener {
        private ITestInvocationListener mListener;
        private Map<String, String> mFinalMetrics = new HashMap<String, String>();

        public CollectingListener(ITestInvocationListener listener) {
            mListener = listener;
        }

        @Override
        public void testStarted(TestIdentifier test) {
            super.testStarted(test);
            mListener.testStarted(test);
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            super.testFailed(test, trace);
            CLog.d("Test (%s) Failed to complete: %s", test.getTestName(), trace);
            mListener.testFailed(test, trace);
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            super.testEnded(test, testMetrics);
            // Test metrics accumulated will be posted at the end of test run.
            mFinalMetrics.putAll(parseResults(test.getTestName(), testMetrics));
            mListener.testEnded(test, Collections.<String, String>emptyMap());
        }

        public Map<String, String> parseResults(String testName, Map<String, String> testMetrics) {
            // Parse activity time stats from the instrumentation result.
            // Format : <metric_key>=<average_of_latency>|<raw_data>
            // Example:
            // FirstCaptureResultTimeMs=38|13 48 ... 35
            // SecondCaptureResultTimeMs=29.2|65 24 ... 0
            // CreateTimeMs=373.6|382 364 ... 323
            //
            // Then report only the first two startup time of cold startup and average warm startup.
            Map<String, String> parsed = new HashMap<String, String>();
            for (Map.Entry<String, String> metric : testMetrics.entrySet()) {
                Matcher matcher = STATS_REGEX.matcher(metric.getValue());
                if (matcher.matches()) {
                    String keyName = String.format("%s_%s", testName, metric.getKey());
                    parsed.put(keyName, matcher.group("latency"));
                } else {
                    CLog.w(String.format("Stats not in correct format: %s", metric.getValue()));
                }
            }
            return parsed;
        }

        public Map<String, String> getFinalMetrics() {
            return mFinalMetrics;
        }
    }
}
