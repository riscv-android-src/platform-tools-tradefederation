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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.cloud.RemoteAndroidVirtualDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.service.IRemoteFeature;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.util.SerializationUtil;

import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import java.io.IOException;

/**
 * Server side implementation of device reset.
 */
public class DeviceResetFeature implements IRemoteFeature, IConfigurationReceiver, ITestInformationReceiver {

    public static final String DEVICE_RESET_FEATURE_NAME = "resetDevice";
    public static final String DEVICE_NAME = "device_name";

    private IConfiguration mConfig;
    private TestInformation mTestInformation;

    @Override
    public String getName() {
        return DEVICE_RESET_FEATURE_NAME;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public void setTestInformation(TestInformation testInformation) {
        mTestInformation = testInformation;
    }

    @Override
    public TestInformation getTestInformation() {
        return mTestInformation;
    }

    @Override
    public FeatureResponse execute(FeatureRequest request) {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        String deviceName = request.getArgsMap().get(DEVICE_NAME);
        if (deviceName == null) {
            responseBuilder.setErrorInfo(
                    ErrorInfo.newBuilder().setErrorTrace("No device_name args specified."));
            return responseBuilder.build();
        }
        IDeviceConfiguration configHolder = mConfig.getDeviceConfigByName(deviceName);
        int index = 0;
        for (IDeviceConfiguration deviceConfig : mConfig.getDeviceConfig()) {
            if (deviceConfig == configHolder) {
                break;
            }
            index++;
        }
        try {
            mTestInformation.setActiveDeviceIndex(index);
            if (mTestInformation.getDevice() instanceof RemoteAndroidVirtualDevice) {
                boolean res = ((RemoteAndroidVirtualDevice) mTestInformation.getDevice()).powerwashGce();
                if (!res) {
                    throw new DeviceNotAvailableException(
                            String.format("Failed to powerwash device: %s",
                                    mTestInformation.getDevice().getSerialNumber()),
                            mTestInformation.getDevice().getSerialNumber(),
                            DeviceErrorIdentifier.DEVICE_FAILED_TO_RESET);
                }
            }
            for (ITargetPreparer labPreparer : configHolder.getLabPreparers()) {
                if (labPreparer.isDisabled()) {
                    continue;
                }
                labPreparer.setUp(mTestInformation);
            }
            for (ITargetPreparer preparer : configHolder.getTargetPreparers()) {
                if (preparer.isDisabled()) {
                    continue;
                }
                preparer.setUp(mTestInformation);
            }
        } catch (Exception e) {
            String error = "Failed to setup after reset device.";
            try {
                error = SerializationUtil.serializeToString(e);
            } catch (RuntimeException | IOException serializationError) {
                // Ignore
            }
            responseBuilder.setErrorInfo(ErrorInfo.newBuilder().setErrorTrace(error));
        } finally {
            mTestInformation.setActiveDeviceIndex(0);
        }
        return responseBuilder.build();
    }

}
