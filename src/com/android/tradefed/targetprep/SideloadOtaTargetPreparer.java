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
package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;

/**
 * A target preparer that performs sideload of a specified OTA package, applies the package, waits
 * for device to boot up, and injects the device build properties to use as build info
 *
 * <p>This target preparer assumes that the device will be in regular adb mode when started, and
 * will ensure that the device exits in the same mode but with the newer build applied. Any
 * unexpected device state transition during the process will be reported as {@link
 * TargetSetupError}, and same applies to any OTA sideload error detected.
 */
@OptionClass(alias = "sideload-ota")
public class SideloadOtaTargetPreparer extends DeviceBuildInfoInjector {

    private static final String SIDELOAD_CMD = "sideload";
    // the timeout for state transition from sideload finishes to recovery mode, not making this
    // an option because it should be a transient state: 10s is already very generous
    private static final long POST_SIDELOAD_TRANSITION_TIMEOUT = 10 * 1000;

    @Option(name = "sideload-ota-package", description = "the OTA package to be sideloaded")
    private File mSideloadOtaPackage = null;

    @Option(
        name = "sideload-timeout",
        description = "timeout for sideloading the OTA package",
        isTimeVal = true
    )
    // defaults to 10m: assuming USB 2.0 transfer speed, concurrency and some buffer
    private long mSideloadTimeout = 10 * 60 * 1000;

    @Option(
        name = "inject-build-info",
        description =
                "whether build info should be injected "
                        + "based on device attributes after sideloading"
    )
    private boolean mInjectBuildInfo = true;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mSideloadOtaPackage == null) {
            CLog.i("No sideload file provided, assuming no-op; skipping ...");
            return;
        }
        device.rebootIntoSideload();
        String filePath = mSideloadOtaPackage.getAbsolutePath();
        CLog.d("Sideloading package from %s ...", filePath);
        device.executeAdbCommand(mSideloadTimeout, SIDELOAD_CMD, filePath);
        // after applying sideload, device should transition to recovery mode
        device.waitForDeviceInRecovery(POST_SIDELOAD_TRANSITION_TIMEOUT);
        // now reboot and wait for the device to become available
        device.reboot();
        // calling this last because we want to inject device side build info after device boots up
        if (mInjectBuildInfo) {
            super.setUp(device, buildInfo);
        }
    }
}
