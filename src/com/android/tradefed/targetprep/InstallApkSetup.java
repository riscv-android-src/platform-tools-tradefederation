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

package com.android.tradefed.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.AbiFormatter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link ITargetPreparer} that installs one or more apks located on the filesystem.
 * <p>
 * This class should only be used for installing apks from the filesystem when all versions of the
 * test rely on the apk being on the filesystem.  For tests which use {@link TestAppInstallSetup}
 * to install apks from the tests zip file, use {@code --alt-dir} to specify an alternate directory
 * on the filesystem containing the apk for other test configurations (for example, local runs
 * where the tests zip file is not present).
 * </p>
 */
@OptionClass(alias = "install-apk")
public class InstallApkSetup implements ITargetPreparer {

    private static final String LOG_TAG = InstallApkSetup.class.getSimpleName();

    @Option(name = "apk-path", description =
        "the filesystem path of the apk to install. Can be repeated.",
        importance = Importance.IF_UNSET)
    private Collection<File> mApkPaths = new ArrayList<File>();

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = null;

    @Option(name = "install-arg",
            description = "Additional arguments to be passed to install command, "
                    + "including leading dash, e.g. \"-d\"")
    private Collection<String> mInstallArgs = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        for (File apk : mApkPaths) {
            if (!apk.exists()) {
                throw new TargetSetupError(String.format("%s does not exist",
                        apk.getAbsolutePath()));
            }
            Log.i(LOG_TAG, String.format("Installing %s on %s", apk.getName(),
                    device.getSerialNumber()));
            if (mForceAbi != null) {
                String abi = AbiFormatter.getDefaultAbi(device, mForceAbi);
                if (abi != null) {
                    mInstallArgs.add(String.format("--abi %s", abi));
                }
            }
            String result = device.installPackage(apk, true, mInstallArgs.toArray(new String[]{}));
            if (result != null) {
                Log.e(LOG_TAG, String.format("Failed to install %s on device %s. Reason: %s",
                        apk.getAbsolutePath(), device.getSerialNumber(), result));
            }
        }
    }
}
