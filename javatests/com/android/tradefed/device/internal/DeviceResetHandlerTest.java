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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.service.TradefedFeatureClient;

import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link DeviceResetHandler}.
 */
@RunWith(JUnit4.class)
public class DeviceResetHandlerTest {

    private DeviceResetHandler mHandler;
    @Mock TradefedFeatureClient mMockClient;
    @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mHandler = new DeviceResetHandler(mMockClient);
    }

    @Test
    public void testReset() throws Exception {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        when(mMockClient.triggerFeature(any(), any())).thenReturn(responseBuilder.build());

        assertTrue(mHandler.resetDevice(mMockDevice));
    }

    @Test
    public void testReset_error() throws Exception {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        responseBuilder.setErrorInfo(ErrorInfo.newBuilder().setErrorTrace("random error"));
        when(mMockClient.triggerFeature(any(), any())).thenReturn(responseBuilder.build());

        assertFalse(mHandler.resetDevice(mMockDevice));
    }
}
