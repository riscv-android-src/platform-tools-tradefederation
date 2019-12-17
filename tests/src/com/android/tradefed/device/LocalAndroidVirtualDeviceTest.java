/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LocalAndroidVirtualDevice}. */
@RunWith(JUnit4.class)
public class LocalAndroidVirtualDeviceTest {

    private class TestableLocalAndroidVirtualDevice extends LocalAndroidVirtualDevice {

        TestableLocalAndroidVirtualDevice(
                IDevice device,
                IDeviceStateMonitor stateMonitor,
                IDeviceMonitor allocationMonitor) {
            super(device, stateMonitor, allocationMonitor);
        }

        IDevice currentDevice = null;
        IRunUtil currentRunUtil = null;

        @Override
        public IDevice getIDevice() {
            return currentDevice;
        }

        @Override
        public void setIDevice(IDevice device) {
            currentDevice = device;
        }

        @Override
        public void setDeviceState(TestDeviceState state) {
            Assert.assertEquals(TestDeviceState.NOT_AVAILABLE, state);
        }

        @Override
        IRunUtil createRunUtil() {
            Assert.assertNotNull("Unexpected method call to createRunUtil.", currentRunUtil);
            IRunUtil returnValue = currentRunUtil;
            currentRunUtil = null;
            return returnValue;
        }

        @Override
        File getTmpDir() {
            return mTmpDir;
        }
    }

    private static final String STUB_SERIAL_NUMBER = "local-virtual-device-0";
    private static final String ONLINE_SERIAL_NUMBER = "127.0.0.1:6520";
    private static final String BUILD_FLAVOR = "cf_x86_phone-userdebug";
    private static final long ACLOUD_TIMEOUT = 12345;

    // Temporary files.
    private File mAcloud;
    private File mImageZip;
    private File mHostPackageTarGzip;
    private File mTmpDir;

    // The initial stub device.
    private StubLocalAndroidVirtualDevice mStubLocalAvd;

    // Mock objects
    private IDeviceStateMonitor mMockDeviceStateMonitor;
    private IDeviceMonitor mMockDeviceMonitor;
    private IDeviceBuildInfo mMockDeviceBuildInfo;

    // The object under test.
    private TestableLocalAndroidVirtualDevice mLocalAvd;

    @Before
    public void setUp() throws IOException {
        mAcloud = FileUtil.createTempFile("acloud-dev", "");
        mImageZip = ZipUtil.createZip(new ArrayList<File>());
        mHostPackageTarGzip = FileUtil.createTempFile("cvd-host_package", ".tar.gz");
        createHostPackage(mHostPackageTarGzip);
        mTmpDir = FileUtil.createTempDir("LocalAvdTmp");

        mStubLocalAvd = new StubLocalAndroidVirtualDevice(STUB_SERIAL_NUMBER);

        mMockDeviceStateMonitor = EasyMock.createMock(IDeviceStateMonitor.class);
        mMockDeviceMonitor = EasyMock.createMock(IDeviceMonitor.class);
        mMockDeviceBuildInfo = EasyMock.createMock(IDeviceBuildInfo.class);
        EasyMock.expect(mMockDeviceBuildInfo.getDeviceImageFile()).andReturn(mImageZip);
        EasyMock.expect(mMockDeviceBuildInfo.getFile(EasyMock.eq("cvd-host_package.tar.gz")))
                .andReturn(mHostPackageTarGzip);
        EasyMock.expect(mMockDeviceBuildInfo.getBuildFlavor()).andReturn(BUILD_FLAVOR);

        mLocalAvd =
                new TestableLocalAndroidVirtualDevice(
                        mStubLocalAvd, mMockDeviceStateMonitor, mMockDeviceMonitor);
        mLocalAvd.setIDevice(mStubLocalAvd);
        TestDeviceOptions options = mLocalAvd.getOptions();
        options.setGceCmdTimeout(ACLOUD_TIMEOUT);
        options.setAvdDriverBinary(mAcloud);
        options.setGceDriverLogLevel(LogLevel.DEBUG);
        options.getGceDriverParams().add("-test");
    }

    @After
    public void tearDown() {
        if (mLocalAvd != null) {
            // Ensure cleanup in case the test failed before calling postInvocationTearDown.
            mLocalAvd.deleteTempDirs();
            mLocalAvd = null;
        }
        FileUtil.deleteFile(mAcloud);
        FileUtil.deleteFile(mImageZip);
        FileUtil.deleteFile(mHostPackageTarGzip);
        FileUtil.recursiveDelete(mTmpDir);
        mAcloud = null;
        mImageZip = null;
        mHostPackageTarGzip = null;
        mTmpDir = null;
    }

    private static void createHostPackage(File hostPackageTarGzip) throws IOException {
        OutputStream out = null;
        try {
            out = new FileOutputStream(hostPackageTarGzip);
            out = new BufferedOutputStream(out);
            out = new GZIPOutputStream(out);
            out = new TarArchiveOutputStream(out);
            TarArchiveOutputStream tar = (TarArchiveOutputStream) out;
            TarArchiveEntry tarEntry = new TarArchiveEntry("bin" + File.separator);
            tar.putArchiveEntry(tarEntry);
            tar.closeArchiveEntry();
            tar.finish();
        } finally {
            StreamUtil.close(out);
        }
    }

    private void replayAllMocks(Object... mocks) {
        EasyMock.replay(mocks);
        EasyMock.replay(mMockDeviceStateMonitor, mMockDeviceMonitor, mMockDeviceBuildInfo);
    }

    private IRunUtil mockAcloudCreate(
            CommandStatus status, Capture<String> hostPackageDir, Capture<String> imageDir) {
        IRunUtil runUtil = EasyMock.createMock(IRunUtil.class);
        runUtil.setEnvVariable(EasyMock.eq("TMPDIR"), EasyMock.eq(mTmpDir.getAbsolutePath()));
        runUtil.setEnvVariable(EasyMock.eq("ANDROID_HOST_OUT"), EasyMock.capture(hostPackageDir));
        runUtil.setEnvVariable(EasyMock.eq("TARGET_PRODUCT"), EasyMock.eq(BUILD_FLAVOR));

        CommandResult result = new CommandResult(status);
        result.setStderr("acloud create");
        result.setStdout("acloud create");
        EasyMock.expect(
                        runUtil.runTimedCmd(
                                EasyMock.eq(ACLOUD_TIMEOUT),
                                EasyMock.startsWith(mAcloud.getAbsolutePath()),
                                EasyMock.eq("create"),
                                EasyMock.eq("--local-instance"),
                                EasyMock.eq("1"),
                                EasyMock.eq("--local-image"),
                                EasyMock.capture(imageDir),
                                EasyMock.eq("--yes"),
                                EasyMock.eq("--skip-pre-run-check"),
                                EasyMock.eq("-vv"),
                                EasyMock.eq("-test")))
                .andReturn(result);

        return runUtil;
    }

    private IRunUtil mockAcloudDelete(CommandStatus status) {
        IRunUtil runUtil = EasyMock.createMock(IRunUtil.class);
        runUtil.setEnvVariable(EasyMock.eq("TMPDIR"), EasyMock.eq(mTmpDir.getAbsolutePath()));

        CommandResult result = new CommandResult(status);
        result.setStderr("acloud delete");
        result.setStdout("acloud delete");
        EasyMock.expect(
                        runUtil.runTimedCmd(
                                EasyMock.eq(ACLOUD_TIMEOUT),
                                EasyMock.startsWith(mAcloud.getAbsolutePath()),
                                EasyMock.eq("delete"),
                                EasyMock.eq("--instance-names"),
                                EasyMock.eq("local-instance-1"),
                                EasyMock.eq("-vv")))
                .andReturn(result);

        return runUtil;
    }

    private ITestLogger mockReportInstanceLogs() {
        ITestLogger testLogger = EasyMock.createMock(ITestLogger.class);
        testLogger.testLog(
                EasyMock.eq("cuttlefish_config.json"),
                EasyMock.eq(LogDataType.TEXT),
                EasyMock.anyObject());
        testLogger.testLog(
                EasyMock.eq("kernel.log"),
                EasyMock.eq(LogDataType.KERNEL_LOG),
                EasyMock.anyObject());
        testLogger.testLog(
                EasyMock.eq("logcat"), EasyMock.eq(LogDataType.LOGCAT), EasyMock.anyObject());
        testLogger.testLog(
                EasyMock.eq("launcher.log"), EasyMock.eq(LogDataType.TEXT), EasyMock.anyObject());
        return testLogger;
    }

    private void createEmptyFiles(File parent, String... names) throws IOException {
        parent.mkdirs();
        for (String name : names) {
            Assert.assertTrue(new File(parent, name).createNewFile());
        }
    }

    private void assertFinalDeviceState(IDevice device) {
        Assert.assertTrue(StubLocalAndroidVirtualDevice.class.equals(device.getClass()));
        StubLocalAndroidVirtualDevice stubDevice = (StubLocalAndroidVirtualDevice) device;
        Assert.assertEquals(STUB_SERIAL_NUMBER, stubDevice.getSerialNumber());
    }

    /**
     * Test that both {@link LocalAndroidVirtualDevice#preInvocationSetup(IBuildInfo,
     * List<IBuildInfo>)} and {@link LocalAndroidVirtualDevice#postInvocationTearDown(Throwable)}
     * succeed.
     */
    @Test
    public void testPreinvocationSetupSuccess()
            throws DeviceNotAvailableException, IOException, TargetSetupError {
        Capture<String> hostPackageDir = new Capture<String>();
        Capture<String> imageDir = new Capture<String>();
        IRunUtil acloudCreateRunUtil =
                mockAcloudCreate(CommandStatus.SUCCESS, hostPackageDir, imageDir);

        IRunUtil acloudDeleteRunUtil = mockAcloudDelete(CommandStatus.SUCCESS);

        ITestLogger testLogger = mockReportInstanceLogs();

        IDevice mockOnlineDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mockOnlineDevice.getSerialNumber()).andReturn(ONLINE_SERIAL_NUMBER);

        replayAllMocks(acloudCreateRunUtil, acloudDeleteRunUtil, testLogger, mockOnlineDevice);

        // Test setUp.
        mLocalAvd.setTestLogger(testLogger);
        mLocalAvd.currentRunUtil = acloudCreateRunUtil;
        mLocalAvd.preInvocationSetup(mMockDeviceBuildInfo, null);

        Assert.assertEquals(ONLINE_SERIAL_NUMBER, mLocalAvd.getIDevice().getSerialNumber());

        File capturedHostPackageDir = new File(hostPackageDir.getValue());
        File capturedImageDir = new File(imageDir.getValue());
        Assert.assertTrue(capturedHostPackageDir.isDirectory());
        Assert.assertTrue(capturedImageDir.isDirectory());

        // Set the device to be online.
        mLocalAvd.setIDevice(mockOnlineDevice);
        // Create the logs and configuration that the local AVD object expects.
        File runtimeDir =
                FileUtil.getFileForPath(
                        mTmpDir, "acloud_cvd_temp", "instance_home_1", "cuttlefish_runtime");
        Assert.assertTrue(runtimeDir.mkdirs());
        createEmptyFiles(
                runtimeDir, "kernel.log", "logcat", "launcher.log", "cuttlefish_config.json");

        // Test tearDown.
        mLocalAvd.currentRunUtil = acloudDeleteRunUtil;
        mLocalAvd.postInvocationTearDown(null);

        assertFinalDeviceState(mLocalAvd.getIDevice());

        Assert.assertFalse(capturedHostPackageDir.exists());
        Assert.assertFalse(capturedImageDir.exists());
    }

    /** Test that the device cannot boot within timeout. */
    @Test
    public void testPreInvocationSetupTimeout() throws DeviceNotAvailableException {
        Capture<String> hostPackageDir = new Capture<String>();
        Capture<String> imageDir = new Capture<String>();
        IRunUtil acloudCreateRunUtil =
                mockAcloudCreate(CommandStatus.TIMED_OUT, hostPackageDir, imageDir);

        IRunUtil acloudDeleteRunUtil = mockAcloudDelete(CommandStatus.FAILED);

        ITestLogger testLogger = EasyMock.createMock(ITestLogger.class);

        replayAllMocks(acloudCreateRunUtil, acloudDeleteRunUtil, testLogger);

        // Test setUp.
        TargetSetupError expectedException = null;
        mLocalAvd.setTestLogger(testLogger);
        mLocalAvd.currentRunUtil = acloudCreateRunUtil;
        try {
            mLocalAvd.preInvocationSetup(mMockDeviceBuildInfo, null);
            Assert.fail("TargetSetupError is not thrown");
        } catch (TargetSetupError e) {
            expectedException = e;
        }

        Assert.assertEquals(ONLINE_SERIAL_NUMBER, mLocalAvd.getIDevice().getSerialNumber());

        File capturedHostPackageDir = new File(hostPackageDir.getValue());
        File capturedImageDir = new File(imageDir.getValue());
        Assert.assertTrue(capturedHostPackageDir.isDirectory());
        Assert.assertTrue(capturedImageDir.isDirectory());

        // Test tearDown.
        mLocalAvd.currentRunUtil = acloudDeleteRunUtil;
        mLocalAvd.postInvocationTearDown(expectedException);

        assertFinalDeviceState(mLocalAvd.getIDevice());

        Assert.assertFalse(capturedHostPackageDir.exists());
        Assert.assertFalse(capturedImageDir.exists());
    }

    /** Test that the device fails to boot. */
    @Test
    public void testPreInvocationSetupFailure() throws DeviceNotAvailableException {
        Capture<String> hostPackageDir = new Capture<String>();
        Capture<String> imageDir = new Capture<String>();
        IRunUtil acloudCreateRunUtil =
                mockAcloudCreate(CommandStatus.FAILED, hostPackageDir, imageDir);

        IRunUtil acloudDeleteRunUtil = mockAcloudDelete(CommandStatus.FAILED);

        ITestLogger testLogger = EasyMock.createMock(ITestLogger.class);

        replayAllMocks(acloudCreateRunUtil, acloudDeleteRunUtil, testLogger);

        // Test setUp.
        TargetSetupError expectedException = null;
        mLocalAvd.setTestLogger(testLogger);
        mLocalAvd.currentRunUtil = acloudCreateRunUtil;
        try {
            mLocalAvd.preInvocationSetup(mMockDeviceBuildInfo, null);
            Assert.fail("TargetSetupError is not thrown");
        } catch (TargetSetupError e) {
            expectedException = e;
        }

        Assert.assertEquals(ONLINE_SERIAL_NUMBER, mLocalAvd.getIDevice().getSerialNumber());

        File capturedHostPackageDir = new File(hostPackageDir.getValue());
        File capturedImageDir = new File(imageDir.getValue());
        Assert.assertTrue(capturedHostPackageDir.isDirectory());
        Assert.assertTrue(capturedImageDir.isDirectory());

        // Test tearDown.
        mLocalAvd.currentRunUtil = acloudDeleteRunUtil;
        mLocalAvd.postInvocationTearDown(expectedException);

        assertFinalDeviceState(mLocalAvd.getIDevice());

        Assert.assertFalse(capturedHostPackageDir.exists());
        Assert.assertFalse(capturedImageDir.exists());
    }
}
