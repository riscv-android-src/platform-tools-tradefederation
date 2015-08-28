/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.media.tests;

import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;

/**
 * Camera2 stress test
 * Since Camera stress test can drain the battery seriously. Need to split
 * the test suite into separate test invocation for each test method.
 * <p/>
 */
@OptionClass(alias = "camera2-stress")
public class Camera2StressTest extends CameraTestBase {

    public Camera2StressTest() {
        setTestPackage("com.google.android.camera");
        setTestClass("com.android.camera.stress.CameraStressTest");
        setTestRunner("android.test.InstrumentationTestRunner");
        setRuKey("CameraAppStress");
        setTestTimeoutMs(6 * 60 * 60 * 1000);   // 6 hours
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        runInstrumentationTest(listener);
    }
}
