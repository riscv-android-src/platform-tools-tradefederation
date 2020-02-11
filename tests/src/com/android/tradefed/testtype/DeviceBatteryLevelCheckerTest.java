/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeviceBatteryLevelChecker}. */
@RunWith(JUnit4.class)
public class DeviceBatteryLevelCheckerTest {
    private DeviceBatteryLevelChecker mChecker = null;
    private ITestDevice mDevice = null;
    private TestInformation mTestInfo = null;
    private ITestDevice mFakeTestDevice = null;
    public Integer mBatteryLevel = 10;

    private IDevice mMockIDevice;
    private IDeviceStateMonitor mMockStateMonitor;
    private IDeviceMonitor mMockDvcMonitor;

    /** A {@link TestDevice} that is suitable for running tests against */
    private class TestableTestDevice extends TestDevice {
        public TestableTestDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
        }

        @Override
        public String getSerialNumber() {
            return mFakeTestDevice.getSerialNumber();
        }

        @Override
        public void stopLogcat() {
            mFakeTestDevice.stopLogcat();
        }

        @Override
        public String executeShellCommand(String command) throws DeviceNotAvailableException {
            return mFakeTestDevice.executeShellCommand(command);
        }

        @Override
        public Integer getBattery() {
            return mBatteryLevel;
        }
    }

    @Before
    public void setUp() throws Exception {
        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockStateMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockDvcMonitor = EasyMock.createMock(IDeviceMonitor.class);
        mFakeTestDevice = EasyMock.createMock(ITestDevice.class);

        mDevice = new TestableTestDevice();
        mChecker = new DeviceBatteryLevelChecker() {
            @Override
            IRunUtil getRunUtil() {
                return EasyMock.createNiceMock(IRunUtil.class);
            }
        };
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        EasyMock.expect(mFakeTestDevice.getSerialNumber()).andStubReturn("SERIAL");
    }

    @Test
    public void testNull() throws Exception {
        expectBattLevel(null);
        replayDevices();

        mChecker.run(mTestInfo, null);
        // expect this to return immediately without throwing an exception.  Should log a warning.
        verifyDevices();
    }

    @Test
    public void testNormal() throws Exception {
        expectBattLevel(45);
        replayDevices();

        mChecker.run(mTestInfo, null);
        verifyDevices();
    }

    /** Low battery with a resume level very low to check a resume if some level are reached. */
    @Test
    public void testLow() throws Exception {
        mFakeTestDevice.stopLogcat();
        EasyMock.expectLastCall();
        expectBattLevel(5);
        EasyMock.expect(mFakeTestDevice.executeShellCommand("svc power stayon false"))
                .andStubReturn("");
        EasyMock.expect(mFakeTestDevice.executeShellCommand(
                "settings put system screen_off_timeout 1000")).andStubReturn("");
        replayDevices();
        mChecker.setResumeLevel(5);
        mChecker.run(mTestInfo, null);
        verifyDevices();
    }

    /** Battery is low, device idles and battery gets high again. */
    @Test
    public void testLow_becomeHigh() throws Exception {
        mFakeTestDevice.stopLogcat();
        EasyMock.expectLastCall();
        expectBattLevel(5);
        EasyMock.expect(mFakeTestDevice.executeShellCommand("svc power stayon false"))
                .andStubReturn("");
        EasyMock.expect(mFakeTestDevice.executeShellCommand(
                "settings put system screen_off_timeout 1000")).andStubReturn("");
        replayDevices();
        Thread raise = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    expectBattLevel(85);
                } catch (Exception e) {
                    CLog.e(e);
                }
            }
        });
        raise.start();
        mChecker.run(mTestInfo, null);
        verifyDevices();
    }

    /** Battery is low, device idles and battery gets null, break the loop. */
    @Test
    public void testLow_becomeNull() throws Exception {
        mFakeTestDevice.stopLogcat();
        EasyMock.expectLastCall();
        expectBattLevel(5);
        EasyMock.expect(mFakeTestDevice.executeShellCommand("svc power stayon false"))
                .andStubReturn("");
        EasyMock.expect(mFakeTestDevice.executeShellCommand(
                "settings put system screen_off_timeout 1000")).andStubReturn("");
        replayDevices();
        Thread raise = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10);
                    expectBattLevel(null);
                } catch (Exception e) {
                    CLog.e(e);
                }
            }
        });
        raise.start();
        mChecker.run(mTestInfo, null);
        verifyDevices();
    }

    private void expectBattLevel(Integer level) throws Exception {
        mBatteryLevel = level;
    }

    private void replayDevices() {
        EasyMock.replay(mFakeTestDevice);
    }

    private void verifyDevices() {
        EasyMock.verify(mFakeTestDevice);
    }
}

