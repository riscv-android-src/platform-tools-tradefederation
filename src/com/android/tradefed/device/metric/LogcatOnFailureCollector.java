/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.device.ILogcatReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.HashMap;
import java.util.Map;

/** Collector that will capture and log a logcat when a test case fails. */
public class LogcatOnFailureCollector extends BaseDeviceMetricCollector {

    private static final int MAX_LOGAT_SIZE_BYTES = 4 * 1024 * 1024;
    private Map<ITestDevice, ILogcatReceiver> mLogcatReceivers = new HashMap<>();
    private Map<ITestDevice, Integer> mOffset = new HashMap<>();

    @Override
    public ITestInvocationListener init(
            IInvocationContext context, ITestInvocationListener listener) {
        ITestInvocationListener init = super.init(context, listener);
        for (ITestDevice device : getDevices()) {
            ILogcatReceiver receiver = createLogcatReceiver(device);
            mLogcatReceivers.put(device, receiver);
            receiver.start();
        }
        getRunUtil().sleep(200);
        for (ITestDevice device : getDevices()) {
            mLogcatReceivers.get(device).clear();
        }
        return init;
    }

    @Override
    public void onTestRunStart(DeviceMetricData runData) {
        for (ITestDevice device : getDevices()) {
            // Get the current offset of the buffer to be able to query later
            mOffset.put(device, (int) mLogcatReceivers.get(device).getLogcatData().size());
        }
    }

    @Override
    public void onTestStart(DeviceMetricData testData) {
        // TODO: Handle the buffer to reset it at the test start
    }

    @Override
    public void onTestFail(DeviceMetricData testData, TestDescription test) {
        for (ITestDevice device : getDevices()) {
            // Delay slightly for the error to get in the logcat
            getRunUtil().sleep(100);
            try (InputStreamSource logcatSource =
                    mLogcatReceivers
                            .get(device)
                            .getLogcatData(MAX_LOGAT_SIZE_BYTES, mOffset.get(device))) {
                super.testLog(
                        String.format(
                                "logcat-on-failure-%s-%s#%s",
                                device.getSerialNumber(), test.getClassName(), test.getTestName()),
                        LogDataType.LOGCAT,
                        logcatSource);
            }
        }
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, Map<String, Metric> currentRunMetrics) {
        for (ILogcatReceiver receiver : mLogcatReceivers.values()) {
            receiver.stop();
            receiver.clear();
        }
        mLogcatReceivers.clear();
        mOffset.clear();
    }

    @VisibleForTesting
    ILogcatReceiver createLogcatReceiver(ITestDevice device) {
        return new LogcatReceiver(device, "logcat", device.getOptions().getMaxLogcatDataSize(), 0);
    }

    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
