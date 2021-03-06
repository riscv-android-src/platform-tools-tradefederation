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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.HashMap;

/** Unit tests for {@link ScreenshotOnFailureCollector}. */
@RunWith(JUnit4.class)
public class ScreenshotOnFailureCollectorTest {
    private ScreenshotOnFailureCollector mCollector;
    private ITestInvocationListener mMockListener;
    private ITestDevice mMockDevice;

    private ITestInvocationListener mTestListener;
    private IInvocationContext mContext;

    @Before
    public void setUp() {
        mMockDevice = Mockito.mock(ITestDevice.class);
        mMockListener = Mockito.mock(ITestInvocationListener.class);
        mCollector = new ScreenshotOnFailureCollector();
        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        when(mMockDevice.getIDevice()).thenReturn(Mockito.mock(IDevice.class));
        when(mMockDevice.getSerialNumber()).thenReturn("serial");
    }

    @Test
    public void testCollect() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        when(mMockDevice.getRecoveryMode()).thenReturn(RecoveryMode.AVAILABLE);
        when(mMockDevice.getScreenshot())
                .thenReturn(new ByteArrayInputStreamSource("".getBytes()));

        mTestListener = mCollector.init(mContext, mMockListener);
        mTestListener.testStarted(test);
        mTestListener.testFailed(test, "I failed");
        mTestListener.testEnded(test, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener).testFailed(Mockito.eq(test), (String) Mockito.any());
        verify(mMockListener).testEnded(
                Mockito.eq(test),
                Mockito.anyLong(),
                Mockito.<HashMap<String, Metric>>any());
        verify(mMockDevice).setRecoveryMode(RecoveryMode.NONE);
        verify(mMockListener).testLog(
                Mockito.eq("class#test-serial-screenshot-on-failure"),
                Mockito.eq(LogDataType.PNG),
                Mockito.any());
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
    }

    @Test
    public void testCollect_skipOffline() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);

        mTestListener = mCollector.init(mContext, mMockListener);
        mTestListener.testStarted(test);
        mTestListener.testFailed(test, "I failed");
        mTestListener.testEnded(test, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener).testFailed(Mockito.eq(test), (String) Mockito.any());
        verify(mMockListener).testEnded(
                Mockito.eq(test),
                Mockito.anyLong(),
                Mockito.<HashMap<String, Metric>>any());
    }
}
