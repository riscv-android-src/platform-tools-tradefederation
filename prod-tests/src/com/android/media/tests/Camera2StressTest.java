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
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Camera2 stress test
 * Since Camera stress test can drain the battery seriously. Need to split
 * the test suite into separate test invocation for each test method.
 * <p/>
 */
@OptionClass(alias = "camera2-stress")
public class Camera2StressTest extends CameraTestBase {

    private static final String RESULT_FILE = "/sdcard/camera-out/stress.txt";
    private static final String METRIC_KEY = "iteration";
    private static final Pattern RESULT_REGEX = Pattern.compile(
            "^numShots=(\\d+)\\|iteration=(\\d+)");


    public Camera2StressTest() {
        setTestPackage("com.google.android.camera");
        setTestClass("com.android.camera.stress.CameraStressTest");
        setTestRunner("android.test.InstrumentationTestRunner");
        setRuKey("CameraAppStress");
        setTestTimeoutMs(6 * 60 * 60 * 1000);   // 6 hours
        setLogcatOnFailure(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        runInstrumentationTest(listener, new CollectingListener(listener));
    }

    /**
     * A listener to collect the output from test run and fatal errors
     */
    private class CollectingListener extends DefaultCollectingListener {

        public CollectingListener(ITestInvocationListener listener) {
            super(listener);
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            if (hasTestRunFatalError()) {
                CLog.v("The instrumentation result not found. Fall back to get the metrics from a "
                        + "log file. errorMsg: %s", getCollectingListener().getErrorMessage());
            }
            // For stress test, parse the metrics from a log file and overwrite the instrumentation
            // results passed.
            testMetrics = parseLog();
            super.testEnded(test, testMetrics);
        }

        private Map<String, String> parseLog() {
            Map<String, String> parsedMetrics = null;
            try {
                File outputFile = FileUtil.createTempFile("stress", ".txt");
                getDevice().pullFile(RESULT_FILE, outputFile);
                BufferedReader reader = new BufferedReader(new FileReader(outputFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = RESULT_REGEX.matcher(line);
                    if (matcher.matches()) {
                        CLog.v("numShots: %s", matcher.group(1));
                        CLog.v("iteration: %s", matcher.group(2));
                        parsedMetrics = new HashMap<String, String>();
                        parsedMetrics.put(METRIC_KEY, matcher.group(2));
                        break;
                    }
                }
            } catch (IOException e) {
                CLog.w("Couldn't parse the output log file: ", e);
            } catch (DeviceNotAvailableException e) {
                CLog.w("Could not pull file: %s, error: %s", RESULT_FILE, e);
            }
            return parsedMetrics;
        }
    }
}
