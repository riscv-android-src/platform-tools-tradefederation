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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;

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

    private boolean mCreatedTestDir = false;

    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        throw new UnsupportedOperationException("Call getBuild(ITestDevice)");
    }

    @Override
    public void buildNotTested(IBuildInfo info) {
        // no op
        CLog.i("ignoring buildNotTested call, build = %s ", info.getBuildId());
    }

    @Override
    public void cleanUp(IBuildInfo info) {
        // If we created the tests dir, we delete it.
        if (mCreatedTestDir) {
            FileUtil.recursiveDelete(((IDeviceBuildInfo) info).getTestsDir());
        }
    }

    @Override
    public IBuildInfo getBuild(ITestDevice device) throws BuildRetrievalError,
            DeviceNotAvailableException {
        String buildId = mBuildId;
        // If mBuildId is set, do not use the device build-id
        if (buildId == null) {
            buildId = device.getBuildId();
        }
        IBuildInfo info = new DeviceBuildInfo(buildId, mBuildTargetName);
        if (!(device.getIDevice() instanceof StubDevice)) {
            if (!device.waitForDeviceShell(mShellAvailableTimeout * 1000)) {
                throw new DeviceNotAvailableException(
                        String.format(
                                "Shell did not become available in %d seconds",
                                mShellAvailableTimeout),
                        device.getSerialNumber());
            }
            if (mBranch == null) {
                mBranch =
                        String.format(
                                "%s-%s-%s-%s",
                                device.getProperty("ro.product.brand"),
                                device.getProperty("ro.product.name"),
                                device.getProductVariant(),
                                device.getProperty("ro.build.version.release"));
            }
        } else {
            // In order to avoid issue with a null branch, use a placeholder stub for StubDevice.
            mBranch = "stub";
        }
        info.setBuildBranch(mBranch);
        info.setBuildFlavor(device.getBuildFlavor());
        info.addBuildAttribute("build_alias", device.getBuildAlias());
        if (mTestsDir != null && mTestsDir.isDirectory()) {
            info.setFile("testsdir", mTestsDir, buildId);
        }
        // Avoid tests dir being null, by creating a temporary dir.
        if (mTestsDir == null) {
            mCreatedTestDir = true;
            try {
                mTestsDir = FileUtil.createTempDir("bootstrap-test-dir");
            } catch (IOException e) {
                throw new BuildRetrievalError(e.getMessage(), e);
            }
            ((IDeviceBuildInfo) info).setTestsDir(mTestsDir, "1");
        }
        return info;
    }
}
