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

import com.android.ddmlib.NullOutputReceiver;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.ITestDevice;

import com.android.tradefed.log.LogUtil.CLog;

import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.LogDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.List;

/**
 * A {@link IMetricCollector} that runs atrace during a test and collects the result and log
 * them to the invocation.
 */
@OptionClass(alias = "atrace")
public class AtraceCollector extends BaseDeviceMetricCollector {

    @Option(name = "categories",
            description = "the tracing categories atrace will capture")
    private List<String> mCategories = new ArrayList<>();

    @Option(name = "log-path",
            description = "the temporary location the trace log will be saved to on device")
    private String mLogPath = "/data/local/tmp/";

    @Option(name = "log-filename",
            description = "the temporary location the trace log will be saved to on device")
    private String mLogFilename = "atrace";

    @Option(name = "preserve-ondevice-log",
            description = "delete the trace log on the target device after the host collects it")
    private boolean mPreserveOndeviceLog = false;

    @Option(name = "compress-dump",
            description = "produce a compressed trace dump")
    private boolean mCompressDump = true;

    private String fullLogPath() {
        String suffix = mCompressDump ? ".dat" : ".txt";
        return mLogPath + mLogFilename + suffix;
    }

    @Override
    public void onTestStart(DeviceMetricData testData) {

        if (mCategories.isEmpty()) {
            CLog.d("no categories specified to trace, not running AtraceMetricCollector");
            return;
        }

        for (ITestDevice device : getDevices()) {
            String cmd = "atrace --async_start ";
            if (mCompressDump) cmd += "-z ";
            cmd += String.join(" ", mCategories);
            CollectingOutputReceiver c = new CollectingOutputReceiver();
            CLog.i("issuing command : \"" + cmd + "\" to device: " + device.getSerialNumber());

            try {
                device.executeShellCommand(cmd, c, 1, TimeUnit.SECONDS, 1);
            } catch (DeviceNotAvailableException e) {
                CLog.e("Error starting atrace:");
                CLog.e(e);
            }
            CLog.i("command output: " + c.getOutput());
        }
    }

    @Override
    public void onTestEnd(
            DeviceMetricData testData, final Map<String, String> currentTestCaseMetrics) {

        if (mCategories.isEmpty())
            return;

        for (ITestDevice device : getDevices()) {
            try {
                CLog.i("collecting atrace log from device: " + device.getSerialNumber());
                device.executeShellCommand(
                        "atrace --async_stop -o " + fullLogPath(),
                        new NullOutputReceiver(),
                        60, TimeUnit.SECONDS, 1);

                File trace = device.pullFile(fullLogPath());
                if (trace != null) {
                    LogDataType type = mCompressDump ? LogDataType.ATRACE : LogDataType.TEXT;
                    try (FileInputStreamSource streamSource = new FileInputStreamSource(trace)) {
                        testLog(mLogFilename + device.getSerialNumber(), type, streamSource);
                    }
                    trace.delete();
                }
                else {
                    throw new DeviceRuntimeException("failed to pull log: " + fullLogPath());
                }

                if (!mPreserveOndeviceLog) {
                    device.executeShellCommand("rm -f " + fullLogPath());
                }
                else {
                    CLog.w("preserving ondevice atrace log: " + fullLogPath());
                }
            }
            catch (DeviceNotAvailableException | DeviceRuntimeException e) {
                CLog.e("Error retrieving atrace log! device not available:");
                CLog.e(e);
            }
        }
    }
}
