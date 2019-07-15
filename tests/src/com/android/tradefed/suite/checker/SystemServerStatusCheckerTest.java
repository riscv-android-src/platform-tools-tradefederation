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
package com.android.tradefed.suite.checker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;
import com.android.tradefed.util.ProcessInfo;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link SystemServerStatusChecker} */
@RunWith(JUnit4.class)
public class SystemServerStatusCheckerTest {

    private SystemServerStatusChecker mChecker;
    private ITestDevice mMockDevice;

    @Before
    public void setUp() {
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("SERIAL");
        mChecker =
                new SystemServerStatusChecker() {
                    @Override
                    protected long getCurrentTime() {
                        return 500L;
                    }
                };
    }

    /** Test that system checker pass if system_server didn't restart. */
    @Test
    public void testSystemServerProcessNotRestarted() throws Exception {
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server")))
                .andReturn(new ProcessInfo("system", 914, "system_server", 1559091922L))
                .times(2);
        EasyMock.replay(mMockDevice);
        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
        EasyMock.verify(mMockDevice);
    }

    /** Test that system checker fail if system_server crashed and didn't come back. */
    @Test
    public void testSystemServerProcessCrashed() throws Exception {
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server")))
                .andReturn(new ProcessInfo("system", 914, "system_server", 1559091922L));
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server"))).andReturn(null);
        EasyMock.replay(mMockDevice);
        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        StatusCheckerResult result = mChecker.postExecutionCheck(mMockDevice);
        assertEquals(CheckStatus.FAILED, result.getStatus());
        assertTrue(result.isBugreportNeeded());
        EasyMock.verify(mMockDevice);
    }

    /** Test that system checker fail if system_server restarted without device reboot. */
    @Test
    public void testSystemServerProcessRestartedWithoutDeviceReboot() throws Exception {
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server")))
                .andReturn(new ProcessInfo("system", 914, "system_server", 1559091922L));
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server")))
                .andReturn(new ProcessInfo("system", 1024, "system_server", 1559096000L));
        EasyMock.expect(mMockDevice.getBootHistorySince(EasyMock.eq(1559091922L)))
                .andReturn(new HashMap<Long, String>());
        EasyMock.replay(mMockDevice);
        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        StatusCheckerResult result = mChecker.postExecutionCheck(mMockDevice);
        assertEquals(CheckStatus.FAILED, result.getStatus());
        assertTrue(result.isBugreportNeeded());
        EasyMock.verify(mMockDevice);
    }

    /** Test that system checker fail if system_server restarted with device reboot. */
    @Test
    public void testSystemServerProcessRestartedWithUnintentionalDeviceReboot() throws Exception {
        Map<Long, String> history = new HashMap<Long, String>();
        history.put(1559095000L, "kernel_panic");
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server")))
                .andReturn(new ProcessInfo("system", 914, "system_server", 1559091922L));
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server")))
                .andReturn(new ProcessInfo("system", 1024, "system_server", 1559096000L));
        EasyMock.expect(mMockDevice.getBootHistorySince(EasyMock.eq(1559091922L)))
                .andReturn(history);
        EasyMock.expect(mMockDevice.getLastExpectedRebootTimeMillis()).andReturn(200L);
        EasyMock.replay(mMockDevice);
        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        StatusCheckerResult result = mChecker.postExecutionCheck(mMockDevice);
        assertEquals(CheckStatus.FAILED, result.getStatus());
        assertTrue(result.isBugreportNeeded());
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test that if the pid changed but there was a Tradefed reboot, we still not fail the checker.
     */
    @Test
    public void testSystemServerProcessRestartedWithIntentionalDeviceReboot() throws Exception {
        Map<Long, String> history = new HashMap<Long, String>();
        history.put(1559095000L, "reboot");
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server")))
                .andReturn(new ProcessInfo("system", 914, "system_server", 1559091922L));
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server")))
                .andReturn(new ProcessInfo("system", 1024, "system_server", 1559096000L));
        EasyMock.expect(mMockDevice.getBootHistorySince(EasyMock.eq(1559091922L)))
                .andReturn(history);
        // TF reboot was triggered by host
        EasyMock.expect(mMockDevice.getLastExpectedRebootTimeMillis()).andReturn(600L);
        EasyMock.replay(mMockDevice);
        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        StatusCheckerResult result = mChecker.postExecutionCheck(mMockDevice);
        assertEquals(CheckStatus.SUCCESS, result.getStatus());
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test that if fail to get system_server process at preExecutionCheck, we skip the
     * system_server check in postExecution.
     */
    @Test
    public void testFailToGetSystemServerProcess() throws Exception {
        EasyMock.expect(mMockDevice.getProcessByName(EasyMock.eq("system_server"))).andReturn(null);
        EasyMock.replay(mMockDevice);
        assertEquals(CheckStatus.FAILED, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
        EasyMock.verify(mMockDevice);
    }

}
