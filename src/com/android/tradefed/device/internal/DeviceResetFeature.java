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

import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.service.IRemoteFeature;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.util.SerializationUtil;

import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import java.io.IOException;

/**
 * Server side implementation of device reset.
 */
public class DeviceResetFeature implements IRemoteFeature, IConfigurationReceiver {

    public static final String DEVICE_RESET_FEATURE_NAME = "resetDevice";

    private IConfiguration mConfig;

    @Override
    public String getName() {
        return DEVICE_RESET_FEATURE_NAME;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public FeatureResponse execute(FeatureRequest request) {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        // TODO: Support multi-device
        String deviceName = ConfigurationDef.DEFAULT_DEVICE_NAME;
        IDeviceConfiguration configHolder = mConfig.getDeviceConfigByName(deviceName);
        try {
            // TODO: trigger reset if needed.
            for (ITargetPreparer preparer : configHolder.getTargetPreparers()) {
                // TODO: Actually get TestInformation
                preparer.setUp(null);
            }
        } catch (Exception e) {
            String error = "Failed to setup after reset device.";
            try {
                error = SerializationUtil.serializeToString(e);
            } catch (RuntimeException | IOException serializationError) {
                // Ignore
            }
            responseBuilder.setErrorInfo(ErrorInfo.newBuilder().setErrorTrace(error));
        }
        return responseBuilder.build();
    }

}
