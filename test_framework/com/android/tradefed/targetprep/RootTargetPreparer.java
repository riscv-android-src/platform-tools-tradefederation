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
package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;

/**
 * Target preparer that performs "adb root" or "adb unroot" based on option "force-root".
 *
 * <p>Will restore back to original root state on tear down.
 */
@OptionClass(alias = "root-preparer")
public class RootTargetPreparer extends BaseTargetPreparer {

    private boolean mWasRoot = false;

    @Option(
            name = "force-root",
            description =
                    "Force the preparer to enable adb root if set to true. Otherwise, disable adb "
                            + "root during setup.")
    private boolean mForceRoot = true;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // Ignore setUp if it's a stub device, since there is no real device to set up.
        if (device.getIDevice() instanceof StubDevice) {
            return;
        }
        mWasRoot = device.isAdbRoot();
        if (!mWasRoot && mForceRoot && !device.enableAdbRoot()) {
            throw new TargetSetupError("Failed to adb root device", device.getDeviceDescriptor());
        } else if (mWasRoot && !mForceRoot && !device.disableAdbRoot()) {
            throw new TargetSetupError("Failed to adb unroot device", device.getDeviceDescriptor());
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        // Ignore tearDown if it's a stub device, since there is no real device to clean.
        if (device.getIDevice() instanceof StubDevice) {
            return;
        }
        if (!mWasRoot && mForceRoot) {
            device.disableAdbRoot();
        } else if (mWasRoot && !mForceRoot) {
            device.enableAdbRoot();
        }
    }
}
