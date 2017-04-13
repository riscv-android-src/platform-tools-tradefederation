/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.targetprep.suite;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Installs specified APKs for Suite configuration: either from $ANDROID_TARGET_OUT_TESTCASES
 * variable or the ROOT_DIR in build info.
 */
@OptionClass(alias = "apk-installer")
public class SuiteApkInstaller extends TestAppInstallSetup {

    private static final String ANDROID_TARGET_TESTCASES = "ANDROID_TARGET_OUT_TESTCASES";
    private static final String ROOT_DIR = "ROOT_DIR";

    @VisibleForTesting
    String getEnvVariable() {
        return System.getenv(ANDROID_TARGET_TESTCASES);
    }

    /**
     * Try to find a path for the base root tests directory.
     *
     * @param buildInfo the {@link IBuildInfo} describing the build.
     * @return a {@link File} pointing to the directory of the root tests dir.
     * @throws FileNotFoundException if no root dir is defined.
     */
    @VisibleForTesting
    protected File getTestsDir(IBuildInfo buildInfo) throws FileNotFoundException {
        String testcasesPath = getEnvVariable();
        if (testcasesPath != null) {
            return new File(testcasesPath);
        }
        if (buildInfo.getBuildAttributes().get(ROOT_DIR) != null) {
            return new File(buildInfo.getBuildAttributes().get(ROOT_DIR));
        }
        throw new FileNotFoundException(
                String.format(
                        "$%s is undefined, and no %s was found.",
                        ANDROID_TARGET_TESTCASES, ROOT_DIR));
    }

    /** {@inheritDoc} */
    @Override
    protected File getLocalPathForFilename(
            IBuildInfo buildInfo, String apkFileName, ITestDevice device) throws TargetSetupError {
        File apkFile = null;
        try {
            // use findFile because there may be a subdirectory
            apkFile = FileUtil.findFile(getTestsDir(buildInfo), apkFileName);
            if (apkFile == null || !apkFile.isFile()) {
                throw new FileNotFoundException();
            }
        } catch (FileNotFoundException e) {
            throw new TargetSetupError(
                    String.format("%s not found", apkFileName), e, device.getDeviceDescriptor());
        }
        return apkFile;
    }
}
