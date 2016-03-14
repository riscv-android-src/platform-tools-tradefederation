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
package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;

/**
 * Unit tests for {@link AndroidNativeDevice}.
 */
public class AndroidNativeDeviceTest extends TestCase {

    private static final String MOCK_DEVICE_SERIAL = "serial";
    private IDevice mMockIDevice;
    private TestableAndroidNativeDevice mTestDevice;
    private IDeviceRecovery mMockRecovery;
    private IDeviceStateMonitor mMockStateMonitor;
    private IRunUtil mMockRunUtil;
    private IWifiHelper mMockWifi;
    private IDeviceMonitor mMockDvcMonitor;

    /**
     * A {@link TestDevice} that is suitable for running tests against
     */
    private class TestableAndroidNativeDevice extends AndroidNativeDevice {
        public TestableAndroidNativeDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
        }

        @Override
        public void postBootSetup() {
            // too annoying to mock out postBootSetup actions everyone, so do nothing
        }

        @Override
        protected IRunUtil getRunUtil() {
            return mMockRunUtil;
        }

        @Override
        void doReboot() throws DeviceNotAvailableException, UnsupportedOperationException {
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockIDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockIDevice.getSerialNumber()).andReturn(MOCK_DEVICE_SERIAL).anyTimes();
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mMockStateMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockDvcMonitor = EasyMock.createMock(IDeviceMonitor.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockWifi = EasyMock.createMock(IWifiHelper.class);

        // A TestDevice with a no-op recoverDevice() implementation
        mTestDevice = new TestableAndroidNativeDevice() {
            @Override
            public void recoverDevice() throws DeviceNotAvailableException {
                // ignore
            }

            @Override
            public IDevice getIDevice() {
                return mMockIDevice;
            }

            @Override
            IWifiHelper createWifiHelper() {
                return mMockWifi;
            }
        };
        mTestDevice.setRecovery(mMockRecovery);
        mTestDevice.setCommandTimeout(100);
        mTestDevice.setLogStartDelay(-1);
    }

    /**
     * Test return exception for package installation
     * {@link AndroidNativeDevice#installPackage(File, boolean, String...)}.
     */
    public void testInstallPackages_exception() {
        try {
            mTestDevice.installPackage(new File(""), false);
        } catch (UnsupportedOperationException onse) {
            return;
        } catch (DeviceNotAvailableException e) {
            fail("installPackage should have thrown an Unsupported exception, not dnae");
        }
        fail("installPackage should have thrown an exception");
    }

    /**
     * Test return exception for package installation
     * {@link AndroidNativeDevice#uninstallPackage(String)}.
     */
    public void testUninstallPackages_exception() {
        try {
            mTestDevice.uninstallPackage("");
        } catch (UnsupportedOperationException onse) {
            return;
        } catch (DeviceNotAvailableException e) {
            fail("uninstallPackage should have thrown an Unsupported exception, not dnae");
        }
        fail("uninstallPackageForUser should have thrown an exception");
    }

    /**
     * Test return exception for package installation
     * {@link AndroidNativeDevice#installPackage(File, boolean, boolean, String...)}.
     */
    public void testInstallPackagesBool_exception() {
        try {
            mTestDevice.installPackage(new File(""), false, false);
        } catch (UnsupportedOperationException onse) {
            return;
        } catch (DeviceNotAvailableException e) {
            fail("installPackage should have thrown an Unsupported exception, not dnae");
        }
        fail("installPackage should have thrown an exception");
    }

    /**
     * Test return exception for package installation
     * {@link AndroidNativeDevice#installPackageForUser(File, boolean, int, String...)}.
     */
    public void testInstallPackagesForUser_exception() {
        try {
            mTestDevice.installPackageForUser(new File(""), false, 0);
        } catch (UnsupportedOperationException onse) {
            return;
        } catch (DeviceNotAvailableException e) {
            fail("installPackageForUser should have thrown an Unsupported exception, not dnae");
        }
        fail("installPackageForUser should have thrown an exception");
    }

    /**
     * Test return exception for package installation
     * {@link AndroidNativeDevice#installPackageForUser(File, boolean, boolean, int, String...)}.
     */
    public void testInstallPackagesForUserWithPermission_exception() {
        try {
            mTestDevice.installPackageForUser(new File(""), false, false, 0);
        } catch (UnsupportedOperationException onse) {
            return;
        } catch (DeviceNotAvailableException e) {
            fail("installPackageForUser should have thrown an Unsupported exception, not dnae");
        }
        fail("installPackageForUser should have thrown an exception");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#getInstalledPackageNames()}.
     */
    public void testGetInstalledPackageNames_exception() throws Exception {
        try {
            mTestDevice.getInstalledPackageNames();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getInstalledPackageNames should have thrown an exception");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#getScreenshot()}.
     */
    public void testGetScreenshot_exception() throws Exception {
        try {
            mTestDevice.getScreenshot();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getScreenshot should have thrown an exception");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#pushDir(File, String)}.
     */
    public void testPushDir_notADir() throws Exception {
        assertFalse(mTestDevice.pushDir(new File(""), ""));
    }

    /**
     * Unit test for {@link AndroidNativeDevice#pushDir(File, String)}.
     */
    public void testPushDir_childFile() throws Exception {
        mTestDevice = new TestableAndroidNativeDevice() {
            @Override
            public boolean pushFile(File localFile, String remoteFilePath)
                    throws DeviceNotAvailableException {
                return true;
            }
        };
        File testDir = FileUtil.createTempDir("pushDirTest");
        FileUtil.createTempFile("test1", ".txt", testDir);
        assertTrue(mTestDevice.pushDir(testDir, ""));
        FileUtil.recursiveDelete(testDir);
    }

    /**
     * Unit test for {@link AndroidNativeDevice#pushDir(File, String)}.
     */
    public void testPushDir_childDir() throws Exception {
        mTestDevice = new TestableAndroidNativeDevice() {
            @Override
            public String executeShellCommand(String cmd) throws DeviceNotAvailableException {
                return "";
            }
            @Override
            public boolean pushFile(File localFile, String remoteFilePath)
                    throws DeviceNotAvailableException {
                return false;
            }
        };
        File testDir = FileUtil.createTempDir("pushDirTest");
        File subDir = FileUtil.createTempDir("testSubDir", testDir);
        FileUtil.createTempDir("test1", subDir);
        assertTrue(mTestDevice.pushDir(testDir, ""));
        FileUtil.recursiveDelete(testDir);
    }

    /**
     * Unit test for {@link AndroidNativeDevice#getCurrentUser()}.
     */
    public void testGetCurrentUser_exception() throws Exception {
        try {
            mTestDevice.getScreenshot();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getCurrentUser should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#getUserFlags(int)}.
     */
    public void testGetUserFlags_exception() throws Exception {
        try {
            mTestDevice.getUserFlags(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getUserFlags should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#getUserSerialNumber(int)}.
     */
    public void testGetUserSerialNumber_exception() throws Exception {
        try {
            mTestDevice.getUserSerialNumber(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getUserSerialNumber should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#switchUser(int)}.
     */
    public void testSwitchUser_exception() throws Exception {
        try {
            mTestDevice.switchUser(10);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("switchUser should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#switchUser(int, long)}.
     */
    public void testSwitchUserTimeout_exception() throws Exception {
        try {
            mTestDevice.switchUser(10, 5*1000);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("switchUser should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#stopUser(int)}.
     */
    public void testStopUser_exception() throws Exception {
        try {
            mTestDevice.stopUser(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("stopUser should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#stopUser(int, boolean, boolean)}.
     */
    public void testStopUserFlags_exception() throws Exception {
        try {
            mTestDevice.stopUser(0, true, true);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("stopUser should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#isUserRunning(int)}.
     */
    public void testIsUserIdRunning_exception() throws Exception {
        try {
            mTestDevice.isUserRunning(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("stopUser should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#hasFeature(String)}.
     */
    public void testHasFeature_exception() throws Exception {
        try {
            mTestDevice.hasFeature("feature:test");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("hasFeature should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#getSetting(int, String, String)}.
     */
    public void testGetSetting_exception() throws Exception {
        try {
            mTestDevice.getSetting(0, "global", "wifi_on");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getSettings should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#setSetting(int, String, String, String)}.
     */
    public void testSetSetting_exception() throws Exception {
        try {
            mTestDevice.setSetting(0, "global", "wifi_on", "0");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("putSettings should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#getAndroidId(int)}.
     */
    public void testGetAndroidId_exception() throws Exception {
        try {
            mTestDevice.getAndroidId(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getAndroidId should have thrown an exception.");
    }

    /**
     * Unit test for {@link AndroidNativeDevice#getAndroidIds()}.
     */
    public void testGetAndroidIds_exception() throws Exception {
        try {
            mTestDevice.getAndroidIds();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getAndroidIds should have thrown an exception.");
    }
}
