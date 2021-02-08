/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Run tests if the device meets both of the following conditions:
 *
 * <ul>
 *   <li>The device shipped with the {@code min-api-level} or later.
 *   <li>The vendor image implemented the features for the {@code min-api-level} or later.
 * </ul>
 */
public class ShippingApiLevelModuleController extends BaseModuleController {

    private static final String SHIPPING_API_LEVEL_PROP = "ro.product.first_api_level";
    private static final String VENDOR_API_LEVEL_PROP = "ro.vndk.version";

    // Device may define the properties to show the launching api level information.
    // Some tests are not available for the upgrading devices. Using the properties in the
    // API_LEVEL_PROPS list, check the correct shipping API level for a device.
    // Empty value for the properties means the API level is "current".
    private static final String[] API_LEVEL_PROPS = {
        SHIPPING_API_LEVEL_PROP, VENDOR_API_LEVEL_PROP,
    };

    @Option(name = "min-api-level", description = "The minimum api-level on which tests will run.")
    private Integer mMinApiLevel = 0;

    @Override
    public RunStrategy shouldRun(IInvocationContext context) {
        for (ITestDevice device : context.getDevices()) {
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }
            try {
                for (String prop : API_LEVEL_PROPS) {
                    long apiLevel = device.getIntProperty(prop, 10000); // 10000 means "current"
                    if (apiLevel < mMinApiLevel) {
                        CLog.d(
                                "Skipping module %s because API Level %d from %s is less than %d.",
                                getModuleName(), apiLevel, prop, mMinApiLevel);
                        return RunStrategy.FULL_MODULE_BYPASS;
                    }
                }
            } catch (DeviceNotAvailableException e) {
                CLog.e("Couldn't check API Levels on %s", device.getSerialNumber());
                CLog.e(e);
            }
        }
        return RunStrategy.RUN;
    }
}
