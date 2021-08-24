/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.command.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link RemoteManager}. */
@RunWith(JUnit4.class)
public class RemoteManagerFuncTest {

    @Mock IDeviceManager mMockDeviceManager;
    private RemoteManager mRemoteMgr;
    private IRemoteClient mRemoteClient;
    @Mock ICommandScheduler mMockScheduler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mRemoteMgr = new RemoteManager(mMockDeviceManager, mMockScheduler);
        // Extra short timeout for testing.
        mRemoteMgr.setRemoteManagerTimeout(100);
    }

    @After
    public void tearDown() throws Exception {
        if (mRemoteClient != null) {
            mRemoteClient.close();
        }
        if (mRemoteMgr != null) {
            mRemoteMgr.cancelAndWait();
        }
    }

    /**
     * An integration test for client-manager interaction, that will allocate, then free a device.
     */
    @Test
    public void testAllocateFree() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getSerialNumber()).thenReturn("serial");
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAllocateDevice("serial");
        mRemoteClient.sendFreeDevice("serial");

        verify(mMockDeviceManager)
                .freeDevice(Mockito.eq(device), Mockito.eq(FreeDeviceState.AVAILABLE));
    }

    /** An integration test for client-manager interaction, that will add a command */
    @Test
    public void testAddCommand() throws Exception {
        when(mMockScheduler.addCommand(AdditionalMatchers.aryEq(new String[] {"arg1", "arg2"})))
                .thenReturn(true);

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAddCommand(3, "arg1", "arg2");
    }

    /** An integration test for client-manager interaction, that will add a command file */
    @Test
    public void testAddCommandFile() throws Exception {
        final String cmdFile = "cmd.txt";
        List<String> extraArgs = Arrays.asList("foo", "bar");

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAddCommandFile(cmdFile, extraArgs);

        verify(mMockScheduler).addCommandFile(Mockito.eq(cmdFile), Mockito.eq(extraArgs));
    }

    /**
     * An integration test for client-manager interaction, that will allocate, then close the
     * connection. Verifies that closing frees all devices.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testAllocateClose() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getSerialNumber()).thenReturn("serial");
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAllocateDevice("serial");
        mRemoteClient.sendClose();
        mRemoteClient.close();
        mRemoteMgr.join();

        verify(mMockDeviceManager)
                .freeDevice(Mockito.eq(device), Mockito.eq(FreeDeviceState.AVAILABLE));
    }

    /**
     * An integration test for client-manager interaction, that will allocate, then frees all
     * devices.
     */
    @Test
    public void testAllocateFreeAll() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getSerialNumber()).thenReturn("serial");
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAllocateDevice("serial");
        mRemoteClient.sendFreeDevice("*");

        verify(mMockDeviceManager)
                .freeDevice(Mockito.eq(device), Mockito.eq(FreeDeviceState.AVAILABLE));
    }

    /** Test attempt to free an unknown device */
    @Test
    public void testFree_unknown() throws Exception {
        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        try {
            mRemoteClient.sendFreeDevice("foo");
            fail("RemoteException not thrown");
        } catch (RemoteException e) {
            // expected
        }
    }

    /** An integration test for {@link ListDevicesOp} */
    @Test
    public void testListDevices() throws Exception {
        List<DeviceDescriptor> deviceList = new ArrayList<DeviceDescriptor>(2);
        deviceList.add(
                new DeviceDescriptor(
                        "serial",
                        false,
                        DeviceAllocationState.Available,
                        "tuna",
                        "toro",
                        "18",
                        "JWR67C",
                        "4"));
        deviceList.add(
                new DeviceDescriptor(
                        "serial2",
                        false,
                        DeviceAllocationState.Allocated,
                        "herring",
                        "crespo",
                        "15",
                        "IMM767",
                        "5"));
        when(mMockDeviceManager.listAllDevices()).thenReturn(deviceList);

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        List<DeviceDescriptor> returnedDevices = mRemoteClient.sendListDevices();
        assertEquals(2, returnedDevices.size());
        assertEquals("serial", returnedDevices.get(0).getSerial());
        assertFalse(returnedDevices.get(0).isStubDevice());
        assertEquals(DeviceAllocationState.Available, returnedDevices.get(0).getState());
        assertEquals("tuna", returnedDevices.get(0).getProduct());
        assertEquals("toro", returnedDevices.get(0).getProductVariant());
        assertEquals("18", returnedDevices.get(0).getSdkVersion());
        assertEquals("JWR67C", returnedDevices.get(0).getBuildId());
        assertEquals("4", returnedDevices.get(0).getBatteryLevel());
        assertEquals("serial2", returnedDevices.get(1).getSerial());
        assertFalse(returnedDevices.get(1).isStubDevice());
        assertEquals(DeviceAllocationState.Allocated, returnedDevices.get(1).getState());
        assertEquals("herring", returnedDevices.get(1).getProduct());
        assertEquals("crespo", returnedDevices.get(1).getProductVariant());
        assertEquals("15", returnedDevices.get(1).getSdkVersion());
        assertEquals("IMM767", returnedDevices.get(1).getBuildId());
        assertEquals("5", returnedDevices.get(1).getBatteryLevel());
    }

    /** An integration test for normal case {@link ExecCommandOp} */
    @Test
    public void testExecCommand() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getSerialNumber()).thenReturn("serial");
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        String[] args = new String[] {"instrument"};

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAllocateDevice("serial");
        mRemoteClient.sendExecCommand("serial", args);
        mRemoteClient.sendFreeDevice("serial");

        verify(mMockDeviceManager)
                .freeDevice(Mockito.eq(device), Mockito.eq(FreeDeviceState.AVAILABLE));
        verify(mMockScheduler)
                .execCommand(
                        (IScheduledInvocationListener) Mockito.any(),
                        Mockito.eq(device),
                        AdditionalMatchers.aryEq(args));
    }

    /** An integration test for consecutive executes {@link ExecCommandOp} */
    @Test
    public void testConsecutiveExecCommand() throws Exception {
        final ITestDevice device = mock(ITestDevice.class);
        when(device.getSerialNumber()).thenReturn("serial");
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        String[] args = new String[] {"instrument"};

        Answer<Object> commandSuccessAnswer =
                new Answer<Object>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public Object answer(InvocationOnMock invocation) {
                        ExecCommandTracker commandTracker =
                                (ExecCommandTracker) invocation.getArguments()[0];
                        IInvocationContext nullMeta = new InvocationContext();
                        nullMeta.addAllocatedDevice("device", device);
                        Map<ITestDevice, FreeDeviceState> state = new HashMap<>();
                        state.put(device, FreeDeviceState.UNAVAILABLE);
                        commandTracker.invocationComplete(nullMeta, state);
                        return null;
                    }
                };
        doAnswer(commandSuccessAnswer)
                .doNothing()
                .doNothing()
                .when(mMockScheduler)
                .execCommand(
                        (IScheduledInvocationListener) Mockito.anyObject(),
                        Mockito.eq(device),
                        AdditionalMatchers.aryEq(args));

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAllocateDevice("serial");
        // First command succeeds right way.
        mRemoteClient.sendExecCommand("serial", args);
        // Second command will be scheduled but will not finish.
        mRemoteClient.sendExecCommand("serial", args);
        // Third command will fail since the second command is still executing.
        try {
            mRemoteClient.sendExecCommand("serial", args);
            fail("did not receive RemoteException");
        } catch (RemoteException e) {
            // expected
        }
        mRemoteClient.sendFreeDevice("serial");

        verify(mMockDeviceManager)
                .freeDevice(Mockito.eq(device), Mockito.eq(FreeDeviceState.AVAILABLE));
        verify(mMockScheduler, times(2))
                .execCommand(
                        (IScheduledInvocationListener) Mockito.any(),
                        Mockito.eq(device),
                        AdditionalMatchers.aryEq(args));
    }

    /** An integration test for case where device was not allocated before {@link ExecCommandOp} */
    @Test
    public void testExecCommand_noallocate() throws Exception {
        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        try {
            mRemoteClient.sendExecCommand("serial", new String[] {"instrument"});
        } catch (RemoteException e) {
            // expected
            return;
        }
        fail("did not receive RemoteException");
    }

    /** Happy-path test for a handover start */
    @Test
    public void testHandover() throws Exception {
        final int port = 88;
        when(mMockScheduler.handoverShutdown(port)).thenReturn(Boolean.TRUE);

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int mgrPort = mRemoteMgr.getPort();
        assertTrue(mgrPort != -1);
        mRemoteClient = RemoteClient.connect(mgrPort);
        mRemoteClient.sendStartHandover(port);
        // disgusting sleep alert! TODO: change to a wait-notify thingy
        Thread.sleep(100);
    }

    /** Basic test for a handover init complete op */
    @Test
    public void testHandoverInit() throws Exception {
        // expect

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int mgrPort = mRemoteMgr.getPort();
        assertTrue(mgrPort != -1);
        mRemoteClient = RemoteClient.connect(mgrPort);
        mRemoteClient.sendHandoverInitComplete();
        // disgusting sleep alert! TODO: change to a wait-notify thingy
        Thread.sleep(100);

        verify(mMockScheduler).handoverInitiationComplete();
    }

    /**
     * Test {@link GetLastCommandResultOp} result when device is unknown
     *
     * @throws Exception
     */
    @Test
    public void testGetLastCommandResult_unknownDevice() throws Exception {
        ICommandResultHandler mockHandler = mock(ICommandResultHandler.class);

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();

        int mgrPort = mRemoteMgr.getPort();
        assertTrue(mgrPort != -1);
        mRemoteClient = RemoteClient.connect(mgrPort);

        mRemoteClient.sendGetLastCommandResult("foo", mockHandler);

        verify(mockHandler).notAllocated();
    }

    /** Test {@link GetLastCommandResultOp} result when there is no active command */
    @Test
    public void testGetLastCommandResult_noActiveCommand() throws Exception {
        ICommandResultHandler mockHandler = mock(ICommandResultHandler.class);

        ITestDevice device = mock(ITestDevice.class);
        when(device.getSerialNumber()).thenReturn("serial");
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int mgrPort = mRemoteMgr.getPort();
        assertTrue(mgrPort != -1);
        mRemoteClient = RemoteClient.connect(mgrPort);

        mRemoteClient.sendAllocateDevice("serial");
        mRemoteClient.sendGetLastCommandResult("serial", mockHandler);
        mRemoteClient.sendFreeDevice("serial");

        verify(mockHandler).noActiveCommand();
        verify(mMockDeviceManager)
                .freeDevice(Mockito.eq(device), Mockito.eq(FreeDeviceState.AVAILABLE));
    }

    /** Test {@link GetLastCommandResultOp} result when command is executing */
    @Test
    public void testGetLastCommandResult_executing() throws Exception {
        ICommandResultHandler mockHandler = mock(ICommandResultHandler.class);

        ITestDevice device = mock(ITestDevice.class);
        when(device.getSerialNumber()).thenReturn("serial");
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        String[] args = new String[] {"instrument"};

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAllocateDevice("serial");
        mRemoteClient.sendExecCommand("serial", args);
        mRemoteClient.sendGetLastCommandResult("serial", mockHandler);
        mRemoteClient.sendFreeDevice("serial");

        verify(mockHandler).stillRunning();
        verify(mMockDeviceManager)
                .freeDevice(Mockito.eq(device), Mockito.eq(FreeDeviceState.AVAILABLE));
    }

    /**
     * Test {@link GetLastCommandResultOp} result when commmand fails due to a not available device.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetLastCommandResult_notAvail() throws Exception {
        ICommandResultHandler mockHandler = mock(ICommandResultHandler.class);

        final ITestDevice device = mock(ITestDevice.class);
        when(device.getSerialNumber()).thenReturn("serial");
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);
        // TODO: change to not available

        String[] args = new String[] {"instrument"};

        Answer<Void> invErrorAnswer =
                invocation -> {
                    IScheduledInvocationListener listener =
                            (IScheduledInvocationListener) invocation.getArguments()[0];
                    IInvocationContext nullMeta = new InvocationContext();
                    nullMeta.addAllocatedDevice("device", device);
                    nullMeta.addDeviceBuildInfo("device", new BuildInfo());
                    listener.invocationStarted(nullMeta);
                    listener.invocationFailed(new DeviceNotAvailableException("test", "serial"));
                    listener.invocationEnded(1);
                    Map<ITestDevice, FreeDeviceState> state = new HashMap<>();
                    state.put(device, FreeDeviceState.UNAVAILABLE);
                    listener.invocationComplete(nullMeta, state);
                    return null;
                };

        Mockito.doAnswer(invErrorAnswer)
                .when(mMockScheduler)
                .execCommand(
                        Mockito.<IScheduledInvocationListener>anyObject(),
                        Mockito.eq(device),
                        AdditionalMatchers.aryEq(args));

        mRemoteMgr.connectAnyPort();
        mRemoteMgr.start();
        int port = mRemoteMgr.getPort();
        assertTrue(port != -1);
        mRemoteClient = RemoteClient.connect(port);
        mRemoteClient.sendAllocateDevice("serial");
        mRemoteClient.sendExecCommand("serial", args);
        mRemoteClient.sendGetLastCommandResult("serial", mockHandler);
        mRemoteClient.sendFreeDevice("serial");
        verify(mockHandler)
                .failure(
                        (String) Mockito.any(),
                        Mockito.eq(FreeDeviceState.UNAVAILABLE),
                        (Map<String, String>) Mockito.any());
        verify(mMockDeviceManager)
                .freeDevice(Mockito.eq(device), Mockito.eq(FreeDeviceState.AVAILABLE));
        verify(mMockScheduler)
                .execCommand(
                        (IScheduledInvocationListener) Mockito.any(),
                        Mockito.eq(device),
                        AdditionalMatchers.aryEq(args));
    }
}
