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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.Set;

/** A target preparer that attempts to install an apex modules to device. */
@OptionClass(alias = "apex-installer")
public class InstallApexModuleTargetPreparer extends SuiteApkInstaller {

    private static final String APEX_DATA_DIR = "/data/apex";

    // TODO: Use interface to retrieve manifest info from staging apex file.
    // Currently we need to pass in the package name and version number.
    @Option(
        name = "apex-packageName",
        description = "The package name of the apex module. Specified in manifest.json.",
        importance = Importance.IF_UNSET,
        mandatory = true
    )
    private String mApexPackageName;

    @Option(
        name = "apex-version",
        description = "The version of the test apex file.",
        importance = Importance.IF_UNSET,
        mandatory = true
    )
    private long mApexVersion;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        CommandResult result =
                device.executeShellV2Command(
                        "rm -rf "
                                + APEX_DATA_DIR
                                + "/*"
                                + getModuleKeywordFromApexPackageName(mApexPackageName)
                                + "*");
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to clean up data/apex on device %s. Output: %s Error: %s",
                            device.getSerialNumber(), result.getStdout(), result.getStderr()),
                    device.getDeviceDescriptor());
        }

        //TODO: Make sure the apex to install is the one specified (e.g., checking version info).
        super.setUp(device, buildInfo);
        device.reboot();

        ApexInfo testApexInfo = new ApexInfo(mApexPackageName, mApexVersion);
        Set<ApexInfo> activatedApexes = device.getActiveApexes();
        if (activatedApexes.isEmpty()) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to retrieve activated apex on device %s. Empty set returned.",
                            device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }
        if (!activatedApexes.contains(testApexInfo)) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to activate %s on device %s. Activated package list: %s",
                            getTestsFileName().toString(),
                            device.getSerialNumber(),
                            activatedApexes.toString()),
                    device.getDeviceDescriptor());
        }
        CLog.i("Apex module is installed successfully");
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        super.tearDown(device, buildInfo, e);
        if (!(e instanceof DeviceNotAvailableException)) {
            CommandResult result =
                    device.executeShellV2Command(
                            "rm -rf "
                                    + APEX_DATA_DIR
                                    + "/*"
                                    + getModuleKeywordFromApexPackageName(mApexPackageName)
                                    + "*");
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                CLog.i(
                        String.format(
                                "Failed to remove %s from %s", mApexPackageName, APEX_DATA_DIR));
            }
            device.reboot();
        }
    }

    protected String getModuleKeywordFromApexPackageName(String packageName) {
        String[] components = packageName.split("\\.");
        return components[components.length - 1];
    }
}
