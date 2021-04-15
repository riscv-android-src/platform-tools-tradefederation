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

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.testtype.suite.module.IModuleController.RunStrategy;
import com.android.tradefed.testtype.suite.ModuleDefinition;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ShippingApiLevelModuleController}. */
@RunWith(JUnit4.class)
public class ShippingApiLevelModuleControllerTest {
    private ShippingApiLevelModuleController mController;
    private IInvocationContext mContext;
    private ITestDevice mMockDevice;
    private IDevice mMockIDevice;
    private String mApiLevelProp;

    private static final String SHIPPING_API_LEVEL_PROP = "ro.product.first_api_level";
    private static final String VENDOR_API_LEVEL_PROP = "ro.vndk.version";

    @Before
    public void setUp() {
        mController = new ShippingApiLevelModuleController();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_ABI, "arm64-v8a");
        mContext.addInvocationAttribute(ModuleDefinition.MODULE_NAME, "module1");
        mMockIDevice = EasyMock.createMock(IDevice.class);
    }

    /**
     * min-api-level is higher than shipping api level. The test will be skipped. No need to check
     * vendor api level.
     */
    @Test
    public void testMinApiLevelHigherThanProductFirstApiLevel()
            throws DeviceNotAvailableException, ConfigurationException {
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(mMockIDevice);
        EasyMock.expect(mMockDevice.getIntProperty(SHIPPING_API_LEVEL_PROP, 10000)).andReturn(28L);
        EasyMock.replay(mMockDevice);
        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("min-api-level", "29");
        assertEquals(RunStrategy.FULL_MODULE_BYPASS, mController.shouldRunModule(mContext));
        EasyMock.verify(mMockDevice);
    }

    /**
     * min-api-level is equal to shipping api level but higher than vendor api level. The test will
     * be skipped.
     */
    @Test
    public void testMinApiLevelHigherThanVendorApiLevel()
            throws DeviceNotAvailableException, ConfigurationException {
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(mMockIDevice);
        EasyMock.expect(mMockDevice.getIntProperty(SHIPPING_API_LEVEL_PROP, 10000)).andReturn(29L);
        EasyMock.expect(mMockDevice.getIntProperty(VENDOR_API_LEVEL_PROP, 10000)).andReturn(28L);
        EasyMock.replay(mMockDevice);
        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("min-api-level", "29");
        assertEquals(RunStrategy.FULL_MODULE_BYPASS, mController.shouldRunModule(mContext));
        EasyMock.verify(mMockDevice);
    }

    /**
     * min-api-level is lower than shipping api level and no vendor api level defined. The test will
     * run.
     */
    @Test
    public void testMinApiLevelLowerThanProductFirstApiLevel()
            throws DeviceNotAvailableException, ConfigurationException {
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(mMockIDevice);
        EasyMock.expect(mMockDevice.getIntProperty(SHIPPING_API_LEVEL_PROP, 10000)).andReturn(28L);
        EasyMock.expect(mMockDevice.getIntProperty(VENDOR_API_LEVEL_PROP, 10000)).andReturn(10000L);
        EasyMock.replay(mMockDevice);
        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("min-api-level", "27");
        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
        EasyMock.verify(mMockDevice);
    }

    /**
     * min-api-level is lower than both shipping api level and vendor api level. The test will run.
     */
    @Test
    public void testMinApiLevelLowerThanProductAndVendorApiLevels()
            throws DeviceNotAvailableException, ConfigurationException {
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(mMockIDevice);
        EasyMock.expect(mMockDevice.getIntProperty(SHIPPING_API_LEVEL_PROP, 10000)).andReturn(28L);
        EasyMock.expect(mMockDevice.getIntProperty(VENDOR_API_LEVEL_PROP, 10000)).andReturn(27L);
        EasyMock.replay(mMockDevice);
        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("min-api-level", "27");
        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
        EasyMock.verify(mMockDevice);
    }

    /** no properties are defined. The test will run. */
    @Test
    public void testDeviceApiLevelNotFound()
            throws DeviceNotAvailableException, ConfigurationException {
        EasyMock.expect(mMockDevice.getIDevice()).andReturn(mMockIDevice);
        EasyMock.expect(mMockDevice.getIntProperty(SHIPPING_API_LEVEL_PROP, 10000))
                .andReturn(10000L);
        EasyMock.expect(mMockDevice.getIntProperty(VENDOR_API_LEVEL_PROP, 10000)).andReturn(10000L);
        EasyMock.replay(mMockDevice);
        OptionSetter setter = new OptionSetter(mController);
        setter.setOptionValue("min-api-level", "27");
        assertEquals(RunStrategy.RUN, mController.shouldRunModule(mContext));
        EasyMock.verify(mMockDevice);
    }
}
