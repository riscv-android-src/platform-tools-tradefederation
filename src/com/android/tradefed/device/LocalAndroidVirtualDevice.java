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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TarUtil;
import com.android.tradefed.util.ZipUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The class for local virtual devices running on TradeFed host. */
public class LocalAndroidVirtualDevice extends TestDevice implements ITestLoggerReceiver {

    // Environment variables.
    private static final String ANDROID_HOST_OUT = "ANDROID_HOST_OUT";
    private static final String TARGET_PRODUCT = "TARGET_PRODUCT";
    private static final String TMPDIR = "TMPDIR";

    // The name of the GZIP file containing launch_cvd and stop_cvd.
    private static final String CVD_HOST_PACKAGE_NAME = "cvd-host_package.tar.gz";

    // The port of cuttlefish instance 1.
    private static final int CUTTLEFISH_FIRST_HOST_PORT = 6520;

    private static final String ACLOUD_CVD_TEMP_DIR_NAME = "acloud_cvd_temp";
    private static final String INSTANCE_DIR_NAME_PREFIX = "instance_home_";
    private static final String CUTTLEFISH_RUNTIME_DIR_NAME = "cuttlefish_runtime";
    private static final String INSTANCE_NAME_PREFIX = "local-instance-";

    private ITestLogger mTestLogger = null;

    // Temporary directories for runtime files, host package, and images.
    private File mHostPackageDir = null;
    private boolean mShouldDeleteHostPackageDir = false;
    private File mImageDir = null;

    // The data for restoring the stub device at tear-down.
    private String mOriginalSerialNumber = null;

    // A positive integer for acloud to identify this device.
    private int mInstanceId = -1;

    public LocalAndroidVirtualDevice(
            IDevice device, IDeviceStateMonitor stateMonitor, IDeviceMonitor allocationMonitor) {
        super(device, stateMonitor, allocationMonitor);
    }

    /** Execute common setup procedure and launch the virtual device. */
    @Override
    public void preInvocationSetup(IBuildInfo info, List<IBuildInfo> testResourceBuildInfos)
            throws TargetSetupError, DeviceNotAvailableException {
        // The setup method in super class does not require the device to be online.
        super.preInvocationSetup(info, testResourceBuildInfos);

        // TODO(b/133211308): multiple instances
        mInstanceId = 1;
        replaceStubDevice("127.0.0.1:" + (CUTTLEFISH_FIRST_HOST_PORT + mInstanceId - 1));

        createTempDirs((IDeviceBuildInfo) info);

        CommandResult result = acloudCreate(info.getBuildFlavor(), getOptions());
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            throw new TargetSetupError(
                    String.format("Cannot launch virtual device. stderr:\n%s", result.getStderr()),
                    getDeviceDescriptor());
        }
    }

    /** Execute common tear-down procedure and stop the virtual device. */
    @Override
    public void postInvocationTearDown(Throwable exception) {
        TestDeviceOptions options = getOptions();
        try {
            if (!options.shouldSkipTearDown() && mHostPackageDir != null) {
                CommandResult result = acloudDelete(options);
                if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                    CLog.e("Cannot stop the virtual device.");
                }
            } else {
                CLog.i("Skip stopping the virtual device.");
            }

            reportInstanceLogs();
        } finally {
            restoreStubDevice();

            if (!options.shouldSkipTearDown()) {
                deleteTempDirs();
            } else {
                CLog.i(
                        "Skip deleting the temporary directories.\nHost package: %s\nImage: %s\n",
                        mHostPackageDir, mImageDir);
            }

            super.postInvocationTearDown(exception);
        }
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }

    /**
     * Initialize mHostPackageDir and mImageDir.
     *
     * @param deviceBuildInfo the {@link IDeviceBuildInfo} that provides host package and image zip.
     * @throws TargetSetupError if any file is not found or cannot be extracted.
     */
    private void createTempDirs(IDeviceBuildInfo deviceBuildInfo) throws TargetSetupError {
        // Extract host package to mHostPackageDir.
        File hostPackageGzip = deviceBuildInfo.getFile(CVD_HOST_PACKAGE_NAME);
        if (hostPackageGzip != null) {
            try {
                mHostPackageDir = TarUtil.extractTarGzipToTemp(hostPackageGzip, "CvdHostPackage");
            } catch (IOException ex) {
                throw new TargetSetupError(
                        "Cannot extract host package.", ex, getDeviceDescriptor());
            }
            mShouldDeleteHostPackageDir = true;
            FileUtil.chmodRWXRecursively(new File(mHostPackageDir, "bin"));
        } else {
            mShouldDeleteHostPackageDir = false;
            CLog.i(
                    "Use the host tools in %s as build info does not provide %s.",
                    ANDROID_HOST_OUT, CVD_HOST_PACKAGE_NAME);
            String androidHostOut = System.getenv(ANDROID_HOST_OUT);
            if (Strings.isNullOrEmpty(androidHostOut) || !new File(androidHostOut).isDirectory()) {
                throw new TargetSetupError(
                        String.format(
                                "%s is not in build info, and %s is not set.",
                                CVD_HOST_PACKAGE_NAME, ANDROID_HOST_OUT),
                        getDeviceDescriptor());
            }
            mHostPackageDir = new File(androidHostOut);
        }

        // Extract images to mImageDir.
        try {
            mImageDir =
                    ZipUtil.extractZipToTemp(deviceBuildInfo.getDeviceImageFile(), "LocalAvdImage");
        } catch (IOException ex) {
            throw new TargetSetupError("Cannot extract image zip.", ex, getDeviceDescriptor());
        }
    }

    /** Delete mHostPackageDir and mImageDir. */
    @VisibleForTesting
    void deleteTempDirs() {
        FileUtil.recursiveDelete(mImageDir);
        if (mShouldDeleteHostPackageDir) {
            mShouldDeleteHostPackageDir = false;
            FileUtil.recursiveDelete(mHostPackageDir);
        }
        mImageDir = null;
        mHostPackageDir = null;
    }

    /**
     * Change the initial serial number of {@link StubLocalAndroidVirtualDevice}.
     *
     * @param newSerialNumber the serial number of the new stub device.
     * @throws TargetSetupError if the original device type is not expected.
     */
    private void replaceStubDevice(String newSerialNumber) throws TargetSetupError {
        IDevice device = getIDevice();
        if (!StubLocalAndroidVirtualDevice.class.equals(device.getClass())) {
            throw new TargetSetupError(
                    "Unexpected device type: " + device.getClass(), getDeviceDescriptor());
        }
        mOriginalSerialNumber = device.getSerialNumber();
        setIDevice(new StubLocalAndroidVirtualDevice(newSerialNumber));
        setFastbootEnabled(false);
    }

    /**
     * Set this device to be offline and associate it with a {@link StubLocalAndroidVirtualDevice}.
     */
    private void restoreStubDevice() {
        if (mOriginalSerialNumber == null) {
            CLog.w("Skip restoring the stub device.");
            return;
        }
        setIDevice(new StubLocalAndroidVirtualDevice(mOriginalSerialNumber));
        setFastbootEnabled(false);
        mOriginalSerialNumber = null;
    }

    private String getInstanceDirName() {
        return INSTANCE_DIR_NAME_PREFIX + mInstanceId;
    }

    private File getInstanceDir() {
        return FileUtil.getFileForPath(
                getTmpDir(),
                ACLOUD_CVD_TEMP_DIR_NAME,
                getInstanceDirName(),
                CUTTLEFISH_RUNTIME_DIR_NAME);
    }

    private String getInstanceName() {
        return INSTANCE_NAME_PREFIX + mInstanceId;
    }

    private static void addLogLevelToAcloudCommand(List<String> command, LogLevel logLevel) {
        if (LogLevel.VERBOSE.equals(logLevel)) {
            command.add("-v");
        } else if (LogLevel.DEBUG.equals(logLevel)) {
            command.add("-vv");
        }
    }

    private CommandResult acloudCreate(String buildFlavor, TestDeviceOptions options) {
        CommandResult result = null;

        File acloud = options.getAvdDriverBinary();
        if (acloud == null || !acloud.isFile()) {
            CLog.e("Specified AVD driver binary is not a file.");
            result = new CommandResult(CommandStatus.EXCEPTION);
            result.setStderr("Specified AVD driver binary is not a file.");
            return result;
        }
        acloud.setExecutable(true);

        for (int attempt = 0; attempt < options.getGceMaxAttempt(); attempt++) {
            result =
                    acloudCreate(
                            options.getGceCmdTimeout(),
                            acloud,
                            buildFlavor,
                            options.getGceDriverLogLevel(),
                            options.getGceDriverParams());
            if (CommandStatus.SUCCESS.equals(result.getStatus())) {
                break;
            }
            CLog.w(
                    "Failed to start local virtual instance with attempt: %d; command status: %s",
                    attempt, result.getStatus());
        }
        return result;
    }

    private CommandResult acloudCreate(
            long timeout, File acloud, String buildFlavor, LogLevel logLevel, List<String> args) {
        IRunUtil runUtil = createRunUtil();
        // The command creates the instance directory under TMPDIR.
        runUtil.setEnvVariable(TMPDIR, getTmpDir().getAbsolutePath());
        // The command finds bin/launch_cvd in ANDROID_HOST_OUT.
        runUtil.setEnvVariable(ANDROID_HOST_OUT, mHostPackageDir.getAbsolutePath());
        runUtil.setEnvVariable(TARGET_PRODUCT, buildFlavor);
        // TODO(b/141349771): Size of sockaddr_un->sun_path is 108, which may be too small for this
        // path.
        if (new File(getInstanceDir(), "launcher_monitor.sock").getAbsolutePath().length() > 108) {
            CLog.w("Length of instance path is too long for launch_cvd.");
        }

        List<String> command =
                new ArrayList<String>(
                        Arrays.asList(
                                acloud.getAbsolutePath(),
                                "create",
                                "--local-instance",
                                Integer.toString(mInstanceId),
                                "--local-image",
                                mImageDir.getAbsolutePath(),
                                "--yes",
                                "--skip-pre-run-check"));
        addLogLevelToAcloudCommand(command, logLevel);
        command.addAll(args);

        CommandResult result = runUtil.runTimedCmd(timeout, command.toArray(new String[0]));
        CLog.i("acloud create stdout:\n%s", result.getStdout());
        CLog.i("acloud create stderr:\n%s", result.getStderr());
        return result;
    }

    private CommandResult acloudDelete(TestDeviceOptions options) {
        File acloud = options.getAvdDriverBinary();
        if (acloud == null || !acloud.isFile()) {
            CLog.e("Specified AVD driver binary is not a file.");
            return new CommandResult(CommandStatus.EXCEPTION);
        }
        acloud.setExecutable(true);

        IRunUtil runUtil = createRunUtil();
        runUtil.setEnvVariable(TMPDIR, getTmpDir().getAbsolutePath());

        List<String> command =
                new ArrayList<String>(
                        Arrays.asList(
                                acloud.getAbsolutePath(),
                                "delete",
                                "--instance-names",
                                getInstanceName()));
        addLogLevelToAcloudCommand(command, options.getGceDriverLogLevel());

        CommandResult result =
                runUtil.runTimedCmd(options.getGceCmdTimeout(), command.toArray(new String[0]));
        CLog.i("acloud delete stdout:\n%s", result.getStdout());
        CLog.i("acloud delete stderr:\n%s", result.getStderr());
        return result;
    }

    private void reportInstanceLogs() {
        if (mTestLogger == null) {
            return;
        }
        reportInstanceLog("kernel.log", LogDataType.KERNEL_LOG);
        reportInstanceLog("logcat", LogDataType.LOGCAT);
        reportInstanceLog("launcher.log", LogDataType.TEXT);
        reportInstanceLog("cuttlefish_config.json", LogDataType.TEXT);
    }

    private void reportInstanceLog(String fileName, LogDataType type) {
        File file = new File(getInstanceDir(), fileName);
        if (file.exists()) {
            try (InputStreamSource source = new FileInputStreamSource(file)) {
                mTestLogger.testLog(fileName, type, source);
            }
        } else {
            CLog.w("%s doesn't exist.", fileName);
        }
    }

    @VisibleForTesting
    IRunUtil createRunUtil() {
        return new RunUtil();
    }

    @VisibleForTesting
    File getTmpDir() {
        return new File(System.getProperty("java.io.tmpdir"));
    }
}
