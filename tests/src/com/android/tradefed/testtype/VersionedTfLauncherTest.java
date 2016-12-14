/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.testtype;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NullDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link VersionedTfLauncher}
 */
public class VersionedTfLauncherTest {

    private static final String FAKE_SERIAL = "FAKE_SERIAL";
    private static final String CONFIG_NAME = "FAKE_CONFIG";

    private VersionedTfLauncher mVersionedTfLauncher;
    private ITestInvocationListener mMockListener;
    private IRunUtil mMockRunUtil;
    private ITestDevice mMockTestDevice;
    private IDevice mMockIDevice;
    private IFolderBuildInfo mMockBuildInfo;

    @Before
    public void setUp() throws Exception {
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockBuildInfo = EasyMock.createMock(IFolderBuildInfo.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);

        mVersionedTfLauncher = new VersionedTfLauncher();
        mVersionedTfLauncher.setRunUtil(mMockRunUtil);
        mVersionedTfLauncher.setConfigName(CONFIG_NAME);
        mVersionedTfLauncher.setBuild(mMockBuildInfo);
        mVersionedTfLauncher.setEventStreaming(false);
    }

    /**
     * Test {@link VersionedTfLauncher#run(ITestInvocationListener)} for test with a single device
     */
    @Test
    public void testRun_singleDevice() {
        mMockIDevice = EasyMock.createMock(IDevice.class);

        CommandResult cr = new CommandResult(CommandStatus.SUCCESS);
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(),
                (FileOutputStream)EasyMock.anyObject(), (FileOutputStream)EasyMock.anyObject(),
                EasyMock.eq("java"), (String)EasyMock.anyObject(), EasyMock.eq("-cp"),
                (String)EasyMock.anyObject(),
                EasyMock.eq("com.android.tradefed.command.CommandRunner"),
                EasyMock.eq(CONFIG_NAME), EasyMock.eq("--serial"), EasyMock.eq(FAKE_SERIAL),
                EasyMock.eq("--subprocess-report-file"),
                (String)EasyMock.anyObject())).andReturn(cr);
        Map<ITestDevice, IBuildInfo> deviceInfos = new HashMap<ITestDevice, IBuildInfo>();
        deviceInfos.put(mMockTestDevice, null);
        mVersionedTfLauncher.setDeviceInfos(deviceInfos);
        EasyMock.expect(mMockBuildInfo.getRootDir()).andReturn(new File(""));
        EasyMock.expect(mMockBuildInfo.getBuildId()).andReturn("FAKEID").times(2);
        EasyMock.expect(mMockTestDevice.getIDevice()).andReturn(mMockIDevice).times(1);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn(FAKE_SERIAL).times(1);
        mMockListener.testLog((String)EasyMock.anyObject(), (LogDataType)EasyMock.anyObject(),
                (FileInputStreamSource)EasyMock.anyObject());
        EasyMock.expectLastCall().times(3);
        mMockListener.testRunStarted("StdErr", 1);
        mMockListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mMockListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(Collections.<String, String>emptyMap()));
        mMockListener.testRunEnded(0, Collections.emptyMap());

        EasyMock.replay(mMockTestDevice, mMockBuildInfo, mMockRunUtil, mMockListener);
        mVersionedTfLauncher.run(mMockListener);
        EasyMock.verify(mMockTestDevice, mMockBuildInfo, mMockRunUtil, mMockListener);
    }

    /**
     * Test {@link VersionedTfLauncher#run(ITestInvocationListener)} for test with a null device
     */
    @Test
    public void testRun_nullDevice() {
        mMockIDevice = new NullDevice("null-device-1");

        CommandResult cr = new CommandResult(CommandStatus.SUCCESS);
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(),
                (FileOutputStream)EasyMock.anyObject(), (FileOutputStream)EasyMock.anyObject(),
                EasyMock.eq("java"), (String)EasyMock.anyObject(), EasyMock.eq("-cp"),
                (String)EasyMock.anyObject(),
                EasyMock.eq("com.android.tradefed.command.CommandRunner"),
                EasyMock.eq(CONFIG_NAME), EasyMock.eq("--null-device"),
                EasyMock.eq("--subprocess-report-file"),
                (String)EasyMock.anyObject())).andReturn(cr);
        Map<ITestDevice, IBuildInfo> deviceInfos = new HashMap<ITestDevice, IBuildInfo>();
        deviceInfos.put(mMockTestDevice, null);
        mVersionedTfLauncher.setDeviceInfos(deviceInfos);
        EasyMock.expect(mMockBuildInfo.getRootDir()).andReturn(new File(""));
        EasyMock.expect(mMockBuildInfo.getBuildId()).andReturn("FAKEID").times(2);
        EasyMock.expect(mMockTestDevice.getIDevice()).andReturn(mMockIDevice).times(1);
        mMockListener.testLog((String)EasyMock.anyObject(), (LogDataType)EasyMock.anyObject(),
                (FileInputStreamSource)EasyMock.anyObject());
        EasyMock.expectLastCall().times(3);
        mMockListener.testRunStarted("StdErr", 1);
        mMockListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mMockListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(Collections.<String, String>emptyMap()));
        mMockListener.testRunEnded(0, Collections.emptyMap());

        EasyMock.replay(mMockTestDevice, mMockBuildInfo, mMockRunUtil, mMockListener);
        mVersionedTfLauncher.run(mMockListener);
        EasyMock.verify(mMockTestDevice, mMockBuildInfo, mMockRunUtil, mMockListener);
    }
}
