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

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import java.util.Map;

/**
 * Unit Tests for {@link DeviceSuite}
 */
public class DeviceSuiteTest {

    // We use HostTest as a runner for JUnit4 Suite
    private HostTest mHostTest;
    private ITestDevice mMockDevice;
    private ITestInvocationListener mListener;
    private IBuildInfo mMockBuildInfo;
    private IAbi mMockAbi;

    @Before
    public void setUp() {
        mHostTest = new HostTest();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockAbi = EasyMock.createMock(IAbi.class);
        mHostTest.setDevice(mMockDevice);
        mHostTest.setBuild(mMockBuildInfo);
        mHostTest.setAbi(mMockAbi);
    }

    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class Junit4DeviceTestclass implements IDeviceTest, IAbiReceiver,
            IBuildReceiver {
        public static ITestDevice sDevice;
        public static IBuildInfo sBuildInfo;
        public static IAbi sAbi;

        public Junit4DeviceTestclass() {
            sDevice = null;
            sBuildInfo = null;
            sAbi = null;
        }

        @Test
        public void testPass1() {}

        @Test
        public void testPass2() {}

        @Override
        public void setDevice(ITestDevice device) {
            sDevice = device;
        }

        @Override
        public ITestDevice getDevice() {
            return sDevice;
        }

        @Override
        public void setBuild(IBuildInfo buildInfo) {
            sBuildInfo = buildInfo;
        }

        @Override
        public void setAbi(IAbi abi) {
            sAbi = abi;
        }
    }

    @RunWith(DeviceSuite.class)
    @SuiteClasses({
        Junit4DeviceTestclass.class,
    })
    public class Junit4DeviceSuite {
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRunDeviceSuite() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4DeviceSuite.class.getName());
        mListener.testRunStarted(
                EasyMock.eq("com.android.tradefed.testtype.DeviceSuiteTest$Junit4DeviceSuite"),
                EasyMock.eq(2));
        TestIdentifier test1 = new TestIdentifier(Junit4DeviceTestclass.class.getName(),
                "testPass1");
        TestIdentifier test2 = new TestIdentifier(Junit4DeviceTestclass.class.getName(),
                "testPass2");
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener, mMockDevice);
        mHostTest.run(mListener);
        EasyMock.verify(mListener, mMockDevice);
        // Verify that all setters were called on Test class inside suite
        assertEquals(mMockDevice, Junit4DeviceTestclass.sDevice);
        assertEquals(mMockBuildInfo, Junit4DeviceTestclass.sBuildInfo);
        assertEquals(mMockAbi, Junit4DeviceTestclass.sAbi);
    }
}
