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
package com.android.tradefed.invoker;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.IHostCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.util.IDisableable;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link InvocationExecution}. Tests for each individual interface of
 * InvocationExecution, integration tests for orders or calls should be in {@link
 * TestInvocationTest}.
 */
@RunWith(JUnit4.class)
public class InvocationExecutionTest {
    private InvocationExecution mExec;
    private IInvocationContext mContext;
    private IConfiguration mConfig;

    @Before
    public void setUp() {
        mExec = new InvocationExecution();
        mContext = new InvocationContext();
        mConfig = new Configuration("test", "test");
    }

    /** Test class for a target preparer class that also do host cleaner. */
    public interface ITargetHostCleaner extends ITargetPreparer, IHostCleaner {}

    /**
     * Test that {@link InvocationExecution#doCleanUp(IInvocationContext, IConfiguration,
     * Throwable)} properly use {@link IDisableable} to let an object run.
     */
    @Test
    public void testCleanUp() throws Exception {
        DeviceConfigurationHolder holder = new DeviceConfigurationHolder("default");
        ITargetHostCleaner cleaner = EasyMock.createMock(ITargetHostCleaner.class);
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        mContext.addAllocatedDevice("default", EasyMock.createMock(ITestDevice.class));
        EasyMock.expect(cleaner.isDisabled()).andReturn(false);
        cleaner.cleanUp(null, null);
        EasyMock.replay(cleaner);
        mExec.doCleanUp(mContext, mConfig, null);
        EasyMock.verify(cleaner);
    }

    /**
     * Test that {@link InvocationExecution#doCleanUp(IInvocationContext, IConfiguration,
     * Throwable)} properly use {@link IDisableable} to prevent an object from running.
     */
    @Test
    public void testCleanUp_disabled() throws Exception {
        DeviceConfigurationHolder holder = new DeviceConfigurationHolder("default");
        ITargetHostCleaner cleaner = EasyMock.createMock(ITargetHostCleaner.class);
        holder.addSpecificConfig(cleaner);
        mConfig.setDeviceConfig(holder);
        mContext.addAllocatedDevice("default", EasyMock.createMock(ITestDevice.class));
        EasyMock.expect(cleaner.isDisabled()).andReturn(true);
        // cleanUp call is not expected
        EasyMock.replay(cleaner);
        mExec.doCleanUp(mContext, mConfig, null);
        EasyMock.verify(cleaner);
    }
}
