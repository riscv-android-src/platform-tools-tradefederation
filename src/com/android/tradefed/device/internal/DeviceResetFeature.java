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

import com.android.tradefed.service.IRemoteFeature;

import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

/**
 * Server side implementation of device reset.
 */
public class DeviceResetFeature implements IRemoteFeature {

    public static final String DEVICE_RESET_FEATURE_NAME = "resetDevice";

    @Override
    public String getName() {
        return DEVICE_RESET_FEATURE_NAME;
    }

    @Override
    public FeatureResponse execute(FeatureRequest request) {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        responseBuilder.setErrorInfo(ErrorInfo.newBuilder().setErrorTrace(
                "Feature not implemented yet."));
        // TODO: Implementation
        return responseBuilder.build();
    }

}
