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

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FailureDescription;

/** Collect a bugreportz when a test case fails. */
public class BugreportzOnFailureCollector extends BaseDeviceMetricCollector {

    private static final String NAME_FORMAT = "%s-%s-bugreportz-on-failure";

    @Override
    public void onTestRunFailed(DeviceMetricData testData, FailureDescription failure) {
        for (ITestDevice device : getDevices()) {
            String name = String.format(NAME_FORMAT, getRunName(), device.getSerialNumber());
            if (!device.logBugreport(name, getInvocationListener())) {
                CLog.e("Failed to capture bugreportz on '%s'", device.getSerialNumber());
            }
        }
    }
}
