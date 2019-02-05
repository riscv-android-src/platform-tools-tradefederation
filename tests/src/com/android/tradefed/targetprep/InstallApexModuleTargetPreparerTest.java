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
package com.android.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Unit test for {@link InstallApexModuleTargetPreparer} */
@RunWith(JUnit4.class)
public class InstallApexModuleTargetPreparerTest {

    private static final String SERIAL = "serial";
    private InstallApexModuleTargetPreparer mInstallApexModuleTargetPreparer;
    private IBuildInfo mMockBuildInfo;
    private ITestDevice mMockDevice;
    private File mFakeApex;
    private static final String APEX_PACKAGE_NAME = "com.android.FAKE_APEX_PACKAGE_NAME";
    private static final String APEX_PACKAGE_KEYWORD = "FAKE_APEX_PACKAGE_NAME";
    private static final long APEX_VERSION = 1;
    private static final String APEX_NAME = "fakeApex.apex";
    private static final String REMOVE_EXISITING_APEX_UNDER_DATA_COMMAND =
            "rm -rf /data/apex/*FAKE_APEX_PACKAGE_NAME*";

    @Before
    public void setUp() throws Exception {
        mFakeApex = FileUtil.createTempFile("fakeApex", ".apex");
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(SERIAL);
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);

        mInstallApexModuleTargetPreparer =
                new InstallApexModuleTargetPreparer() {
                    @Override
                    protected String getModuleKeywordFromApexPackageName(String packageName) {
                        return APEX_PACKAGE_KEYWORD;
                    }

                    @Override
                    protected File getLocalPathForFilename(
                            IBuildInfo buildInfo, String apexFileName, ITestDevice device)
                            throws TargetSetupError {
                        return mFakeApex;
                    }

                    @Override
                    protected String parsePackageName(
                            File testApexFile, DeviceDescriptor deviceDescriptor) {
                        return APEX_PACKAGE_NAME;
                    }

                    @Override
                    protected ApexInfo retrieveApexInfo(
                            File apex, DeviceDescriptor deviceDescriptor) {
                        ApexInfo apexInfo = new ApexInfo(APEX_PACKAGE_NAME, APEX_VERSION);
                        return apexInfo;
                    }
                };
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mFakeApex);
    }

    @Test
    public void testSetupSuccess_removeExistingStagedApexSuccess() throws Exception {
        CommandResult resRemoveExistingApex = new CommandResult();
        resRemoveExistingApex.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mMockDevice.executeShellV2Command(REMOVE_EXISITING_APEX_UNDER_DATA_COMMAND))
                .andReturn(resRemoveExistingApex)
                .once();
        mockSuccessfulInstallPackageAndReboot();
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(new ApexInfo("com.android.FAKE_APEX_PACKAGE_NAME", 1));
        EasyMock.expect(mMockDevice.getActiveApexes()).andReturn(activatedApex);

        EasyMock.replay(mMockBuildInfo, mMockDevice);
        mInstallApexModuleTargetPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockBuildInfo, mMockDevice);
    }

    @Test
    public void testSetupFail_removeExistingStagedApexFail() throws Exception {
        CommandResult resRemoveExistingApex = new CommandResult();
        resRemoveExistingApex.setStatus(CommandStatus.FAILED);
        EasyMock.expect(mMockDevice.executeShellV2Command(REMOVE_EXISITING_APEX_UNDER_DATA_COMMAND))
                .andReturn(resRemoveExistingApex);
        try {
            EasyMock.replay(mMockBuildInfo, mMockDevice);
            mInstallApexModuleTargetPreparer.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .contains(
                                    "Failed to clean up com.android.FAKE_APEX_PACKAGE_NAME "
                                            + "under data/apex on device"));
        } finally {
            EasyMock.verify(mMockBuildInfo, mMockDevice);
        }
    }

    @Test
    public void testSetupSuccess_getActivatedPackageSuccess() throws Exception {
        CommandResult resRemoveExistingApex = new CommandResult();
        resRemoveExistingApex.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mMockDevice.executeShellV2Command(REMOVE_EXISITING_APEX_UNDER_DATA_COMMAND))
                .andReturn(resRemoveExistingApex)
                .once();
        mockSuccessfulInstallPackageAndReboot();
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(new ApexInfo("com.android.FAKE_APEX_PACKAGE_NAME", 1));
        EasyMock.expect(mMockDevice.getActiveApexes()).andReturn(activatedApex);

        EasyMock.replay(mMockBuildInfo, mMockDevice);
        mInstallApexModuleTargetPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockBuildInfo, mMockDevice);
    }

    @Test
    public void testSetupFail_getActivatedPackageFail() throws Exception {
        CommandResult resRemoveExistingApex = new CommandResult();
        resRemoveExistingApex.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mMockDevice.executeShellV2Command(REMOVE_EXISITING_APEX_UNDER_DATA_COMMAND))
                .andReturn(resRemoveExistingApex)
                .once();
        mockSuccessfulInstallPackageAndReboot();
        EasyMock.expect(mMockDevice.getActiveApexes()).andReturn(new HashSet<ApexInfo>());

        try {
            EasyMock.replay(mMockBuildInfo, mMockDevice);
            mInstallApexModuleTargetPreparer.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .contains("Failed to retrieve activated apex on device serial."));
        } finally {
            EasyMock.verify(mMockBuildInfo, mMockDevice);
        }
    }

    @Test
    public void testSetupFail_apexActivationFail() throws Exception {
        CommandResult resRemoveExistingApex = new CommandResult();
        resRemoveExistingApex.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mMockDevice.executeShellV2Command(REMOVE_EXISITING_APEX_UNDER_DATA_COMMAND))
                .andReturn(resRemoveExistingApex)
                .once();
        mockSuccessfulInstallPackageAndReboot();
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(new ApexInfo("com.android.FAKE_APEX_PACKAGE_NAME_TO_FAIL", 1));
        EasyMock.expect(mMockDevice.getActiveApexes()).andReturn(activatedApex);

        try {
            EasyMock.replay(mMockBuildInfo, mMockDevice);
            mInstallApexModuleTargetPreparer.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            String failureMsg =
                    String.format(
                            "packageName: %s, versionCode: %d", APEX_PACKAGE_NAME, APEX_VERSION);
            assertTrue(expected.getMessage().contains(failureMsg));
        } finally {
            EasyMock.verify(mMockBuildInfo, mMockDevice);
        }
    }

    @Test
    public void testSetupAndTearDown() throws Exception {
        CommandResult resRemoveExistingApex = new CommandResult();
        resRemoveExistingApex.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mMockDevice.executeShellV2Command(REMOVE_EXISITING_APEX_UNDER_DATA_COMMAND))
                .andReturn(resRemoveExistingApex)
                .times(2);
        mockSuccessfulInstallPackageAndReboot();
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(new ApexInfo("com.android.FAKE_APEX_PACKAGE_NAME", 1));
        EasyMock.expect(mMockDevice.getActiveApexes()).andReturn(activatedApex);
        mMockDevice.reboot();
        EasyMock.expectLastCall();

        EasyMock.replay(mMockBuildInfo, mMockDevice);
        mInstallApexModuleTargetPreparer.setUp(mMockDevice, mMockBuildInfo);
        mInstallApexModuleTargetPreparer.tearDown(mMockDevice, mMockBuildInfo, null);
        EasyMock.verify(mMockBuildInfo, mMockDevice);
    }

    @Test
    public void testGetModuleKeyword() {
        mInstallApexModuleTargetPreparer = new InstallApexModuleTargetPreparer();
        final String testApex1PackageName = "com.android.foo";
        final String testApex2PackageName = "com.android.bar_test";
        assertEquals(
                "foo",
                mInstallApexModuleTargetPreparer.getModuleKeywordFromApexPackageName(
                        testApex1PackageName));
        assertEquals(
                "bar_test",
                mInstallApexModuleTargetPreparer.getModuleKeywordFromApexPackageName(
                        testApex2PackageName));
    }

    private void mockSuccessfulInstallPackageAndReboot() throws Exception {
        EasyMock.expect(mMockDevice.installPackage((File) EasyMock.anyObject(), EasyMock.eq(true)))
                .andReturn(null)
                .once();
        mMockDevice.reboot();
        EasyMock.expectLastCall().once();
    }
}
