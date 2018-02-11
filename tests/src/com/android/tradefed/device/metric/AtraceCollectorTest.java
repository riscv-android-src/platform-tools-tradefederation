/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link AtraceCollector},
 */
@RunWith(JUnit4.class)
public final class AtraceCollectorTest {
    private ITestDevice mMockDevice;
    private AtraceCollector mAtrace;
    private OptionSetter mOptionSetter;
    private ITestInvocationListener mMockTestLogger;
    private IInvocationContext mMockInvocationContext;
    private String mDefaultLogPath = "/data/local/tmp/atrace.dat";
    private String mSerialNo = "12349876";
    private String mCategories = "tisket tasket brisket basket";

    @Before
    public void setUp() throws Exception {
        mMockDevice = EasyMock.createNiceMock(ITestDevice.class);
        mMockTestLogger = EasyMock.createMock(ITestInvocationListener.class);
        mMockInvocationContext = EasyMock.createNiceMock(IInvocationContext.class);

        mAtrace = new AtraceCollector();
        mOptionSetter = new OptionSetter(mAtrace);
        mOptionSetter.setOptionValue("categories", mCategories);

        EasyMock.expect(mMockInvocationContext.getDevices())
                .andStubReturn(Arrays.asList(mMockDevice));
        EasyMock.replay(mMockInvocationContext);
        mAtrace.init(mMockInvocationContext, mMockTestLogger);
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started correctly.
     *
     * <p>
     * Expect that atrace was started in async mode with compression on.
     * </p>
     */
    @Test
    public void testStartsAtraceOnSetupNoOptions() throws Exception {
        mMockDevice.executeShellCommand(
                EasyMock.eq("atrace --async_start -z " + mCategories),
                EasyMock.anyObject(), EasyMock.eq(1L), EasyMock.anyObject(), EasyMock.eq(1));
        EasyMock.expectLastCall().times(1);

        EasyMock.replay(mMockDevice);

        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started correctly when the compress-dump option is false.
     *
     * <p>
     * Expect that atrace was started in async mode with compression off.
     * </p>
     */
    @Test
    public void testStartsAtraceOnSetupNoCompression() throws Exception {
        mMockDevice.executeShellCommand(
                EasyMock.eq("atrace --async_start " + mCategories),
                EasyMock.anyObject(), EasyMock.eq(1L), EasyMock.anyObject(), EasyMock.eq(1));
        EasyMock.expectLastCall().times(1);

        EasyMock.replay(mMockDevice);

        mOptionSetter.setOptionValue("compress-dump", "false");
        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started correctly with some tracing categories.
     *
     * <p>
     * Expect that supplied categories options were included in the command
     * when starting atrace.
     * </p>
     */
    @Test
    public void testStartsAtraceOnSetupCategoriesOption() throws Exception {
        mMockDevice.executeShellCommand(
                EasyMock.eq("atrace --async_start -z " + mCategories),
                EasyMock.anyObject(), EasyMock.eq(1L), EasyMock.anyObject(), EasyMock.eq(1));
        EasyMock.expectLastCall().times(1);

        EasyMock.replay(mMockDevice);

        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started correctly with multiple tracing categories.
     *
     * <p>
     * Expect that supplied categories options were included in the command
     * when starting atrace.
     * </p>
     */
    @Test
    public void testStartsAtraceOnSetupMultipleCategoriesOption() throws Exception {
        String freqCategory = "freq";
        String schedCategory = "sched";
        String expectedCategories = mCategories + " " + freqCategory + " " + schedCategory;
        mMockDevice.executeShellCommand(
                EasyMock.eq("atrace --async_start -z " + expectedCategories),
                EasyMock.anyObject(), EasyMock.eq(1L), EasyMock.anyObject(), EasyMock.eq(1));
        EasyMock.expectLastCall().times(1);

        EasyMock.replay(mMockDevice);

        mOptionSetter.setOptionValue("categories", freqCategory);
        mOptionSetter.setOptionValue("categories", schedCategory);
        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started with no tracing categories does not do anything.
     *
     * <p>
     * Expect that no commands are issued to the device when no categories are set
     * </p>
     */
    @Test
    public void testStartsAtraceWithNoCategoriesOption() throws Exception {
        mMockDevice.executeShellCommand(
                (String) EasyMock.anyObject(),
                EasyMock.anyObject(), EasyMock.anyLong(), EasyMock.anyObject(), EasyMock.anyInt());
        EasyMock.expectLastCall()
                .andThrow(new Error("should not be called"))
                .anyTimes();
        EasyMock.replay(mMockDevice);

        AtraceCollector atrace = new AtraceCollector();
        atrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        atrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, String>());
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see if atrace collection
     * stopped correctly.
     *
     * <p>Expect that atrace command was stopped, the trace file was pulled from device to host and
     * the trace file removed from device.
     */
    @Test
    public void testStopsAtraceDuringTearDown() throws Exception {
        mMockDevice.executeShellCommand(
                EasyMock.eq("atrace --async_stop -o " + mDefaultLogPath),
                EasyMock.anyObject(), EasyMock.eq(60L), EasyMock.anyObject(), EasyMock.eq(1));
        EasyMock.expectLastCall().times(1);
        EasyMock.expect(mMockDevice.pullFile(EasyMock.eq(mDefaultLogPath)))
                .andReturn(new File("/tmp/potato")).once();
        EasyMock.expect(mMockDevice.executeShellCommand(EasyMock.eq("rm -f " + mDefaultLogPath)))
                .andReturn("").times(1);

        EasyMock.replay(mMockDevice);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, String>());
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see if atrace collection
     * stopped correctly when preserve-ondevice-log is set.
     *
     * <p>Expect that atrace command was stopped, the trace file was pulled from device to host and
     * the trace file was not removed from the device.
     */
    @Test
    public void testPreserveFileOnDeviceOption() throws Exception {
        mMockDevice.executeShellCommand(
                EasyMock.eq("atrace --async_stop -o " + mDefaultLogPath),
                EasyMock.anyObject(), EasyMock.eq(60L), EasyMock.anyObject(), EasyMock.eq(1));
        EasyMock.expectLastCall().times(1);
        EasyMock.expect(mMockDevice.pullFile(EasyMock.eq(mDefaultLogPath)))
                .andReturn(new File("/tmp/potato")).once();

        EasyMock.replay(mMockDevice);
        mOptionSetter.setOptionValue("preserve-ondevice-log", "true");
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, String>());
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it throws an
     * exception if the atrace file could not be collected.
     *
     * <p>Expect that DeviceNotAvailableException is thrown when the file returned is null.
     */
    @Test
    public void testLogPullFail() throws Exception {
        EasyMock.expect(mMockDevice.pullFile((String) EasyMock.anyObject()))
                .andReturn(null).once();
        EasyMock.replay(mMockDevice);

        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, String>());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it uploads its file
     * correctly with compression on.
     *
     * <p>Expect that testLog is called with the proper filename and LogDataType.
     */
    @Test
    public void testUploadsLogWithCompression() throws Exception {
        EasyMock.expect(mMockDevice.pullFile((String) EasyMock.anyObject()))
                .andStubReturn(new File("/tmp/potato"));
        EasyMock.expect(mMockDevice.getSerialNumber())
                .andStubReturn(mSerialNo);
        mMockTestLogger.testLog(
                EasyMock.eq("atrace" + mSerialNo),
                EasyMock.eq(LogDataType.ATRACE),
                EasyMock.anyObject());
        EasyMock.expectLastCall().times(1);
        EasyMock.replay(mMockDevice, mMockTestLogger);

        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, String>());

        EasyMock.verify(mMockTestLogger);
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it uploads its file
     * correctly with compression off.
     *
     * <p>Expect that testLog is called with the proper filename and LogDataType.
     */
    @Test
    public void testUploadslogWithoutCompression() throws Exception {
        EasyMock.expect(mMockDevice.pullFile((String) EasyMock.anyObject()))
                .andStubReturn(new File("/tmp/potato"));
        EasyMock.expect(mMockDevice.getSerialNumber())
                .andStubReturn(mSerialNo);
        mMockTestLogger.testLog(
                EasyMock.eq("atrace" + mSerialNo),
                EasyMock.eq(LogDataType.TEXT),
                EasyMock.anyObject());
        EasyMock.expectLastCall().times(1);
        EasyMock.replay(mMockDevice, mMockTestLogger);

        mOptionSetter.setOptionValue("compress-dump", "false");
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, String>());

        EasyMock.verify(mMockTestLogger);
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that each device uploads
     * a log
     *
     * <p>Expect that testLog is called for each device.
     */
    @Test
    public void testMultipleDeviceBehavior() throws Exception {
        int num_devices = 3;
        List<ITestDevice> devices = new ArrayList<ITestDevice>();
        for (int i = 0; i < num_devices; i++) {
            ITestDevice device = EasyMock.createNiceMock(ITestDevice.class);
            EasyMock.expect(device.getSerialNumber())
                    .andStubReturn(mSerialNo);
            EasyMock.expect(device.pullFile((String) EasyMock.anyObject()))
                    .andStubReturn(new File("/tmp/potato"));
            EasyMock.replay(device);
            devices.add(device);
        }
        IInvocationContext mockInvocationContext =
            EasyMock.createNiceMock(IInvocationContext.class);
        EasyMock.expect(mockInvocationContext.getDevices())
                .andStubReturn(devices);

        mMockTestLogger.testLog(
            (String) EasyMock.anyObject(),
            EasyMock.eq(LogDataType.ATRACE),
            EasyMock.anyObject());
        EasyMock.expectLastCall().times(num_devices);
        EasyMock.replay(mMockTestLogger, mockInvocationContext);

        AtraceCollector atrace = new AtraceCollector();
        OptionSetter optionSetter = new OptionSetter(atrace);
        optionSetter.setOptionValue("categories", mCategories);
        atrace.init(mockInvocationContext, mMockTestLogger);
        atrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, String>());

        EasyMock.verify(mMockTestLogger);
    }
}
