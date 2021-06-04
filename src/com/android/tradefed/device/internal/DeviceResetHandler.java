/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tradefed.device.internal;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.service.TradefedFeatureClient;

import com.proto.tradefed.feature.FeatureResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility handling generically device resetting. This is meant to only be used internally to the
 * test harness. This shouldn't be called during a test.
 */
public class DeviceResetHandler {

    private final TradefedFeatureClient mClient;

    public DeviceResetHandler() {
        this(new TradefedFeatureClient());
    }

    @VisibleForTesting
    DeviceResetHandler(TradefedFeatureClient client) {
        mClient = client;
    }

    /**
     * Calls reset of the given device.
     *
     * @param device The device to reset.
     * @return True if reset was successful, false otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean resetDevice(ITestDevice device) throws DeviceNotAvailableException {
        FeatureResponse response;
        try {
            Map<String, String> args = new HashMap<>();
            // Reference the device to be reset by its serial which should be unique
            args.put("serial", device.getSerialNumber());
            response = mClient.triggerFeature(DeviceResetFeature.DEVICE_RESET_FEATURE_NAME, args);
        } finally {
            mClient.close();
        }
        if (response.hasErrorInfo()) {
            // TODO: Handle DNAE
            CLog.e("Reset failed: %s", response.getErrorInfo().getErrorTrace());
            return false;
        }
        return true;
    }
}
