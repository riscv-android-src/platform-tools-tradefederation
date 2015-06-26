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

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A harness that launches Audio Loopback tool and reports result.
 */
public class AudioLoopbackTest implements IDeviceTest, IRemoteTest {

    private static final String RUN_KEY = "AudioLoopback";
    private static final long TIMEOUT_MS = 5 * 60 * 1000; // 5 min
    private static final long DEVICE_SYNC_MS = 5 * 60 * 1000; // 5 min
    private static final long POLLING_INTERVAL_MS = 5 * 1000;
    private static final int MAX_ATTEMPTS = 3;
    private static final Map<String, String> METRICS_KEY_MAP = createMetricsKeyMap();

    private ITestDevice mDevice;

    @Option(name = "sampling-freq", description = "Sampling Frequency for Loopback app")
    private String mSamplingFreq = "48000";

    @Option(name = "mic-source", description = "Mic Source for Loopback app")
    private String mMicSource = "3";

    @Option(name = "audio-thread", description = "Audio Thread for Loopback app")
    private String mAudioThread = "1";

    @Option(name = "audio-level", description = "Audio Level for Loopback app")
    private String mAudioLevel = "12";

    @Option(name = "key-prefix", description = "Key Prefix for reporting")
    private String mKeyPrefix = "48000_Mic3_";

    private final String DEVICE_TEMP_DIR_PATH = "/sdcard/";
    private final String OUTPUT_FILENAME = "output_" + System.currentTimeMillis();
    private final String OUTPUT_TXT_PATH = DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + ".txt";
    private final String OUTPUT_PNG_PATH = DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + ".png";
    private final String OUTPUT_WAV_PATH = DEVICE_TEMP_DIR_PATH + OUTPUT_FILENAME + ".wav";

    private static Map<String, String> createMetricsKeyMap() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("LatencyMs", "latency_ms");
        result.put("LatencyConfidence", "latency_confidence");
        return Collections.unmodifiableMap(result);
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
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), RUN_KEY);
        ITestDevice device = getDevice();
        // Wait device to settle
        RunUtil.getDefault().sleep(DEVICE_SYNC_MS);

        listener.testRunStarted(RUN_KEY, 0);
        listener.testStarted(testId);

        long testStartTime = System.currentTimeMillis();
        Map<String, String> metrics = new HashMap<String, String>();
        String errMsg = null;

        // start measurement and wait for result file
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.unlockDevice();
        String loopbackCmd = String.format(
                "am start -n org.drrickorang.loopback/.LoopbackActivity" +
                " --ei SF %s --es FileName %s --ei MicSource %s --ei AudioThread %s" +
                " --ei AudioLevel %s", mSamplingFreq, OUTPUT_FILENAME, mMicSource,
                mAudioThread, mAudioLevel);
        CLog.i("Running cmd: " + loopbackCmd);
        device.executeShellCommand(loopbackCmd, receiver,
                TIMEOUT_MS, TimeUnit.MILLISECONDS, MAX_ATTEMPTS);
        long loopbackStartTime = System.currentTimeMillis();
        boolean isTimedOut = false;
        boolean isResultGenerated = false;
        while (!isResultGenerated && !isTimedOut) {
            RunUtil.getDefault().sleep(POLLING_INTERVAL_MS);
            isTimedOut = (System.currentTimeMillis() - loopbackStartTime >= TIMEOUT_MS);
            boolean isResultFileFound = device.doesFileExist(OUTPUT_TXT_PATH);
            if (isResultFileFound) {
                File loopbackReport = device.pullFile(OUTPUT_TXT_PATH);
                if (loopbackReport.length() > 0) {
                    isResultGenerated = true;
                }
            }
        }

        if (isTimedOut) {
            errMsg = "Loopback result not found, time out.";
        } else {
            // TODO: fail the test or rerun if the confidence level is too low
            // parse result
            CLog.i("== Loopback result ==");
            File loopbackReport = device.pullFile(OUTPUT_TXT_PATH);
            try {
                Map<String, String> loopbackResult = parseResult(loopbackReport);
                if (loopbackResult == null || loopbackResult.size() == 0) {
                    errMsg = "Failed to parse Loopback result.";
                } else {
                    metrics = loopbackResult;
                    listener.testLog(mKeyPrefix + "result", LogDataType.TEXT,
                            new SnapshotInputStreamSource(new FileInputStream(loopbackReport)));
                    File loopbackGraphFile = device.pullFile(OUTPUT_PNG_PATH);
                    listener.testLog(mKeyPrefix + "graph", LogDataType.PNG,
                            new SnapshotInputStreamSource(new FileInputStream(loopbackGraphFile)));
                    File loopbackWaveFile = device.pullFile(OUTPUT_WAV_PATH);
                    listener.testLog(mKeyPrefix + "wave", LogDataType.UNKNOWN,
                            new SnapshotInputStreamSource(new FileInputStream(loopbackWaveFile)));
                }
            } catch (IOException ioe) {
                CLog.e(ioe.getMessage());
                errMsg = "I/O error while parsing Loopback result.";
            }
        }

        if (errMsg != null) {
            CLog.e(errMsg);
            listener.testFailed(testId, errMsg);
            listener.testEnded(testId, metrics);
            listener.testRunFailed(errMsg);
        } else {
            long durationMs = System.currentTimeMillis() - testStartTime;
            listener.testEnded(testId, metrics);
            listener.testRunEnded(durationMs, metrics);
        }
    }

    /**
     * Parse result.
     *
     * @param result Loopback app result file
     * @return a {@link HashMap} that contains metrics keys and results
     * @throws IOException
     */
    private Map<String, String> parseResult(File result) throws IOException {
        Map<String, String> resultMap = new HashMap<String, String>();
        BufferedReader br = new BufferedReader(new FileReader(result));
        try {
            String line = br.readLine();
            while (line != null) {
                line = line.trim().replaceAll(" +", " ");
                String[] tokens = line.split("=");
                if (tokens.length >= 2) {
                    String metricName = tokens[0].trim();
                    String metricValue = tokens[1].trim();
                    if (METRICS_KEY_MAP.containsKey(metricName)) {
                        CLog.i(String.format("%s: %s", metricName, metricValue));
                        resultMap.put(mKeyPrefix + METRICS_KEY_MAP.get(metricName), metricValue);
                    }
                }
                line = br.readLine();
            }
        } finally {
            br.close();
        }
        return resultMap;
    }
}
