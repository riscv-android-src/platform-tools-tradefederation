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

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.device.PackageInfo;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.suite.SuiteApkInstaller;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.BundletoolUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

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

    private List<ApexInfo> mTestApexInfoList = new ArrayList<>();
    private Set<String> mApkToInstall = new LinkedHashSet<>();
    private List<String> mApkInstalled = new ArrayList<>();
    private List<String> mSplitsInstallArgs = new ArrayList<>();
    private BundletoolUtil mBundletoolUtil;
    private String mDeviceSpecFilePath = "";

    @Option(name = "bundletool-file-name", description = "The file name of the bundletool jar.")
    private String mBundletoolFilename;

    @Option(
        name = "apex-staging-wait-time",
        description = "The time in ms to wait for apex staged session ready.",
        isTimeVal = true
    )
    private long mApexStagingWaitTime = 1 * 60 * 1000;

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        setTestInformation(testInfo);
        ITestDevice device = testInfo.getDevice();

        if (getTestsFileName().isEmpty()) {
            CLog.i("No apk/apex module file to install. Skipping.");
            return;
        }

        cleanUpStagedAndActiveSession(device);

        List<String> testAppFileNames = getModulesToInstall(testInfo);
        if (testAppFileNames.isEmpty()) {
            CLog.i("No modules are preloaded on the device, so no modules will be installed.");
            return;
        }
        if (containsApks(testAppFileNames)) {
            installUsingBundleTool(testInfo, testAppFileNames);
            if (mTestApexInfoList.isEmpty()) {
                CLog.i("No Apex module in the train. Skipping reboot.");
                return;
            } else {
                RunUtil.getDefault().sleep(mApexStagingWaitTime);
                device.reboot();
            }
        } else {
            installer(testInfo, testAppFileNames);
            if (containsApex(testAppFileNames)
                    || containsPersistentApk(testAppFileNames, testInfo)) {
                RunUtil.getDefault().sleep(mApexStagingWaitTime);
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
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        if (e instanceof DeviceNotAvailableException) {
            CLog.e("Device %s is not available. Teardown() skipped.", device.getSerialNumber());
            return;
        } else {
            if (mTestApexInfoList.isEmpty() && getApkInstalled().isEmpty()) {
                super.tearDown(testInfo, e);
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
     * Initializes the bundletool util for this class.
     *
     * @param testInfo the {@link TestInformation} for the invocation.
     * @throws TargetSetupError if bundletool cannot be found.
     */
    private void initBundletoolUtil(TestInformation testInfo) throws TargetSetupError {
        if (mBundletoolUtil != null) {
            return;
        }
        File bundletoolJar = getLocalPathForFilename(testInfo, getBundletoolFileName());
        if (bundletoolJar == null) {
            throw new TargetSetupError(
                    String.format("Failed to find bundletool jar %s.", getBundletoolFileName()),
                    testInfo.getDevice().getDeviceDescriptor());
        }
        mBundletoolUtil = new BundletoolUtil(bundletoolJar);
    }

    /**
     * Initializes the path to the device spec file.
     *
     * @param device the {@link ITestDevice} to install the train.
     * @return String path to the device spec.
     * @throws TargetSetupError if fails to generate the device spec file.
     */
    private void initDeviceSpecFilePath(ITestDevice device) throws TargetSetupError {
        if (!mDeviceSpecFilePath.equals("")) {
            return;
        }
        try {
            mDeviceSpecFilePath = getBundletoolUtil().generateDeviceSpecFile(device);
        } catch (IOException e) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to generate device spec file on %s.", device.getSerialNumber()),
                    e,
                    device.getDeviceDescriptor());
        }
    }

    /**
     * Extracts and returns splits for the specified apks.
     *
     * @param testInfo the {@link TestInformation}
     * @param apksName The name of the apks file to extract splits from.
     * @return a File[] containing the splits.
     * @throws TargetSetupError if bundletool cannot be found or device spec file fails to generate.
     */
    private File[] getSplitsForApks(TestInformation testInfo, String apksName)
            throws TargetSetupError {
        initBundletoolUtil(testInfo);
        initDeviceSpecFilePath(testInfo.getDevice());
        File moduleFile = getLocalPathForFilename(testInfo, apksName);
        File splitsDir =
                getBundletoolUtil()
                        .extractSplitsFromApks(
                                moduleFile,
                                mDeviceSpecFilePath,
                                testInfo.getDevice(),
                                testInfo.getBuildInfo());
        if (splitsDir == null) {
            return null;
        }
        return splitsDir.listFiles();
    }

    /**
     * Gets the modules that should be installed on the train, based on the modules preloaded on the
     * device. Modules that are not preloaded will not be installed.
     *
     * @param testInfo the {@link TestInformation}
     * @return List<String> of the modules that should be installed on the device.
     * @throws DeviceNotAvailableException when device is not available.
     * @throws TargetSetupError when mandatory modules are not installed, or module cannot be
     *     installed.
     */
    public List<String> getModulesToInstall(TestInformation testInfo)
            throws DeviceNotAvailableException, TargetSetupError {
        // Get all preloaded modules for the device.
        ITestDevice device = testInfo.getDevice();
        Set<String> installedPackages = new HashSet<>(device.getInstalledPackageNames());
        Set<ApexInfo> installedApexes = new HashSet<>(device.getActiveApexes());
        for (ApexInfo installedApex : installedApexes) {
            installedPackages.add(installedApex.name);
        }
        List<String> moduleFileNames = getTestsFileName();
        List<String> moduleNamesToInstall = new ArrayList<>();
        for (String moduleFileName : moduleFileNames) {
            File moduleFile = getLocalPathForFilename(testInfo, moduleFileName);
            if (moduleFile == null) {
                throw new TargetSetupError(
                        String.format("%s not found.", moduleFileName),
                        device.getDeviceDescriptor());
            }
            String modulePackageName = "";
            if (moduleFile.getName().endsWith(SPLIT_APKS_SUFFIX)) {
                File[] splits = getSplitsForApks(testInfo, moduleFileName);
                if (splits == null) {
                    // Bundletool failed to extract splits.
                    CLog.w(
                            "Apks %s is not available on device %s and will not be installed.",
                            moduleFileName, mDeviceSpecFilePath);
                    continue;
                }
                modulePackageName = parsePackageName(splits[0], device.getDeviceDescriptor());
            } else {
                modulePackageName = parsePackageName(moduleFile, device.getDeviceDescriptor());
            }
            if (installedPackages.contains(modulePackageName)) {
                CLog.i("Found preloaded module for %s.", modulePackageName);
                moduleNamesToInstall.add(moduleFileName);
                installedPackages.remove(modulePackageName);
            } else {
                CLog.i(
                        "The module package %s is not preloaded on the device but is included in "
                                + "the train.",
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
    protected void installer(TestInformation testInfo, List<String> testAppFileNames)
            throws TargetSetupError, DeviceNotAvailableException {
        if (containsApex(testAppFileNames)) {
            mTestApexInfoList = collectApexInfoFromApexModules(testAppFileNames, testInfo);
        }
        if (containsPersistentApk(testAppFileNames, testInfo)) {
            // When there is a persistent apk in the train, use '--staged' to install full train
            // Otherwise, do normal install without '--staged'
            installTrain(testInfo, testAppFileNames, new String[] {"--staged"});
            return;
        }
        installTrain(testInfo, testAppFileNames, new String[] {});
    }

    /**
     * Attempts to install a mainline train containing apex on the device.
     *
     * @param testInfo the {@link TestInformation}
     * @param moduleFilenames List of String. The list of filenames of the mainline modules to be
     *     installed.
     */
    protected void installTrain(
            TestInformation testInfo, List<String> moduleFilenames, final String[] extraArgs)
            throws TargetSetupError, DeviceNotAvailableException {
        // TODO(b/137883918):remove after new adb is released, which supports installing
        // single apk/apex using 'install-multi-package'
        ITestDevice device = testInfo.getDevice();
        if (moduleFilenames.size() == 1) {
            String moduleFileName = moduleFilenames.get(0);
            File module = getLocalPathForFilename(testInfo, moduleFileName);
            device.installPackage(module, true, extraArgs);
            if (moduleFileName.endsWith(APK_SUFFIX)) {
                String packageName = parsePackageName(module, device.getDeviceDescriptor());
                mApkInstalled.add(packageName);
            }
            return;
        }

        List<String> apkPackageNames = new ArrayList<>();
        List<String> trainInstallCmd = new ArrayList<>();

        trainInstallCmd.add(TRAIN_WITH_APEX_INSTALL_OPTION);
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                trainInstallCmd.add(arg);
            }
        }

        for (String fileName : moduleFilenames) {
            File moduleFile = getLocalPathForFilename(testInfo, fileName);
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
     * @param testInfo the {@link TestInformation}
     * @param testAppFileNames the filenames of the preloaded modules to install.
     */
    protected void installUsingBundleTool(TestInformation testInfo, List<String> testAppFileNames)
            throws TargetSetupError, DeviceNotAvailableException {
        initBundletoolUtil(testInfo);
        initDeviceSpecFilePath(testInfo.getDevice());

        if (testAppFileNames.size() == 1) {
            // Installs single .apks module.
            installSingleModuleUsingBundletool(
                    testInfo, mDeviceSpecFilePath, testAppFileNames.get(0));
        } else {
            installMultipleModuleUsingBundletool(testInfo, mDeviceSpecFilePath, testAppFileNames);
        }

        mApkInstalled.addAll(mApkToInstall);
    }

    /**
     * Attempts to install a single mainline module(.apks) using bundletool.
     *
     * @param testInfo the {@link TestInformation}
     * @param deviceSpecFilePath the spec file of the test device
     * @param apksName the file name of the .apks
     */
    private void installSingleModuleUsingBundletool(
            TestInformation testInfo, String deviceSpecFilePath, String apksName)
            throws TargetSetupError, DeviceNotAvailableException {
        File apks = getLocalPathForFilename(testInfo, apksName);
        // Rename the extracted files and add the file to filename list.
        File[] splits = getSplitsForApks(testInfo, apks.getName());
        ITestDevice device = testInfo.getDevice();
        if (splits.length == 0) {
            throw new TargetSetupError(
                    String.format("Extraction for %s failed. No apk/apex is extracted.", apksName),
                    device.getDeviceDescriptor());
        }
        String splitFileName = splits[0].getName();
        // Install .apks that contain apex module.
        if (containsApex(Arrays.asList(splitFileName))) {
            super.installer(testInfo, Arrays.asList(splitFileName));
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
     * @param testInfo the {@link TestInformation}
     * @param deviceSpecFilePath the spec file of the test device
     * @param testAppFileNames the list of preloaded modules to install.
     */
    private void installMultipleModuleUsingBundletool(
            TestInformation testInfo, String deviceSpecFilePath, List<String> testAppFileNames)
            throws TargetSetupError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        for (String moduleFileName : testAppFileNames) {
            File moduleFile = getLocalPathForFilename(testInfo, moduleFileName);
            if (moduleFileName.endsWith(SPLIT_APKS_SUFFIX)) {
                File[] splits = getSplitsForApks(testInfo, moduleFileName);
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
     * @param testInfo The {@link TestInformation}
     * @return <code>true</code> if the input files contains a persistent apk module.
     */
    protected boolean containsPersistentApk(List<String> testAppFileNames, TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        for (String moduleFileName : testAppFileNames) {
            if (isPersistentApk(moduleFileName, testInfo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an apk is a persistent apk.
     *
     * @param filename The apk module file to check
     * @param testInfo The {@link TestInformation}
     * @return <code>true</code> if this is a persistent apk module.
     */
    protected boolean isPersistentApk(String filename, TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        if (!filename.endsWith(APK_SUFFIX)) {
            return false;
        }
        File moduleFile = getLocalPathForFilename(testInfo, filename);
        PackageInfo pkgInfo =
                testInfo.getDevice()
                        .getAppPackageInfo(
                                parsePackageName(
                                        moduleFile, testInfo.getDevice().getDeviceDescriptor()));
        return pkgInfo.isPersistentApp();
    }

    /**
     * Collects apex info from the apex modules for activation check.
     *
     * @param testAppFileNames The list of the file names of the modules to install
     * @param testInfo The {@link TestInformation}
     * @return a list containing the apexinfo of the apex modules in the input file lists
     */
    protected List<ApexInfo> collectApexInfoFromApexModules(
            List<String> testAppFileNames, TestInformation testInfo) throws TargetSetupError {
        List<ApexInfo> apexInfoList = new ArrayList<>();
        for (String appFilename : getTestsFileName()) {
            File appFile = getLocalPathForFilename(testInfo, appFilename);
            if (isApex(appFile)) {
                ApexInfo apexInfo =
                        retrieveApexInfo(appFile, testInfo.getDevice().getDeviceDescriptor());
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
