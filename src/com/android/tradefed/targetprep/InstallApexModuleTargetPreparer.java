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
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.device.PackageInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.BundletoolUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
 * A {@link TargetPreparer} that attempts to install mainline modules to device
 * and verify install success.
 */
@OptionClass(alias = "mainline-module-installer")
public class InstallApexModuleTargetPreparer extends SuiteApkInstaller {

    private static final String APEX_DATA_DIR = "/data/apex/active/";
    private static final String STAGING_DATA_DIR = "/data/app-staging/";
    private static final String SESSION_DATA_DIR = "/data/apex/sessions/";
    private static final String APEX_SUFFIX = ".apex";
    private static final String APK_SUFFIX = ".apk";
    private static final String SPLIT_APKS_SUFFIX = ".apks";
    private static final String TRAIN_WITH_APEX_INSTALL_OPTION = "install-multi-package";

    // Modules that are mandatory for all devices. If a device does not have all of these modules,
    // it should throw an error.
    public static final ImmutableList<String> MANDATORY_MODULES =
            ImmutableList.of(
                    "com.google.android.modulemetadata",
                    "com.google.android.permissioncontroller",
                    "com.google.android.ext.services");

    private List<ApexInfo> mTestApexInfoList = new ArrayList<>();
    private Set<String> mApkToInstall = new LinkedHashSet<>();
    private List<String> mApkInstalled = new ArrayList<>();
    private List<String> mSplitsInstallArgs = new ArrayList<>();
    private BundletoolUtil mBundletoolUtil;

    @Option(name = "bundletool-file-name", description = "The file name of the bundletool jar.")
    private String mBundletoolFilename;

    @Option(
        name = "apex-staging-wait-time",
        description = "The time in ms to wait for apex staged session ready.",
        isTimeVal = true
    )
    private long mApexStagingWaitTime = 1 * 60 * 1000;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {

        if (getTestsFileName().isEmpty()) {
            CLog.i("No apk/apex module file to install. Skipping.");
            return;
        }

        cleanUpStagedAndActiveSession(device);

        List<String> testAppFileNames = getModulesToInstall(buildInfo, device);
        if (containsApks(testAppFileNames)) {
            installUsingBundleTool(buildInfo, device);
            if (mTestApexInfoList.isEmpty()) {
                CLog.i("No Apex module in the train. Skipping reboot.");
                return;
            } else {
                RunUtil.getDefault().sleep(mApexStagingWaitTime);
                device.reboot();
            }
        } else {
            installer(device, buildInfo, testAppFileNames);
            if (containsApex(testAppFileNames)
                    || containsPersistentApk(testAppFileNames, device, buildInfo)) {
                device.reboot();
            }
            if (mTestApexInfoList.isEmpty()) {
                CLog.i("Train activation succeed.");
                return;
            }
        }

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
        CLog.i("Train activation succeed.");
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (e instanceof DeviceNotAvailableException) {
            CLog.e("Device %s is not available. Teardown() skipped.", device.getSerialNumber());
            return;
        } else {
            if (mTestApexInfoList.isEmpty() && getApkInstalled().isEmpty()) {
                super.tearDown(device, buildInfo, e);
            } else {
                for (String apkPkgName : getApkInstalled()) {
                    super.uninstallPackage(device, apkPkgName);
                }
                if (!mTestApexInfoList.isEmpty()) {
                    cleanUpStagedAndActiveSession(device);
                }
            }
        }
    }

    /**
     * Gets the modules that should be installed on the train, based on the modules preloaded on the
     * device.
     *
     * @param buildInfo the {@link IBuildInfo} for the artifacts.
     * @param device the {@link ITestDevice} to install the train.
     * @return List<String> of the modules that should be installed on the device.
     * @throws DeviceNotAvailableException when device is not available.
     * @throws TargetSetupError when mandatory modules are not installed, or module cannot be
     *     installed.
     */
    public List<String> getModulesToInstall(IBuildInfo buildInfo, ITestDevice device)
            throws DeviceNotAvailableException, TargetSetupError {
        // Get all preloaded modules for the device, and check that mandatory modules are included.
        Set<String> installedPackages = new HashSet<>(device.getInstalledPackageNames());
        Set<ApexInfo> installedApexes = new HashSet<>(device.getActiveApexes());
        for (ApexInfo installedApex : installedApexes) {
            installedPackages.add(installedApex.name);
        }
        if (!installedPackages.containsAll(MANDATORY_MODULES)) {
            throw new TargetSetupError(
                    String.format(
                            "Mandatory modules are not available to install on device %s. "
                                    + "Skipping installing.",
                            device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }
        List<String> moduleFileNames = getTestsFileName();
        List<String> moduleNamesToInstall = new ArrayList<>();
        for (String moduleFileName : moduleFileNames) {
            File moduleFile = getLocalPathForFilename(buildInfo, moduleFileName, device);
            if (moduleFile == null) {
                throw new TargetSetupError(
                        String.format("%s not found.", moduleFileName),
                        device.getDeviceDescriptor());
            }
            String modulePackageName = parsePackageName(moduleFile, device.getDeviceDescriptor());
            if (installedPackages.contains(modulePackageName)) {
                long versionCode =
                        retrieveApexInfo(moduleFile, device.getDeviceDescriptor()).versionCode;
                CLog.i(
                        "Found preloaded module for %s with version name %s.",
                        modulePackageName, versionCode);
                moduleNamesToInstall.add(moduleFileName);
                installedPackages.remove(modulePackageName);
            } else {
                CLog.i(
                        "The module package %s is not preloaded on the device but is included in "
                                + "the train. Skipping.",
                        modulePackageName);
            }
        }
        // Log the modules that are not included in the train.
        if (!installedPackages.isEmpty()) {
            CLog.i(
                    "The following modules are preloaded on the device, but not included in the "
                            + "train: %s",
                    installedPackages);
        }
        return moduleNamesToInstall;
    }

    // TODO(b/124461631): Remove after ddmlib supports install-multi-package.
    @Override
    protected void installer(
            ITestDevice device, IBuildInfo buildInfo, List<String> testAppFileNames)
            throws TargetSetupError, DeviceNotAvailableException {
        if (containsApex(testAppFileNames)) {
            mTestApexInfoList = collectApexInfoFromApexModules(testAppFileNames, device, buildInfo);
        }
        if (containsPersistentApk(testAppFileNames, device, buildInfo)) {
            // When there is a persistent apk in the train, use '--staged' to install full train
            // Otherwise, do normal install without '--staged'
            installTrain(device, buildInfo, testAppFileNames, new String[] {"--staged"});
            return;
        }
        installTrain(device, buildInfo, testAppFileNames, null);
    }

    /**
     * Attempts to install a mainline train containing apex on the device.
     *
     * @param device the {@link ITestDevice} to install the train
     * @param buildInfo build artifact information
     * @param moduleFilenames List of String. The list of filenames of the mainline modules to be
     *     installed.
     */
    protected void installTrain(
            ITestDevice device,
            IBuildInfo buildInfo,
            Collection<String> moduleFilenames,
            final String[] extraArgs)
            throws TargetSetupError, DeviceNotAvailableException {

        List<String> apkPackageNames = new ArrayList<>();
        List<String> trainInstallCmd = new ArrayList<>();

        trainInstallCmd.add(TRAIN_WITH_APEX_INSTALL_OPTION);
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                trainInstallCmd.add(arg);
            }
        }

        for (String fileName : moduleFilenames) {
            File moduleFile = getLocalPathForFilename(buildInfo, fileName, device);
            if (moduleFile == null) {
                throw new TargetSetupError(
                        String.format("File %s not found.", fileName),
                        device.getDeviceDescriptor());
            }
            trainInstallCmd.add(moduleFile.getAbsolutePath());
            if (fileName.endsWith(APK_SUFFIX)) {
                String packageName = parsePackageName(moduleFile, device.getDeviceDescriptor());
                apkPackageNames.add(packageName);
            }
        }
        String log = device.executeAdbCommand(trainInstallCmd.toArray(new String[0]));

        // Wait until all apexes are fully staged and ready.
        // TODO: should have adb level solution b/130039562
        RunUtil.getDefault().sleep(mApexStagingWaitTime);

        if (log.contains("Success")) {
            CLog.d(
                    "Train is staged successfully. Cmd: %s, Output: %s.",
                    trainInstallCmd.toString(), log);
        } else {
            throw new TargetSetupError(
                    String.format(
                            "Failed to install %s on %s. Error log: '%s'",
                            moduleFilenames.toString(), device.getSerialNumber(), log),
                    device.getDeviceDescriptor());
        }
        mApkInstalled.addAll(apkPackageNames);
    }

    /**
     * Attempts to install mainline module(s) using bundletool.
     *
     * @param device the {@link ITestDevice} to install the train
     * @param buildInfo build artifact information
     */
    protected void installUsingBundleTool(IBuildInfo buildInfo, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        File bundletoolJar = getLocalPathForFilename(buildInfo, getBundletoolFileName(), device);
        if (bundletoolJar == null) {
            throw new TargetSetupError(
                    String.format(
                            " Failed to find bundletool jar on %s.", device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }
        mBundletoolUtil = new BundletoolUtil(bundletoolJar);
        String deviceSpecFilePath = "";
        try {
            deviceSpecFilePath = getBundletoolUtil().generateDeviceSpecFile(device);
        } catch (IOException e) {
            throw new TargetSetupError(
                    String.format(
                            " Failed to generate device spec file on %s.",
                            device.getSerialNumber()),
                    e,
                    device.getDeviceDescriptor());
        }
        if (getTestsFileName().size() == 1) {
            // Installs single .apks module.
            installSingleModuleUsingBundletool(
                    device, buildInfo, deviceSpecFilePath, getTestsFileName().get(0));
        } else {
            installMultipleModuleUsingBundletool(device, buildInfo, deviceSpecFilePath);
        }
        mApkInstalled.addAll(mApkToInstall);
    }

    /**
     * Attempts to install a single mainline module(.apks) using bundletool.
     *
     * @param device the {@link ITestDevice} to install the train
     * @param buildInfo build artifact information
     * @param deviceSpecFilePath the spec file of the test device
     * @param apksName the file name of the .apks
     */
    private void installSingleModuleUsingBundletool(
            ITestDevice device, IBuildInfo buildInfo, String deviceSpecFilePath, String apksName)
            throws TargetSetupError, DeviceNotAvailableException {
        File apks = getLocalPathForFilename(buildInfo, apksName, device);
        File splitsDir =
                getBundletoolUtil()
                        .extractSplitsFromApks(apks, deviceSpecFilePath, device, buildInfo);
        // Rename the extracted files and add the file to filename list.
        File[] splits = splitsDir.listFiles();

        if (splits.length == 0) {
            throw new TargetSetupError(
                    String.format("Extraction for %s failed. No apk/apex is extracted.", apksName),
                    device.getDeviceDescriptor());
        }
        String splitFileName = splits[0].getName();
        // Install .apks that contain apex module.
        if (containsApex(Arrays.asList(splitFileName))) {
            super.installer(device, buildInfo, Arrays.asList(splitFileName));
        } else {
            // Install .apks that contain apk module.
            getBundletoolUtil().installApks(apks, device);
            mApkToInstall.add(parsePackageName(splits[0], device.getDeviceDescriptor()));
        }
        return;
    }

    /**
     * Attempts to install multiple mainline modules using bundletool. Modules can be any
     * combination of .apk, .apex or .apks.
     *
     * @param device the {@link ITestDevice} to install the train
     * @param buildInfo build artifact information
     * @param deviceSpecFilePath the spec file of the test device
     */
    private void installMultipleModuleUsingBundletool(
            ITestDevice device, IBuildInfo buildInfo, String deviceSpecFilePath)
            throws TargetSetupError, DeviceNotAvailableException {
        for (String moduleFileName : getTestsFileName()) {
            File moduleFile = getLocalPathForFilename(buildInfo, moduleFileName, device);
            if (moduleFileName.endsWith(SPLIT_APKS_SUFFIX)) {
                File splitsDir =
                        getBundletoolUtil()
                                .extractSplitsFromApks(
                                        moduleFile, deviceSpecFilePath, device, buildInfo);
                File[] splits = splitsDir.listFiles();
                String splitsArgs = createInstallArgsForSplit(splits, device);
                mSplitsInstallArgs.add(splitsArgs);
            } else {
                if (moduleFileName.endsWith(APEX_SUFFIX)) {
                    ApexInfo apexInfo = retrieveApexInfo(moduleFile, device.getDeviceDescriptor());
                    mTestApexInfoList.add(apexInfo);
                } else {
                    mApkToInstall.add(parsePackageName(moduleFile, device.getDeviceDescriptor()));
                }
                mSplitsInstallArgs.add(moduleFile.getAbsolutePath());
            }
        }

        List<String> installCmd = new ArrayList<>();

        installCmd.add(TRAIN_WITH_APEX_INSTALL_OPTION);
        for (String arg : mSplitsInstallArgs) {
            installCmd.add(arg);
        }
        device.waitForDeviceAvailable();

        String log = device.executeAdbCommand(installCmd.toArray(new String[0]));
        if (log.contains("Success")) {
            CLog.d("Train is staged successfully. Output: %s.", log);
        } else {
            throw new TargetSetupError(
                    String.format(
                            "Failed to stage train on device %s. Cmd is: %s. Error log: %s.",
                            device.getSerialNumber(), installCmd.toString(), log),
                    device.getDeviceDescriptor());
        }
    }

    /**
     * Retrieves ApexInfo which contains packageName and versionCode from the given apex file.
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

    /**
     * Gets the keyword (e.g., 'tzdata' for com.android.tzdata.apex) from the apex package name.
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

    /* Checks if the app file is apex or not */
    private boolean isApex(File file) {
        if (file.getName().endsWith(APEX_SUFFIX)) {
            return true;
        }
        return false;
    }

    /** Checks if the apps need to be installed contains apex. */
    private boolean containsApex(Collection<String> testFileNames) {
        for (String filename : testFileNames) {
            if (filename.endsWith(APEX_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the apps need to be installed contains apex.
     *
     * @param testFileNames The list of the test modules
     */
    private boolean containsApks(Collection<String> testFileNames) {
        for (String filename : testFileNames) {
            if (filename.endsWith(SPLIT_APKS_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cleans up data/apex/active. data/apex/sessions, data/app-staging.
     *
     * @param device The test device
     */
    private void cleanUpStagedAndActiveSession(ITestDevice device)
            throws DeviceNotAvailableException {
        boolean reboot = false;
        if (!mTestApexInfoList.isEmpty()) {
            device.deleteFile(APEX_DATA_DIR + "*");
            device.deleteFile(STAGING_DATA_DIR + "*");
            device.deleteFile(SESSION_DATA_DIR + "*");
            reboot = true;
        } else {
            if (!device.executeShellV2Command("ls " + APEX_DATA_DIR).getStdout().isEmpty()) {
                device.deleteFile(APEX_DATA_DIR + "*");
                reboot = true;
            }
            if (!device.executeShellV2Command("ls " + STAGING_DATA_DIR).getStdout().isEmpty()) {
                device.deleteFile(STAGING_DATA_DIR + "*");
                reboot = true;
            }
            if (!device.executeShellV2Command("ls " + SESSION_DATA_DIR).getStdout().isEmpty()) {
                device.deleteFile(SESSION_DATA_DIR + "*");
                reboot = true;
            }
        }
        if (reboot) {
            device.reboot();
        }
    }

    /**
     * Creates the install args for the split .apks.
     *
     * @param splits The directory that split apk/apex get extracted to
     * @param device The test device
     * @return a {@link String} representing the install args for the split apks.
     */
    private String createInstallArgsForSplit(File[] splits, ITestDevice device)
            throws TargetSetupError {
        String splitsArgs = "";
        for (File f : splits) {
            if (f.getName().endsWith(APEX_SUFFIX)) {
                ApexInfo apexInfo = retrieveApexInfo(f, device.getDeviceDescriptor());
                mTestApexInfoList.add(apexInfo);
            }
            if (f.getName().endsWith(APK_SUFFIX)) {
                mApkToInstall.add(parsePackageName(f, device.getDeviceDescriptor()));
            }
            if (!splitsArgs.isEmpty()) {
                splitsArgs += ":" + f.getAbsolutePath();
            } else {
                splitsArgs += f.getAbsolutePath();
            }
        }
        return splitsArgs;
    }

    /**
     * Checks if the input files contain any persistent apk.
     *
     * @param testAppFileNames The list of the file names of the modules to install
     * @param device The test device
     * @param buildInfo build artifact information
     * @return <code>true</code> if the input files contains a persistent apk module.
     */
    protected boolean containsPersistentApk(
            List<String> testAppFileNames, ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        for (String moduleFileName : testAppFileNames) {
            if (moduleFileName.endsWith(APK_SUFFIX) &&
                isPersistentApk(moduleFileName, device, buildInfo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an apk is a persistent apk.
     *
     * @param filename The apk module file to check
     * @param device The test device
     * @param buildInfo build artifact information
     * @return <code>true</code> if this is a persistent apk module.
     */
    protected boolean isPersistentApk(String filename, ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        File moduleFile = getLocalPathForFilename(buildInfo, filename, device);
        PackageInfo pkgInfo =
            device.getAppPackageInfo(parsePackageName(moduleFile, device.getDeviceDescriptor()));
        return pkgInfo.isPersistentApp();
    }

    /**
     * Collects apex info from the apex modules for activation check.
     *
     * @param testAppFileNames The list of the file names of the modules to install
     * @param device The test device
     * @param buildInfo build artifact information
     * @return a list containing the apexinfo of the apex modules in the input file lists
     */
    protected List<ApexInfo> collectApexInfoFromApexModules(
            List<String> testAppFileNames, ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError {
        List<ApexInfo> apexInfoList = new ArrayList<>();
        for (String appFilename : getTestsFileName()) {
            File appFile = getLocalPathForFilename(buildInfo, appFilename, device);
            if (isApex(appFile)) {
                ApexInfo apexInfo = retrieveApexInfo(appFile, device.getDeviceDescriptor());
                apexInfoList.add(apexInfo);
            }
        }
        return apexInfoList;
    }

    @VisibleForTesting
    protected String getBundletoolFileName() {
        return mBundletoolFilename;
    }

    @VisibleForTesting
    protected BundletoolUtil getBundletoolUtil() {
        return mBundletoolUtil;
    }

    @VisibleForTesting
    protected List<String> getApkInstalled() {
        return mApkInstalled;
    }
}

