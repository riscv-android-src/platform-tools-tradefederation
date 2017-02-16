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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;

/** Unit tests for {@link TestAppInstallSetup} */
public class TestAppInstallSetupTest extends TestCase {

    private static final String SERIAL = "SERIAL";
    private static final String PACKAGE_NAME = "PACKAGE_NAME";
    private TestAppInstallSetup mPrep;
    private IDeviceBuildInfo mMockBuildInfo;
    private ITestDevice mMockTestDevice;
    private File testDir;

    /** {@inheritDoc} */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        testDir = FileUtil.createTempDir("TestAppSetupTest");
        // fake hierarchy of directory and files
        final File fakeApk = FileUtil.createTempFile("fakeApk", ".apk", testDir);

        mPrep =
                new TestAppInstallSetup() {
                    @Override
                    protected String parsePackageName(
                            File testAppFile, DeviceDescriptor deviceDescriptor) {
                        return PACKAGE_NAME;
                    }

                    @Override
                    protected File getLocalPathForFilename(
                            IBuildInfo buildInfo, String apkFileName, ITestDevice device)
                            throws TargetSetupError {
                        return fakeApk;
                    }
                };
        mPrep.addTestFileName("fakeApk.apk");

        OptionSetter setter = new OptionSetter(mPrep);
        setter.setOptionValue("cleanup-apks", "true");
        mMockBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn(SERIAL);
        EasyMock.expect(mMockTestDevice.getDeviceDescriptor()).andStubReturn(null);
    }

    @Override
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(testDir);
    }

    public void testSetupAndTeardown() throws Exception {
        EasyMock.expect(
                        mMockTestDevice.installPackage(
                                (File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null);
        EasyMock.replay(mMockBuildInfo, mMockTestDevice);
        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockBuildInfo, mMockTestDevice);
    }

    public void testInstallFailure() throws Exception {
        final String failure = "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED";
        EasyMock.expect(
                        mMockTestDevice.installPackage(
                                (File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(failure);
        EasyMock.replay(mMockBuildInfo, mMockTestDevice);
        try {
            mPrep.setUp(mMockTestDevice, mMockBuildInfo);
            fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            String expected =
                    String.format(
                            "Failed to install %s on %s. Reason: '%s' " + "null",
                            "fakeApk.apk", SERIAL, failure);
            assertEquals(expected, e.getMessage());
        }
        EasyMock.verify(mMockBuildInfo, mMockTestDevice);
    }

    public void testInstallFailedUpdateIncompatible() throws Exception {
        final String failure = "INSTALL_FAILED_UPDATE_INCOMPATIBLE";
        EasyMock.expect(
                        mMockTestDevice.installPackage(
                                (File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(failure);
        EasyMock.expect(mMockTestDevice.uninstallPackage(PACKAGE_NAME)).andReturn(null);
        EasyMock.expect(
                        mMockTestDevice.installPackage(
                                (File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null);
        EasyMock.replay(mMockBuildInfo, mMockTestDevice);
        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        EasyMock.verify(mMockBuildInfo, mMockTestDevice);
    }
}
