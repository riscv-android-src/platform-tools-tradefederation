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
package com.android.tradefed.testtype;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * JUnit4 test runner that also accommodate {@link IDeviceTest}.
 * Should be specify above JUnit4 Test with the RunWith annotation.
 */
public class DeviceJUnit4ClassRunner extends BlockJUnit4ClassRunner implements IDeviceTest,
        IBuildReceiver, IAbiReceiver {
    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private IAbi mAbi;

    public DeviceJUnit4ClassRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    /**
     * We override createTest in order to set the device.
     */
    @Override
    protected Object createTest() throws Exception {
        Object testObj = super.createTest();
        if (testObj instanceof IDeviceTest) {
            if (mDevice == null) {
                throw new IllegalArgumentException("Missing device");
            }
            ((IDeviceTest)testObj).setDevice(mDevice);
        }
        if (testObj instanceof IBuildReceiver) {
            if (mBuildInfo == null) {
                throw new IllegalArgumentException("Missing build information");
            }
            ((IBuildReceiver)testObj).setBuild(mBuildInfo);
        }
        // We are more flexible about abi information since not always available.
        if (testObj instanceof IAbiReceiver) {
            ((IAbiReceiver)testObj).setAbi(mAbi);
        }
        return testObj;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
