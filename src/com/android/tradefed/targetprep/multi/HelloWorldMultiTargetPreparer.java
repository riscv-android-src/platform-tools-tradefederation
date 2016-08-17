/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.targetprep.multi;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.Map;
import java.util.Map.Entry;

/**
 * An example implementation of a {@link IMultiTargetPreparer}.
 */
public class HelloWorldMultiTargetPreparer implements IMultiTargetPreparer {

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(Map<ITestDevice, IBuildInfo> deviceBuildInfo) throws TargetSetupError {
        if (deviceBuildInfo.entrySet().size() != 2) {
            throw new TargetSetupError("The HelloWorldMultiTargetPreparer assumes 2 devices only.");
        }
        // This would be the perfect place to do setup that requires multiple devices like
        // syncing two devices, etc.
        for (Entry<ITestDevice, IBuildInfo> entry : deviceBuildInfo.entrySet()) {
            CLog.i("Hello World! multi preparer '%s' with build id '%s'",
                    entry.getKey().getSerialNumber(), entry.getValue().getBuildId());
        }
    }

    @Override
    public void tearDown(Map<ITestDevice, IBuildInfo> deviceBuildInfo, Throwable e)
            throws DeviceNotAvailableException {
        for (Entry<ITestDevice, IBuildInfo> entry : deviceBuildInfo.entrySet()) {
            CLog.i("Hello World! multi tear down '%s' with build id '%s'",
                    entry.getKey().getSerialNumber(), entry.getValue().getBuildId());
        }
    }
}
