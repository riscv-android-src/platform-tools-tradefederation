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
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/*
 * A target preparer that attempts to install an apex modules to device and verify install success.
 */
@OptionClass(alias = "apex-installer")
public class InstallApexModuleTargetPreparer extends SuiteApkInstaller {

    private static final String APEX_DATA_DIR = "/data/apex/active";
    private static final String STAGING_DATA_DIR = "/data/pkg_staging";
    private static final String APEX_SUFFIX = ".apex";
    private static final String TRAIN_WITH_APEX_INSTALL_CMD = "install-multi-package";

    private List<ApexInfo> mTestApexInfoList;
    private List<String> mApkInstalled;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {

        if (getTestsFileName().isEmpty()) {
            throw new TargetSetupError(
                    "No apk/apex module file specified.", device.getDeviceDescriptor());
        }

        mApkInstalled = new ArrayList<>();
        mTestApexInfoList = new ArrayList<ApexInfo>();

        // Clean up data/apex/active and data/pkg_staging.
        cleanUpStagedAndActiveSession(device, buildInfo);

        List<String> testAppFileNames = getTestsFileName();
        if (!containsApex(testAppFileNames)) {
            super.installer(device, buildInfo, testAppFileNames);
            return;
        }

        // Test apps contain at least 1 apex.
        installer(device, buildInfo, testAppFileNames);
        // Reboot to activate the staged apex files if app list contains apex file.
        device.reboot();

        Set<ApexInfo> activatedApexes = device.getActiveApexes();
        if (activatedApexes.isEmpty()) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to retrieve activated apex on device %s. Empty set returned.",
                            device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }

        List<ApexInfo> failToActivateApex = new ArrayList<ApexInfo>();

        for (ApexInfo testApexInfo : mTestApexInfoList) {
            if (!activatedApexes.contains(testApexInfo)) {
                failToActivateApex.add(testApexInfo);
            }
        }

        if (!failToActivateApex.isEmpty()) {
            CLog.i("Activated apex packages list:");
            for (ApexInfo info : activatedApexes) {
                CLog.i("Activated apex: %s", info.toString());
            }
            throw new TargetSetupError(
                    String.format(
                            "Failed to activate %s on device %s.",
                            listApexInfo(failToActivateApex).toString(), device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }
        CLog.i("Installation succeed.");
    }

    // TODO(b/124461631): Remove after ddmlib supports install-multi-package.
    @Override
    protected void installer(ITestDevice device, IBuildInfo buildInfo, List<String> appNames)
            throws TargetSetupError, DeviceNotAvailableException {
        if (appNames.size() > 1) {
            installMultiPackageContainingApex(device, buildInfo, appNames);
        } else {
            // Single apex file install.
            super.installer(device, buildInfo, appNames);
        }
    }

    /**
     * Attempt to install a mainline train containing apex on the device.
     *
     * @param device the {@link ITestDevice} to install the train
     * @param buildInfo build artifact information
     * @param moduleFilenames List of String. The list of filenames of the mainline modules to be
     *     installed.
     */
    protected void installMultiPackageContainingApex(
            ITestDevice device, IBuildInfo buildInfo, Collection<String> moduleFilenames)
            throws TargetSetupError, DeviceNotAvailableException {

        List<String> apkPackageNames = new ArrayList<>();
        List<String> trainInstallCmd = new ArrayList<>();

        trainInstallCmd.add(TRAIN_WITH_APEX_INSTALL_CMD);

        for (String fileName : moduleFilenames) {
            File moduleFile = getLocalPathForFilename(buildInfo, fileName, device);
            if (moduleFile == null) {
                throw new TargetSetupError(
                        String.format("File %s not found.", fileName),
                        device.getDeviceDescriptor());
            }
            trainInstallCmd.add(moduleFile.getAbsolutePath());
            if (fileName.endsWith(".apk")) {
                String packageName = parsePackageName(moduleFile, device.getDeviceDescriptor());
                apkPackageNames.add(packageName);
            }
        }
        String log = device.executeAdbCommand(trainInstallCmd.toArray(new String[0]));
        if (log.contains("Success")) {
            CLog.d("Train is staged successfully. Output: %s.", log);
        } else {
            throw new TargetSetupError(
                    String.format(
                            "Failed to install %s on %s. Error log: '%s'",
                            moduleFilenames.toString(), device.getSerialNumber(), log),
                    device.getDeviceDescriptor());
        }
        mApkInstalled.addAll(apkPackageNames);
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (!(e instanceof DeviceNotAvailableException)) {
            for (String apkPkgName : mApkInstalled) {
                super.uninstallPackage(device, apkPkgName);
            }
            for (ApexInfo apexInfo : mTestApexInfoList) {
                CommandResult result =
                        device.executeShellV2Command(
                                "rm -rf "
                                        + APEX_DATA_DIR
                                        + "/*"
                                        + getModuleKeywordFromApexPackageName(apexInfo.name)
                                        + "*");
                if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                    CLog.i("Failed to remove %s from %s", apexInfo.name, APEX_DATA_DIR);
                }
            }
            device.executeShellV2Command("rm -rf " + STAGING_DATA_DIR + "/*");
            if (!mTestApexInfoList.isEmpty()) {
                device.reboot();
            }
        }
    }

    /* Retrieve ApexInfo which contains packageName and versionCode
     * from the given apex file.
     *
     * @param testApexFile The apex file we retrieve information from.
     * @return an {@link ApexInfo} containing the packageName and versionCode of the given file
     * @throws TargetSetupError if aapt parser failed to parse the file.
     */
    @VisibleForTesting
    protected ApexInfo retrieveApexInfo(File testApexFile, DeviceDescriptor deviceDescriptor)
            throws TargetSetupError {
        AaptParser parser = AaptParser.parse(testApexFile);
        if (parser == null) {
            throw new TargetSetupError("apex installed but AaptParser failed", deviceDescriptor);
        }
        return new ApexInfo(parser.getPackageName(), Long.parseLong(parser.getVersionCode()));
    }

    /* Get the keyword (e.g., 'tzdata' for com.android.tzdata.apex)
     * from the apex package name.
     *
     * @param packageName The package name of the apex file.
     * @return a string The keyword of the apex package name.
     */
    protected String getModuleKeywordFromApexPackageName(String packageName) {
        String[] components = packageName.split("\\.");
        return components[components.length - 1];
    }

    /* Helper method to format List<ApexInfo> to List<String>. */
    private ArrayList<String> listApexInfo(List<ApexInfo> list) {
        ArrayList<String> res = new ArrayList<String>();
        for (ApexInfo testApexInfo : list) {
            res.add(testApexInfo.toString());
        }
        return res;
    }

    /* Check if the app file is apex or not */
    protected boolean isApex(File file) {
        if (file.getName().endsWith(APEX_SUFFIX)) {
            return true;
        }
        return false;
    }

    /** Check if the apps need to be installed contains apex. */
    protected boolean containsApex(Collection<String> testFileNames) {
        for (String filename : testFileNames) {
            if (filename.endsWith(".apex")) {
                return true;
            }
        }
        return false;
    }

    /** Clean up data/apex/active and data/pkg_staging. */
    private void cleanUpStagedAndActiveSession(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        for (String appFilename : getTestsFileName()) {
            File appFile = getLocalPathForFilename(buildInfo, appFilename, device);
            if (isApex(appFile)) {
                ApexInfo apexInfo = retrieveApexInfo(appFile, device.getDeviceDescriptor());
                mTestApexInfoList.add(apexInfo);
                CommandResult result =
                        device.executeShellV2Command(
                                "rm -rf "
                                        + APEX_DATA_DIR
                                        + "/*"
                                        + getModuleKeywordFromApexPackageName(apexInfo.name)
                                        + "*");
                if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                    throw new TargetSetupError(
                            String.format(
                                    "Failed to clean up %s under %s on device %s."
                                            + "Output: %s Error: %s",
                                    apexInfo.name,
                                    APEX_DATA_DIR,
                                    device.getSerialNumber(),
                                    result.getStdout(),
                                    result.getStderr()),
                            device.getDeviceDescriptor());
                }
                device.executeShellV2Command("rm -rf " + STAGING_DATA_DIR + "/*");
            }
        }
    }
}

