/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tradefed.build;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.build.IBuildInfo.BuildInfoProperties;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.ExecutionFiles;
import com.android.tradefed.invoker.ExecutionFiles.FilesKey;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.invoker.logger.CurrentInvocation.InvocationInfo;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.BuildInfoUtil;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A {@link IDeviceBuildProvider} that bootstraps build info from the test device
 *
 * <p>
 * This is typically used for devices with an externally supplied build, i.e. not generated by
 * in-house build system. Certain information, specifically the branch, is not actually available
 * from the device, therefore it's artificially generated.
 *
 * <p>All build meta data info comes from various ro.* property fields on device
 *
 * <p>Currently this build provider generates meta data as follows:
 * <ul>
 * <li>branch:
 * $(ro.product.brand)-$(ro.product.name)-$(ro.product.device)-$(ro.build.version.release),
 * for example:
 * <ul>
 *   <li>for Google Play edition Samsung S4 running Android 4.2: samsung-jgedlteue-jgedlte-4.2
 *   <li>for Nexus 7 running Android 4.2: google-nakasi-grouper-4.2
 * </ul>
 * <li>build flavor: as provided by {@link ITestDevice#getBuildFlavor()}
 * <li>build alias: as provided by {@link ITestDevice#getBuildAlias()}
 * <li>build id: as provided by {@link ITestDevice#getBuildId()}
 */
@OptionClass(alias = "bootstrap-build")
public class BootstrapBuildProvider implements IDeviceBuildProvider {

    @Option(name="build-target", description="build target name to supply.")
    private String mBuildTargetName = "bootstrapped";

    @Option(name="branch", description="build branch name to supply.")
    private String mBranch = null;

    @Option(
        name = "build-id",
        description = "Specify the build id to report instead of the one from the device."
    )
    private String mBuildId = null;

    @Option(name="shell-available-timeout",
            description="Time to wait in seconds for device shell to become available. " +
            "Default to 300 seconds.")
    private long mShellAvailableTimeout = 5 * 60;

    @Option(name="tests-dir", description="Path to top directory of expanded tests zip")
    private File mTestsDir = null;

    @Option(
            name = "extra-file",
            description =
                    "The extra file to be added to the Build Provider. "
                            + "Can be repeated. For example --extra-file file_key_1=/path/to/file")
    private Map<String, File> mExtraFiles = new LinkedHashMap<>();

    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        throw new UnsupportedOperationException("Call getBuild(ITestDevice)");
    }

    @Override
    public void cleanUp(IBuildInfo info) {
    }

    @Override
    public IBuildInfo getBuild(ITestDevice device) throws BuildRetrievalError,
            DeviceNotAvailableException {
        IBuildInfo info = new DeviceBuildInfo(mBuildId, mBuildTargetName);
        addFiles(info, mExtraFiles);
        info.setProperties(BuildInfoProperties.DO_NOT_COPY_ON_SHARDING);
        if (!(device.getIDevice() instanceof StubDevice)) {
            if (!device.waitForDeviceShell(mShellAvailableTimeout * 1000)) {
                throw new DeviceNotAvailableException(
                        String.format(
                                "Shell did not become available in %d seconds",
                                mShellAvailableTimeout),
                        device.getSerialNumber());
            }
        } else {
            // In order to avoid issue with a null branch, use a placeholder stub for StubDevice.
            mBranch = "stub";
        }
        BuildInfoUtil.bootstrapDeviceBuildAttributes(
                info,
                device,
                mBuildId,
                null /* override build flavor */,
                mBranch,
                null /* override build alias */);
        if (mTestsDir != null && mTestsDir.isDirectory()) {
            info.setFile("testsdir", mTestsDir, info.getBuildId());
        }
        // Avoid tests dir being null, by creating a temporary dir.
        boolean createdTestDir = false;
        if (mTestsDir == null) {
            createdTestDir = true;
            try {
                mTestsDir =
                        FileUtil.createTempDir(
                                "bootstrap-test-dir",
                                CurrentInvocation.getInfo(InvocationInfo.WORK_FOLDER));
            } catch (IOException e) {
                throw new BuildRetrievalError(
                        e.getMessage(), e, InfraErrorIdentifier.FAIL_TO_CREATE_FILE);
            }
            ((IDeviceBuildInfo) info).setTestsDir(mTestsDir, "1");
        }
        if (getInvocationFiles() != null) {
            getInvocationFiles()
                    .put(
                            FilesKey.TESTS_DIRECTORY,
                            mTestsDir,
                            !createdTestDir /* shouldNotDelete */);
        }
        return info;
    }

    /**
     * Add file to build info.
     *
     * @param buildInfo the {@link IBuildInfo} the build info
     * @param fileMaps the {@link Map} of file_key and file object to be added to the buildInfo
     */
    private void addFiles(IBuildInfo buildInfo, Map<String, File> fileMaps) {
        for (final Entry<String, File> entry : fileMaps.entrySet()) {
            buildInfo.setFile(entry.getKey(), entry.getValue(), "0");
        }
    }

    @VisibleForTesting
    ExecutionFiles getInvocationFiles() {
        return CurrentInvocation.getInvocationFiles();
    }

    public final File getTestsDir() {
        return mTestsDir;
    }
}
