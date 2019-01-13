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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;

/** Collector that will capture and log a screenshot when a test case fails. */
public class ScreenshotOnFailureCollector extends BaseDeviceMetricCollector {

    @Override
    public void onTestFail(DeviceMetricData testData, TestDescription test) {
        for (ITestDevice device : getDevices()) {
            try (InputStreamSource screenSource = device.getScreenshot()) {
                super.testLog(
                        String.format("screenshot-%s_%s", test.getClassName(), test.getTestName()),
                        LogDataType.PNG,
                        screenSource);
            } catch (DeviceNotAvailableException e) {
                CLog.e(
                        "Device %s became unavailable while capturing screenshot, %s",
                        device.getSerialNumber(), e.toString());
            }
        }
    }
}
