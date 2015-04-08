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
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A {@link ITargetPreparer} that installs one or more apps from a
 * {@link IDeviceBuildInfo#getTestsDir()} folder onto device.
 * <p>
 * This preparer will look in alternate directories if the tests zip does not exist or does not
 * contain the required apk. The search will go in order from the last alternative dir specified to
 * the first.
 * </p>
 */
@OptionClass(alias = "tests-zip-app")
public class TestAppInstallSetup implements ITargetCleaner, IAbiReceiver {

    private static final String LOG_TAG = TestAppInstallSetup.class.getSimpleName();

    @Option(name = "test-file-name",
            description = "the name of a test zip file to install on device. Can be repeated.",
            importance = Importance.IF_UNSET)
    private Collection<String> mTestFileNames = new ArrayList<String>();

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = null;

    @Option(name = "install-arg",
            description = "Additional arguments to be passed to install command, "
                    + "including leading dash, e.g. \"-d\"")
    private Collection<String> mInstallArgs = new ArrayList<>();

    @Option(name = "cleanup-apks",
            description = "Whether apks installed should be uninstalled after test. Note that the "
                    + "preparer does not verify if the apks are successfully removed.")
    private boolean mCleanup = false;

    @Option(name = "alt-dir",
            description = "Alternate directory to look for the apk if the apk is not in the tests "
                    + "zip file. For each alternate dir, will look in //, //data/app, //DATA/app, "
                    + "and //DATA/app/apk_name/. Can be repeated. Look for apks in last alt-dir "
                    + "first.")
    private List<File> mAltDirs = new ArrayList<>();

    private IAbi mAbi = null;

    private List<String> mPackagesInstalled = new ArrayList<>();

    /**
     * Adds a file to the list of apks to install
     *
     * @param fileName
     */
    public void addTestFileName(String fileName) {
        mTestFileNames.add(fileName);
    }

    /**
     * Resolve the actual apk path based on testing artifact information inside build info.
     *
     * @param buildInfo build artifact information
     * @param apkFileName filename of the apk to install
     * @return a {@link File} representing the physical apk file on host or {@code null} if the
     *     file does not exist.
     */
    protected File getLocalPathForFilename(IBuildInfo buildInfo, String apkFileName)
            throws TargetSetupError {
        String apkBase = apkFileName.split("\\.")[0];

        List<File> dirs = new ArrayList<>();
        for (File dir : mAltDirs) {
            dirs.add(dir);
            // Files in tests zip file will be in DATA/app/ or DATA/app/apk_name
            dirs.add(FileUtil.getFileForPath(dir, "DATA", "app"));
            dirs.add(FileUtil.getFileForPath(dir, "DATA", "app", apkBase));
            // Files in out dir will bein in uses data/app/apk_name
            dirs.add(FileUtil.getFileForPath(dir, "data", "app", apkBase));
        }

        // Add test dirs last so they will be first when list is reversed
        if (buildInfo instanceof IDeviceBuildInfo) {
            File testsDir = ((IDeviceBuildInfo)buildInfo).getTestsDir();
            if (testsDir != null && testsDir.exists()) {
                dirs.add(FileUtil.getFileForPath(testsDir, "DATA", "app"));
                dirs.add(FileUtil.getFileForPath(testsDir, "DATA", "app", apkBase));
            }
        }
        if (dirs.isEmpty()) {
            throw new TargetSetupError(
                    "Provided buildInfo does not contain a valid tests directory and no " +
                    "alternative directories were provided");
        }

        Collections.reverse(dirs);
        for (File dir : dirs) {
            File testAppFile = new File(dir, apkFileName);
            if (testAppFile.exists()) {
                return testAppFile;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (mTestFileNames.size() == 0) {
            Log.i(LOG_TAG, "No test apps to install, skipping");
            return;
        }
        for (String testAppName : mTestFileNames) {
            File testAppFile = getLocalPathForFilename(buildInfo, testAppName);
            if (testAppFile == null) {
                throw new TargetSetupError(
                    String.format("Could not find test app %s directory in extracted tests.zip",
                            testAppFile));
            }
            // resolve abi flags
            if (mAbi != null && mForceAbi != null) {
                throw new IllegalStateException("cannot specify both abi flags");
            }
            String abiName = null;
            if (mAbi != null) {
                abiName = mAbi.getName();
            } else if (mForceAbi != null) {
                abiName = AbiFormatter.getDefaultAbi(device, mForceAbi);
            }
            if (abiName != null) {
                mInstallArgs.add(String.format("--abi %s", abiName));
            }
            String result = device.installPackage(testAppFile, true,
                    mInstallArgs.toArray(new String[]{}));
            if (result != null) {
                throw new TargetSetupError(
                        String.format("Failed to install %s on %s. Reason: '%s'", testAppName,
                                device.getSerialNumber(), result));
            }
            if (mCleanup) {
                AaptParser parser = AaptParser.parse(testAppFile);
                if (parser == null) {
                    throw new TargetSetupError("apk installed but AaptParser failed");
                }
                mPackagesInstalled.add(parser.getPackageName());
            }
        }
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mCleanup && !(e instanceof DeviceNotAvailableException)) {
            for (String packageName : mPackagesInstalled) {
                String msg = device.uninstallPackage(packageName);
                if (msg != null) {
                    Log.w(LOG_TAG, String.format("error uninstalling package '%s': %s",
                            packageName, msg));
                }
            }
        }
    }

    /**
     * Set an alternate directory.
     */
    public void setAltDir(File altDir) {
        mAltDirs.add(altDir);
    }
}
