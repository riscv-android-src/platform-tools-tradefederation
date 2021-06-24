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
 * Run tests if the device meets the following conditions:
 *
 * <ul>
 *   <li>If {@code min-api-level} is defined:
 *       <ul>
 *         <li>The device shipped with the {@code min-api-level} or later.
 *       </ul>
 *   <li>If {@code vsr-min-api-level} is defined:
 *       <ul>
 *         <li>The device shipped with the {@code vsr-min-api-level} or later.
 *         <li>The vendor image implemented the features for the {@code vsr-min-api-level} or later.
 *       </ul>
 * </ul>
 */
public class ShippingApiLevelModuleController extends BaseModuleController {

    private static final String SYSTEM_SHIPPING_API_LEVEL_PROP = "ro.product.first_api_level";
    private static final String SYSTEM_API_LEVEL_PROP = "ro.build.version.sdk";
    private static final String VENDOR_SHIPPING_API_LEVEL_PROP = "ro.board.first_api_level";
    private static final String VENDOR_API_LEVEL_PROP = "ro.board.api_level";
    private static final long API_LEVEL_CURRENT = 10000;

    @Option(
            name = "min-api-level",
            description = "The minimum shipping api-level of the device on which tests will run.")
    private Integer mMinApiLevel = 0;

    @Option(
            name = "vsr-min-api-level",
            description =
                    "The minimum api-level on which tests will run. Both the shipping api-level of"
                        + " the device and the vendor api-level must be greater than or equal to"
                        + " the vsr-min-api-level to run the tests.")
    private Integer mVsrMinApiLevel = 0;

    /**
     * Compares the API level from the {@code minApiLevel} and decide if the test should run or not.
     *
     * @param device the {@link ITestDevice}.
     * @param apiLevelprop the name of a property that has the API level to compare with the {@code
     *     minApiLevel}.
     * @param fallbackProp the name of a property that is used when the {@code apiLevelprop} is not
     *     defined.
     * @param minApiLevel the minimum api level on which the test will run.
     * @return {@code true} if the api level is equal to or greater than the {@code minApiLevel}.
     *     Otherwise, {@code false}.
     * @throws DeviceNotAvailableException
     */
    private boolean shouldRunTestWithApiLevel(
            ITestDevice device, String apiLevelprop, String fallbackProp, int minApiLevel)
            throws DeviceNotAvailableException {
        String prop = apiLevelprop;
        long apiLevel = device.getIntProperty(prop, API_LEVEL_CURRENT);
        if (apiLevel == API_LEVEL_CURRENT) {
            prop = fallbackProp;
            apiLevel = device.getIntProperty(prop, API_LEVEL_CURRENT);
        }
        if (apiLevel < minApiLevel) {
            CLog.d(
                    "Skipping module %s because API Level %d from %s is less than %d.",
                    getModuleName(), apiLevel, prop, minApiLevel);
            return false;
        }
        return true;
    }

    /**
     * Method to decide if the module should run or not.
     *
     * @param context the {@link IInvocationContext} of the module
     * @return {@link RunStrategy#RUN} if the module should run, {@link
     *     RunStrategy#FULL_MODULE_BYPASS} otherwise.
     * @throws RuntimeException if device is not available
     */
    @Override
    public RunStrategy shouldRun(IInvocationContext context) {
        for (ITestDevice device : context.getDevices()) {
            if (device.getIDevice() instanceof StubDevice) {
                continue;
            }
            try {
                // Check system shipping api level against the min-api-level.
                // The base property to see the shipping api level of the device is the
                // "ro.product.first_api_level". If it is not defined, the current api level will be
                // read from the "ro.build.version.sdk"
                if (!shouldRunTestWithApiLevel(
                        device,
                        SYSTEM_SHIPPING_API_LEVEL_PROP,
                        SYSTEM_API_LEVEL_PROP,
                        mMinApiLevel)) {
                    return RunStrategy.FULL_MODULE_BYPASS;
                }
                // Check system shipping api level against the vsr-min-api-level.
                if (!shouldRunTestWithApiLevel(
                        device,
                        SYSTEM_SHIPPING_API_LEVEL_PROP,
                        SYSTEM_API_LEVEL_PROP,
                        mVsrMinApiLevel)) {
                    return RunStrategy.FULL_MODULE_BYPASS;
                }
                // vsr-min-api-level also requires to check the api level of the vendor
                // implementation.
                // If "ro.board.api_level" is not defined, read "ro.board.first_api_level" instead.
                if (!shouldRunTestWithApiLevel(
                        device,
                        VENDOR_API_LEVEL_PROP,
                        VENDOR_SHIPPING_API_LEVEL_PROP,
                        mVsrMinApiLevel)) {
                    return RunStrategy.FULL_MODULE_BYPASS;
                }
            } catch (DeviceNotAvailableException e) {
                CLog.e("Couldn't check API Levels on %s", device.getSerialNumber());
                CLog.e(e);
                throw new RuntimeException(e);
            }
        }
        return RunStrategy.RUN;
    }
}
