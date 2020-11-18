/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tradefed.monitoring;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LabResourceDeviceMonitorTest {

    private LabResourceDeviceMonitor mLabResourceDeviceMonitor;

    @Before
    public void setUp() {
        mLabResourceDeviceMonitor = new LabResourceDeviceMonitor();
    }

    @Test
    public void testServerStartAndShutdown() {
        Assert.assertFalse(
                "server should be empty before monitor run",
                mLabResourceDeviceMonitor.getServer().isPresent());
        mLabResourceDeviceMonitor.run();
        Assert.assertTrue(
                "server should present after monitor run",
                mLabResourceDeviceMonitor.getServer().isPresent());
        Assert.assertEquals(
                LabResourceDeviceMonitor.DEFAULT_PORT,
                mLabResourceDeviceMonitor.getServer().get().getPort());
        mLabResourceDeviceMonitor.stop();
        Assert.assertTrue(
                "server should be shutdown after monitor stop",
                mLabResourceDeviceMonitor.getServer().get().isShutdown());
    }
}
