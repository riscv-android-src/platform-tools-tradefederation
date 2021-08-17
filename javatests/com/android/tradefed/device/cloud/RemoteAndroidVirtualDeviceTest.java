/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.device.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.cloud.GceAvdInfo.GceStatus;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;

import com.google.common.net.HostAndPort;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link RemoteAndroidVirtualDevice}. */
@RunWith(JUnit4.class)
public class RemoteAndroidVirtualDeviceTest {

    private static final String MOCK_DEVICE_SERIAL = "localhost:1234";
    private static final long WAIT_FOR_TUNNEL_TIMEOUT = 10;
    @Mock IDevice mMockIDevice;
    @Mock ITestLogger mTestLogger;
    @Mock IDeviceStateMonitor mMockStateMonitor;
    @Mock IRunUtil mMockRunUtil;
    @Mock IDeviceMonitor mMockDvcMonitor;
    @Mock IDeviceRecovery mMockRecovery;
    private RemoteAndroidVirtualDevice mTestDevice;
    private GceSshTunnelMonitor mGceSshMonitor;
    private boolean mUseRealTunnel = false;

    private GceManager mGceHandler;
    private IBuildInfo mMockBuildInfo;

    /** A {@link TestDevice} that is suitable for running tests against */
    private class TestableRemoteAndroidVirtualDevice extends RemoteAndroidVirtualDevice {
        public TestableRemoteAndroidVirtualDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
            mOptions = new TestDeviceOptions();
        }

        @Override
        protected IRunUtil getRunUtil() {
            return mMockRunUtil;
        }

        @Override
        protected GceSshTunnelMonitor getGceSshMonitor() {
            if (mUseRealTunnel) {
                return super.getGceSshMonitor();
            }
            return mGceSshMonitor;
        }

        @Override
        public IDevice getIDevice() {
            return mMockIDevice;
        }

        @Override
        public DeviceDescriptor getDeviceDescriptor() {
            DeviceDescriptor desc =
                    new DeviceDescriptor(
                            "", false, DeviceAllocationState.Allocated, "", "", "", "", "");
            return desc;
        }

        @Override
        public String getSerialNumber() {
            return MOCK_DEVICE_SERIAL;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mUseRealTunnel = false;

        when(mMockIDevice.getSerialNumber()).thenReturn(MOCK_DEVICE_SERIAL);

        // A TestDevice with a no-op recoverDevice() implementation
        mTestDevice = new TestableRemoteAndroidVirtualDevice();
        mTestDevice.setRecovery(mMockRecovery);

        mGceSshMonitor = Mockito.mock(GceSshTunnelMonitor.class);
        mGceHandler = Mockito.mock(GceManager.class);

        mMockBuildInfo = new BuildInfo();
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mTestDevice.getExecuteShellCommandLog());
    }

    /**
     * Test that an exception thrown in the parser should be propagated to the top level and should
     * not be caught.
     */
    @Test
    public void testExceptionFromParser() {
        final String expectedException =
                "acloud errors: Could not get a valid instance name, check the gce driver's "
                        + "output.The instance may not have booted up at all. [ : ]";
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void createGceSshMonitor(
                            ITestDevice device,
                            IBuildInfo buildInfo,
                            HostAndPort hostAndPort,
                            TestDeviceOptions deviceOptions) {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return new GceManager(
                                getDeviceDescriptor(), new TestDeviceOptions(), mMockBuildInfo) {
                            @Override
                            protected List<String> buildGceCmd(
                                    File reportFile,
                                    IBuildInfo b,
                                    String ipDevice,
                                    MultiMap<String, String> attributes) {
                                FileUtil.deleteFile(reportFile);
                                List<String> tmp = new ArrayList<String>();
                                tmp.add("");
                                return tmp;
                            }
                        };
                    }
                };

        try {
            mTestDevice.launchGce(mMockBuildInfo, null);
            fail("A TargetSetupError should have been thrown");
        } catch (TargetSetupError expected) {
            assertEquals(expectedException, expected.getMessage());
        }
    }

    /**
     * Test {@link RemoteAndroidVirtualDevice#waitForTunnelOnline(long)} return without exception
     * when tunnel is online.
     */
    @Test
    public void testWaitForTunnelOnline() throws Exception {
        doReturn(true).when(mGceSshMonitor).isTunnelAlive();

        mTestDevice.waitForTunnelOnline(WAIT_FOR_TUNNEL_TIMEOUT);
    }

    /**
     * Test {@link RemoteAndroidVirtualDevice#waitForTunnelOnline(long)} throws an exception when
     * the tunnel returns not alive.
     */
    @Test
    public void testWaitForTunnelOnline_notOnline() throws Exception {

        doReturn(false).when(mGceSshMonitor).isTunnelAlive();

        try {
            mTestDevice.waitForTunnelOnline(WAIT_FOR_TUNNEL_TIMEOUT);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // expected.
        }
    }

    /**
     * Test {@link RemoteAndroidVirtualDevice#waitForTunnelOnline(long)} throws an exception when
     * the tunnel object is null, meaning something went wrong during its setup.
     */
    @Test
    public void testWaitForTunnelOnline_tunnelTerminated() throws Exception {
        mGceSshMonitor = null;

        try {
            mTestDevice.waitForTunnelOnline(WAIT_FOR_TUNNEL_TIMEOUT);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            assertEquals(
                    String.format(
                            "Tunnel did not come back online after %sms", WAIT_FOR_TUNNEL_TIMEOUT),
                    expected.getMessage());
        }
    }

    /** Test {@link RemoteAndroidVirtualDevice#preInvocationSetup(IBuildInfo)}. */
    @Test
    public void testPreInvocationSetup() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected void launchGce(
                            IBuildInfo buildInfo, MultiMap<String, String> attributes)
                            throws TargetSetupError {
                        // ignore
                    }

                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public void startLogcat() {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }
                };
        when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(mMockIDevice);
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);

        mTestDevice.preInvocationSetup(mMockBuildInfo, null);
    }

    /**
     * Test {@link RemoteAndroidVirtualDevice#preInvocationSetup(IBuildInfo)} when the device does
     * not come up online at the end should throw an exception.
     */
    @Test
    public void testPreInvocationSetup_fails() throws Exception {
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected void launchGce(
                            IBuildInfo buildInfo, MultiMap<String, String> attributes)
                            throws TargetSetupError {
                        // ignore
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(mMockIDevice);
        when(mMockIDevice.getState()).thenReturn(DeviceState.OFFLINE);

        try {
            mTestDevice.preInvocationSetup(mMockBuildInfo, null);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // expected
        }
        verify(mMockIDevice, times(2)).getState();
    }

    /** Test {@link RemoteAndroidVirtualDevice#postInvocationTearDown(Throwable)}. */
    @Test
    public void testPostInvocationTearDown() throws Exception {
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void createGceSshMonitor(
                            ITestDevice device,
                            IBuildInfo buildInfo,
                            HostAndPort hostAndPort,
                            TestDeviceOptions deviceOptions) {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor() {
                        return null;
                    }
                };
        mTestDevice.setTestLogger(mTestLogger);
        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(true);

        mTestDevice.postInvocationTearDown(null);

        verify(mMockStateMonitor).setIDevice(Mockito.any());
        verify(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq("logcat -v threadtime -d"), Mockito.any(),
                        Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
        verify(mTestLogger)
                .testLog(
                        Mockito.eq("device_logcat_teardown_gce"),
                        Mockito.eq(LogDataType.LOGCAT),
                        Mockito.any());

        Mockito.verify(mGceSshMonitor).shutdown();
        Mockito.verify(mGceSshMonitor).joinMonitor();
    }

    /** Test that in case of BOOT_FAIL, RemoteAndroidVirtualDevice choose to throw exception. */
    @Test
    public void testLaunchGce_bootFail() throws Exception {
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void createGceSshMonitor(
                            ITestDevice device,
                            IBuildInfo buildInfo,
                            HostAndPort hostAndPort,
                            TestDeviceOptions deviceOptions) {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor() {
                        return null;
                    }
                };
        doReturn(
                        new GceAvdInfo(
                                "ins-name",
                                HostAndPort.fromHost("127.0.0.1"),
                                null,
                                "acloud error",
                                GceStatus.BOOT_FAIL))
                .when(mGceHandler)
                .startGce(null, null);

        try {
            mTestDevice.launchGce(new BuildInfo(), null);
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
            // expected
        }
    }

    @Test
    public void testLaunchGce_nullPort() throws Exception {
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void createGceSshMonitor(
                            ITestDevice device,
                            IBuildInfo buildInfo,
                            HostAndPort hostAndPort,
                            TestDeviceOptions deviceOptions) {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor() {
                        return null;
                    }
                };
        doReturn(new GceAvdInfo("ins-name", null, null, "acloud error", GceStatus.BOOT_FAIL))
                .when(mGceHandler)
                .startGce(null, null);

        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(true);

        mTestDevice.setTestLogger(mTestLogger);
        Exception expectedException = null;
        try {
            mTestDevice.launchGce(new BuildInfo(), null);
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
            // expected
            expectedException = expected;
        }
        mTestDevice.postInvocationTearDown(expectedException);

        verify(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq("logcat -v threadtime -d"), Mockito.any(),
                        Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
        verify(mMockStateMonitor).setIDevice(Mockito.any());
        verify(mTestLogger)
                .testLog(
                        Mockito.eq("device_logcat_teardown_gce"),
                        Mockito.eq(LogDataType.LOGCAT),
                        Mockito.any());
    }

    /**
     * Test that when running on the same device a second time, no shutdown state is preserved that
     * would prevent the tunnel from init again.
     */
    @Test
    public void testDeviceNotStoreShutdownState() throws Exception {
        mUseRealTunnel = true;
        IRunUtil mockRunUtil = Mockito.mock(IRunUtil.class);
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("flavor");
        when(mMockBuildInfo.getBuildId()).thenReturn("id");
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public void startLogcat() {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor() {
                        return null;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mockRunUtil;
                    }
                };
        mTestDevice.setTestLogger(mTestLogger);
        File tmpKeyFile = FileUtil.createTempFile("test-gce", "key");
        try {
            OptionSetter setter = new OptionSetter(mTestDevice.getOptions());
            setter.setOptionValue("gce-private-key-path", tmpKeyFile.getAbsolutePath());
            // We use a missing ssh to prevent the real tunnel from running.
            FileUtil.deleteFile(tmpKeyFile);

            when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong()))
                    .thenReturn(mMockIDevice);
            when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);
            when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(true);

            doReturn(
                            new GceAvdInfo(
                                    "ins-name",
                                    HostAndPort.fromHost("127.0.0.1"),
                                    null,
                                    null,
                                    GceStatus.SUCCESS))
                    .when(mGceHandler)
                    .startGce(null, null);

            // Run device a first time
            mTestDevice.preInvocationSetup(mMockBuildInfo, null);
            mTestDevice.getGceSshMonitor().joinMonitor();
            // We expect to find our Runtime exception for the ssh key
            assertNotNull(mTestDevice.getGceSshMonitor().getLastException());
            mTestDevice.postInvocationTearDown(null);
            // Bridge is set to null after tear down
            assertNull(mTestDevice.getGceSshMonitor());

            // run a second time on same device should yield exact same exception.
            mTestDevice.preInvocationSetup(mMockBuildInfo, null);
            mTestDevice.getGceSshMonitor().joinMonitor();
            // Should have the same result, the run time exception from ssh key
            assertNotNull(mTestDevice.getGceSshMonitor().getLastException());
            mTestDevice.postInvocationTearDown(null);
            // Bridge is set to null after tear down
            assertNull(mTestDevice.getGceSshMonitor());

            verify(mMockStateMonitor, times(2)).setIDevice(Mockito.any());
            verify(mMockIDevice, times(2))
                    .executeShellCommand(
                            Mockito.eq("logcat -v threadtime -d"), Mockito.any(),
                            Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
            verify(mTestLogger, times(2))
                    .testLog(
                            Mockito.eq("device_logcat_teardown_gce"),
                            Mockito.eq(LogDataType.LOGCAT),
                            Mockito.any());
            verify(mMockStateMonitor, times(2)).waitForDeviceAvailable(Mockito.anyLong());
            verify(mMockIDevice, times(2)).getState();
            verify(mMockStateMonitor, times(2)).waitForDeviceNotAvailable(Mockito.anyLong());
        } finally {
            FileUtil.deleteFile(tmpKeyFile);
        }
    }

    /** Test that when we skip the GCE teardown the gce shutdown is not called. */
    @Test
    public void testDevice_skipTearDown() throws Exception {
        mUseRealTunnel = true;
        IRunUtil mockRunUtil = Mockito.mock(IRunUtil.class);
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("flavor");
        when(mMockBuildInfo.getBuildId()).thenReturn("id");
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public void startLogcat() {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor() {
                        return null;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mockRunUtil;
                    }
                };
        mTestDevice.setTestLogger(mTestLogger);
        File tmpKeyFile = FileUtil.createTempFile("test-gce", "key");
        try {
            OptionSetter setter = new OptionSetter(mTestDevice.getOptions());
            setter.setOptionValue("gce-private-key-path", tmpKeyFile.getAbsolutePath());
            // We use a missing ssh to prevent the real tunnel from running.
            FileUtil.deleteFile(tmpKeyFile);
            setter.setOptionValue("skip-gce-teardown", "true");

            when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong()))
                    .thenReturn(mMockIDevice);
            when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);
            when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(true);

            doReturn(
                            new GceAvdInfo(
                                    "ins-name",
                                    HostAndPort.fromHost("127.0.0.1"),
                                    null,
                                    null,
                                    GceStatus.SUCCESS))
                    .when(mGceHandler)
                    .startGce(null, null);

            // Run device a first time
            mTestDevice.preInvocationSetup(mMockBuildInfo, null);
            mTestDevice.getGceSshMonitor().joinMonitor();
            // We expect to find our Runtime exception for the ssh key
            assertNotNull(mTestDevice.getGceSshMonitor().getLastException());
            mTestDevice.postInvocationTearDown(null);
            // shutdown was disabled, it should not have been called.
            verify(mGceHandler, never()).shutdownGce();
            verify(mMockStateMonitor).setIDevice(Mockito.any());
            verify(mMockIDevice)
                    .executeShellCommand(
                            Mockito.eq("logcat -v threadtime -d"), Mockito.any(),
                            Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
            verify(mTestLogger)
                    .testLog(
                            Mockito.eq("device_logcat_teardown_gce"),
                            Mockito.eq(LogDataType.LOGCAT),
                            Mockito.any());
        } finally {
            FileUtil.deleteFile(tmpKeyFile);
        }
    }

    /** Test that when device boot offline, we attempt the bugreport collection */
    @Test
    public void testDeviceBoot_offline() throws Exception {
        mUseRealTunnel = true;
        IRunUtil mockRunUtil = Mockito.mock(IRunUtil.class);
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        when(mMockBuildInfo.getBuildBranch()).thenReturn("branch");
        when(mMockBuildInfo.getBuildFlavor()).thenReturn("flavor");
        when(mMockBuildInfo.getBuildId()).thenReturn("id");
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public void startLogcat() {
                        // ignore
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    public DeviceDescriptor getDeviceDescriptor() {
                        return null;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mockRunUtil;
                    }
                };
        mTestDevice.setTestLogger(mTestLogger);
        File tmpKeyFile = FileUtil.createTempFile("test-gce", "key");
        try {
            OptionSetter setter = new OptionSetter(mTestDevice.getOptions());
            setter.setOptionValue("gce-private-key-path", tmpKeyFile.getAbsolutePath());
            // We use a missing ssh to prevent the real tunnel from running.
            FileUtil.deleteFile(tmpKeyFile);

            when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong()))
                    .thenReturn(mMockIDevice);
            when(mMockIDevice.getState()).thenReturn(DeviceState.OFFLINE);
            when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(false);

            doReturn(
                            new GceAvdInfo(
                                    "ins-name",
                                    HostAndPort.fromHost("127.0.0.1"),
                                    null,
                                    null,
                                    GceStatus.SUCCESS))
                    .when(mGceHandler)
                    .startGce(null, null);

            CommandResult bugreportzResult = new CommandResult(CommandStatus.SUCCESS);
            bugreportzResult.setStdout("OK: bugreportz-file");
            doReturn(bugreportzResult)
                    .when(mockRunUtil)
                    .runTimedCmd(Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any());

            // Pulling of the file
            CommandResult result = new CommandResult(CommandStatus.SUCCESS);
            result.setStderr("");
            result.setStdout("");
            doReturn(result).when(mockRunUtil).runTimedCmd(Mockito.anyLong(), Mockito.any());

            // Run device a first time
            try {
                mTestDevice.preInvocationSetup(mMockBuildInfo, null);
                fail("Should have thrown an exception.");
            } catch (DeviceNotAvailableException expected) {
                assertEquals("AVD device booted but was in OFFLINE state", expected.getMessage());
            }
            mTestDevice.getGceSshMonitor().joinMonitor();
            // We expect to find our Runtime exception for the ssh key
            assertNotNull(mTestDevice.getGceSshMonitor().getLastException());
            mTestDevice.postInvocationTearDown(null);
            verify(mMockIDevice, times(2)).getState();
            verify(mMockStateMonitor).setIDevice(Mockito.any());
            verify(mTestLogger)
                    .testLog(
                            Mockito.eq("bugreportz-ssh"),
                            Mockito.eq(LogDataType.BUGREPORTZ),
                            Mockito.any());
            verify(mMockIDevice)
                    .executeShellCommand(
                            Mockito.eq("logcat -v threadtime -d"), Mockito.any(),
                            Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
            verify(mTestLogger)
                    .testLog(
                            Mockito.eq("device_logcat_teardown_gce"),
                            Mockito.eq(LogDataType.LOGCAT),
                            Mockito.any());
        } finally {
            FileUtil.deleteFile(tmpKeyFile);
        }
    }

    @Test
    public void testGetRemoteTombstone() throws Exception {
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    boolean fetchRemoteDir(File localDir, String remotePath) {
                        try {
                            FileUtil.createTempFile("tombstone_00", "", localDir);
                            FileUtil.createTempFile("tombstone_01", "", localDir);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    }
                };
        OptionSetter setter = new OptionSetter(mTestDevice.getOptions());
        setter.setOptionValue(TestDeviceOptions.INSTANCE_TYPE_OPTION, "CUTTLEFISH");

        List<File> tombstones = mTestDevice.getTombstones();
        try {
            assertEquals(2, tombstones.size());
        } finally {
            for (File f : tombstones) {
                FileUtil.deleteFile(f);
            }
        }
    }

    /**
     * Run powerwash() but GceAvdInfo = null, RemoteAndroidVirtualDevice choose to throw exception.
     */
    @Test
    public void testPowerwashNoAvdInfo() throws Exception {
        final String expectedException = "Can not get GCE AVD Info. launch GCE first? [ : ]";

        try {
            mTestDevice.powerwashGce();
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
            assertEquals(expectedException, expected.getMessage());
        }
    }

    /** Test powerwash GCE command */
    @Test
    public void testPowerwashGce() throws Exception {
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    void createGceSshMonitor(
                            ITestDevice device,
                            IBuildInfo buildInfo,
                            HostAndPort hostAndPort,
                            TestDeviceOptions deviceOptions) {
                        // ignore
                    }
                };
        String instanceUser = "user1";
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        OptionSetter setter = new OptionSetter(mTestDevice.getOptions());
        setter.setOptionValue("instance-user", instanceUser);
        String powerwashCommand = String.format("/home/%s/bin/powerwash_cvd", instanceUser);
        String avdConnectHost = String.format("%s@127.0.0.1", instanceUser);
        GceAvdInfo gceAvd =
                new GceAvdInfo(
                        instanceUser,
                        HostAndPort.fromHost("127.0.0.1"),
                        null,
                        null,
                        GceStatus.SUCCESS);
        doReturn(gceAvd).when(mGceHandler).startGce(null, null);
        OutputStream stdout = null;
        OutputStream stderr = null;
        CommandResult powerwashCmdResult = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(stdout),
                        Mockito.eq(stderr),
                        Mockito.eq("ssh"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq(avdConnectHost),
                        Mockito.eq(powerwashCommand)))
                .thenReturn(powerwashCmdResult);
        when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(mMockIDevice);

        // Launch GCE before powerwash.
        mTestDevice.launchGce(mMockBuildInfo, null);
        mTestDevice.powerwashGce();
    }

    /** Test powerwash Oxygen GCE command */
    @Test
    public void testPowerwashOxygenGce() throws Exception {
        mTestDevice =
                new TestableRemoteAndroidVirtualDevice() {
                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    GceManager getGceHandler() {
                        return mGceHandler;
                    }

                    @Override
                    void createGceSshMonitor(
                            ITestDevice device,
                            IBuildInfo buildInfo,
                            HostAndPort hostAndPort,
                            TestDeviceOptions deviceOptions) {
                        // ignore
                    }
                };
        String instanceUser = "user1";
        IBuildInfo mMockBuildInfo = mock(IBuildInfo.class);
        OptionSetter setter = new OptionSetter(mTestDevice.getOptions());
        setter.setOptionValue("instance-user", instanceUser);
        setter.setOptionValue("use-oxygen", "true");
        String avdConnectHost = String.format("%s@127.0.0.1", instanceUser);
        GceAvdInfo gceAvd =
                new GceAvdInfo(
                        instanceUser,
                        HostAndPort.fromHost("127.0.0.1"),
                        null,
                        null,
                        GceStatus.SUCCESS);
        doReturn(gceAvd).when(mGceHandler).startGce(null, null);
        OutputStream stdout = null;
        OutputStream stderr = null;
        CommandResult locateCmdResult = new CommandResult(CommandStatus.SUCCESS);
        locateCmdResult.setStdout("/tmp/cf_dir/bin/powerwash_cvd");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(stdout),
                        Mockito.eq(stderr),
                        Mockito.eq("ssh"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq(avdConnectHost),
                        Mockito.eq("toybox"),
                        Mockito.eq("find"),
                        Mockito.eq("/tmp"),
                        Mockito.eq("-name"),
                        Mockito.eq("powerwash_cvd")))
                .thenReturn(locateCmdResult);
        CommandResult powerwashCmdResult = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(stdout),
                        Mockito.eq(stderr),
                        Mockito.eq("ssh"),
                        Mockito.eq("-o"),
                        Mockito.eq("UserKnownHostsFile=/dev/null"),
                        Mockito.eq("-o"),
                        Mockito.eq("StrictHostKeyChecking=no"),
                        Mockito.eq("-o"),
                        Mockito.eq("ServerAliveInterval=10"),
                        Mockito.eq("-i"),
                        Mockito.any(),
                        Mockito.eq(avdConnectHost),
                        Mockito.eq("HOME=/tmp/cf_dir"),
                        Mockito.eq("/tmp/cf_dir/bin/powerwash_cvd")))
                .thenReturn(powerwashCmdResult);
        when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(mMockIDevice);

        // Launch GCE before powerwash.
        mTestDevice.launchGce(mMockBuildInfo, null);
        mTestDevice.powerwashGce();
    }
}
