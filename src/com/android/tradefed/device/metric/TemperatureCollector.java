/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tradefed.targetprep.TemperatureThrottlingWaiter.DEVICE_TEMPERATURE_FILE_PATH_NAME;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ScheduledDeviceMetricCollector} to measure min and max device temperature. Useful for
 * long duration performance tests to monitor if the device overheats.
 */
public class TemperatureCollector extends ScheduledDeviceMetricCollector {

    // Option name intentionally shared with TemperatureThrottlingWaiter
    @Option(
        name = DEVICE_TEMPERATURE_FILE_PATH_NAME,
        description =
                "Name of file that contains device temperature. "
                        + "Example: /sys/class/hwmon/hwmon1/device/msm_therm"
    )
    private String mDeviceTemperatureFilePath = null;

    @Option(
        name = "device-temperature-file-regex",
        description =
                "Regex to parse temperature file. First group must be the temperature parsable"
                        + "to Double. Default: Result:(\\d+) Raw:.*"
    )
    private String mDeviceTemperatureFileRegex = "Result:(\\d+) Raw:.*";

    /**
     * Stores the highest recorded temperature per device. Device will not be present in the map if
     * no valid temperature was recorded.
     */
    private Map<ITestDevice, Double> mMaxDeviceTemps = new HashMap<>();

    /**
     * Stores the lowest recorded temperature per device. Device will not be present in the map if
     * no valid temperature was recorded.
     */
    private Map<ITestDevice, Double> mMinDeviceTemps = new HashMap<>();

    // Example: Result:32 Raw:7e51
    private static Pattern mTemperatureRegex;

    @Override
    void onStart(DeviceMetricData runData) {
        mTemperatureRegex = Pattern.compile(mDeviceTemperatureFileRegex);
    }

    @Override
    void collect(ITestDevice device, DeviceMetricData runData) throws InterruptedException {
        if (mDeviceTemperatureFilePath == null) {
            return;
        }
        try {
            if (!device.isAdbRoot()) {
                return;
            }
            Double temp = getTemperature(device);
            if (temp == null) {
                return;
            }
            mMaxDeviceTemps.putIfAbsent(device, temp);
            mMinDeviceTemps.putIfAbsent(device, temp);
            if (mMaxDeviceTemps.get(device) < temp) {
                mMaxDeviceTemps.put(device, temp);
            }
            if (mMinDeviceTemps.get(device) > temp) {
                mMinDeviceTemps.put(device, temp);
            }
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
        }
    }

    private Double getTemperature(ITestDevice device) throws DeviceNotAvailableException {
        String cmd = "cat " + mDeviceTemperatureFilePath;
        String result = device.executeShellCommand(cmd).trim();
        Matcher m = mTemperatureRegex.matcher(result);
        if (m.matches()) {
            return Double.parseDouble(m.group(1));
        }
        CLog.e("Error parsing temperature file output: " + result);
        return null;
    }

    @Override
    void onEnd(DeviceMetricData runData) {
        for (ITestDevice device : getDevices()) {
            Double maxtemp = mMaxDeviceTemps.get(device);
            if (maxtemp != null) {
                runData.addStringMetricForDevice(device, "maxtemp", Double.toString(maxtemp));
            }
            Double mintemp = mMinDeviceTemps.get(device);
            if (mintemp != null) {
                runData.addStringMetricForDevice(device, "mintemp", Double.toString(mintemp));
            }
        }
    }
}
