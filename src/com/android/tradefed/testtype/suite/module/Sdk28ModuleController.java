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
package com.android.tradefed.testtype.suite.module;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * A module controller to check if a device is on SDK 28 (Android 9) or above. This is used to
 * workaround b/78780430, where on Android 8.1 armeabi-v7a devices, instrumentation will crash
 * because the target package was pre-opted for only arm64.
 */
public class Sdk28ModuleController extends BaseModuleController {

    private static final String SDK_VERSION_PROP = "ro.build.version.sdk";

    @Override
    public RunStrategy shouldRun(IInvocationContext context) {
        for (ITestDevice device : context.getDevices()) {
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }
            try {
                String sdkVersionString = device.getProperty(SDK_VERSION_PROP);
                int sdkVersion = -1;
                try {
                    if (sdkVersionString != null) {
                        sdkVersion = Integer.parseInt(sdkVersionString);
                    } else {
                        CLog.d("SDK version is null");
                    }
                } catch (NumberFormatException e) {
                    CLog.d(e.getMessage());
                }
                if (sdkVersion >= 28) {
                    continue;
                }
                CLog.d(
                        "Skipping module %s because SDK version %d is < 28.",
                        getModuleName(), sdkVersion);
                return RunStrategy.FULL_MODULE_BYPASS;
            } catch (DeviceNotAvailableException e) {
                CLog.e("Couldn't check SDK version on %s", device.getSerialNumber());
                CLog.e(e);
            }
        }
        return RunStrategy.RUN;
    }
}
