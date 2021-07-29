/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.InstallReceiver;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SplitApkInstaller;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.sdklib.AndroidVersion;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.device.ITestDevice.MountPointInfo;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.contentprovider.ContentProviderHandler;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.host.HostOptions;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestLifeCycleReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.KeyguardControllerState;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil2;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/** Unit tests for {@link TestDevice}. */
@RunWith(JUnit4.class)
public class TestDeviceTest {

    private static final String MOCK_DEVICE_SERIAL = "serial";
    // For getCurrentUser, the min api should be 24. We make the stub return 23, the logic should
    // increment it by one.
    private static final int MIN_API_LEVEL_GET_CURRENT_USER = 23;
    private static final int MIN_API_LEVEL_STOP_USER = 22;
    private static final String RAWIMAGE_RESOURCE = "/testdata/rawImage.zip";
    @Mock IDevice mMockIDevice;
    @Mock IShellOutputReceiver mMockReceiver;
    private TestDevice mTestDevice;
    private TestDevice mRecoveryTestDevice;
    private TestDevice mNoFastbootTestDevice;
    @Mock IDeviceRecovery mMockRecovery;
    @Mock IDeviceStateMonitor mMockStateMonitor;
    @Mock IRunUtil mMockRunUtil;
    @Mock IWifiHelper mMockWifi;
    @Mock IDeviceMonitor mMockDvcMonitor;
    IShellResponse mMockShellResponse;
    int mAnswerIndex;

    /** A helper interface as a workaround for lacking of chained answers with Mockito. */
    private interface IShellResponse {
        public String getResponse();
    }

    /** A {@link TestDevice} that is suitable for running tests against */
    private class TestableTestDevice extends TestDevice {
        public TestableTestDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
        }

        @Override
        public void postBootSetup() {
            // too annoying to mock out postBootSetup actions everyone, so do nothing
        }

        @Override
        protected IRunUtil getRunUtil() {
            return mMockRunUtil;
        }

        @Override
        void doReboot(RebootMode rebootMode, @Nullable final String reason)
                throws DeviceNotAvailableException, UnsupportedOperationException {}

        @Override
        IHostOptions getHostOptions() {
            // Avoid issue with GlobalConfiguration
            return new HostOptions();
        }

        @Override
        public boolean isAdbTcp() {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockIDevice.getSerialNumber()).thenReturn(MOCK_DEVICE_SERIAL);

        // A TestDevice with a no-op recoverDevice() implementation
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public void recoverDevice() throws DeviceNotAvailableException {
                        // ignore
                    }

                    @Override
                    IWifiHelper createWifiHelper() throws DeviceNotAvailableException {
                        return mMockWifi;
                    }
                };
        mTestDevice.setRecovery(mMockRecovery);
        mTestDevice.setCommandTimeout(100);
        mTestDevice.setLogStartDelay(-1);

        // TestDevice with intact recoverDevice()
        mRecoveryTestDevice = new TestableTestDevice();
        mRecoveryTestDevice.setRecovery(mMockRecovery);
        mRecoveryTestDevice.setCommandTimeout(100);
        mRecoveryTestDevice.setLogStartDelay(-1);

        // TestDevice without fastboot
        mNoFastbootTestDevice = new TestableTestDevice();
        mNoFastbootTestDevice.setFastbootEnabled(false);
        mNoFastbootTestDevice.setRecovery(mMockRecovery);
        mNoFastbootTestDevice.setCommandTimeout(100);
        mNoFastbootTestDevice.setLogStartDelay(-1);
    }

    /** Test {@link TestDevice#enableAdbRoot()} when adb is already root */
    @Test
    public void testEnableAdbRoot_alreadyRoot() throws Exception {
        injectShellResponse("id", "uid=0(root) gid=0(root)");

        assertTrue(mTestDevice.enableAdbRoot());
    }

    /** Test {@link TestDevice#enableAdbRoot()} when adb is not root */
    @Test
    public void testEnableAdbRoot_notRoot() throws Exception {
        setEnableAdbRootExpectations();

        assertTrue(mTestDevice.enableAdbRoot());
    }

    /** Test {@link TestDevice#enableAdbRoot()} when "enable-root" is "false" */
    @Test
    public void testEnableAdbRoot_noEnableRoot() throws Exception {
        boolean enableRoot = mTestDevice.getOptions().isEnableAdbRoot();
        OptionSetter setter = new OptionSetter(mTestDevice.getOptions());
        setter.setOptionValue("enable-root", "false");
        try {
            assertFalse(mTestDevice.enableAdbRoot());
        } finally {
            setter.setOptionValue("enable-root", Boolean.toString(enableRoot));
        }
    }

    /** Test {@link TestDevice#disableAdbRoot()} when adb is already unroot */
    @Test
    public void testDisableAdbRoot_alreadyUnroot() throws Exception {
        injectShellResponse("id", "uid=2000(shell) gid=2000(shell) groups=2000(shell)");

        assertTrue(mTestDevice.disableAdbRoot());
        verifyShellResponse("id");
    }

    /** Test {@link TestDevice#disableAdbRoot()} when adb is root */
    @Test
    public void testDisableAdbRoot_unroot() throws Exception {
        mMockShellResponse = mock(IShellResponse.class);
        when(mMockShellResponse.getResponse())
                .thenReturn("uid=0(root) gid=0(root)", "uid=2000(shell) gid=2000(shell)");
        injectShellResponse("id", mMockShellResponse);
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as non root");
        setExecuteAdbCommandExpectations(adbResult, "unroot");
        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong()))
                .thenReturn(Boolean.TRUE);
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);

        assertTrue(mTestDevice.disableAdbRoot());

        verifyShellResponse("id", 2);
    }

    /** Configure Mockito expectations for a successful adb root call */
    private void setEnableAdbRootExpectations() throws Exception {
        mMockShellResponse = mock(IShellResponse.class);
        when(mMockShellResponse.getResponse())
                .thenReturn("uid=2000(shell) gid=2000(shell)", "uid=0(root) gid=0(root)");
        injectShellResponse("id", mMockShellResponse);
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as root");
        setExecuteAdbCommandExpectations(adbResult, "root");
        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong()))
                .thenReturn(Boolean.TRUE);
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);
    }

    /** Verify Mockito expectations for a successful adb root call */
    private void verifyEnableAdbRootExpectations() throws Exception {
        verifyShellResponse("id", 2);
    }

    /**
     * Configure Mockito expectations for a successful adb command call
     *
     * @param command the adb command to execute
     * @param result the {@link CommandResult} expected from the adb command execution
     * @throws Exception
     */
    private void setExecuteAdbCommandExpectations(CommandResult result, String command)
            throws Exception {
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("-s"),
                        Mockito.eq(MOCK_DEVICE_SERIAL),
                        Mockito.eq(command)))
                .thenReturn(result);
    }

    /** Test that {@link TestDevice#enableAdbRoot()} reattempts adb root */
    @Test
    public void testEnableAdbRoot_rootRetry() throws Exception {
        mMockShellResponse = mock(IShellResponse.class);
        when(mMockShellResponse.getResponse())
                .thenReturn(
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)");
        injectShellResponse("id", mMockShellResponse);
        CommandResult adbBadResult = new CommandResult(CommandStatus.SUCCESS);
        adbBadResult.setStdout("");
        setExecuteAdbCommandExpectations(adbBadResult, "root");
        CommandResult adbResult = new CommandResult(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as root");
        setExecuteAdbCommandExpectations(adbResult, "root");
        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong()))
                .thenReturn(Boolean.TRUE);
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);

        assertTrue(mTestDevice.enableAdbRoot());

        verify(mMockStateMonitor, times(2)).waitForDeviceNotAvailable(Mockito.anyLong());
        verify(mMockStateMonitor, times(2)).waitForDeviceOnline();
    }

    /** Test that {@link TestDevice#isAdbRoot()} for device without adb root. */
    @Test
    public void testIsAdbRootForNonRoot() throws Exception {
        injectShellResponse("id", "uid=2000(shell) gid=2000(shell)");

        assertFalse(mTestDevice.isAdbRoot());
    }

    /** Test that {@link TestDevice#isAdbRoot()} for device with adb root. */
    @Test
    public void testIsAdbRootForRoot() throws Exception {
        injectShellResponse("id", "uid=0(root) gid=0(root)");

        assertTrue(mTestDevice.isAdbRoot());
    }

    /**
     * Test {@link TestDevice#getProductType()} when device is in fastboot and IDevice has not
     * cached product type property
     */
    @Test
    public void testGetProductType_fastboot() throws DeviceNotAvailableException {
        when(mMockIDevice.getProperty(Mockito.<String>any())).thenReturn(null);
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        // output of this cmd goes to stderr
        fastbootResult.setStdout("");
        fastbootResult.setStderr("product: nexusone\n" + "finished. total time: 0.001s");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any()))
                .thenReturn(fastbootResult);

        mRecoveryTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        assertEquals("nexusone", mRecoveryTestDevice.getProductType());
    }

    /**
     * Test {@link TestDevice#getProductType()} for a device with a non-alphanumeric fastboot
     * product type
     */
    @Test
    public void testGetProductType_fastbootNonalpha() throws DeviceNotAvailableException {
        when(mMockIDevice.getProperty(Mockito.<String>any())).thenReturn(null);
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        // output of this cmd goes to stderr
        fastbootResult.setStdout("");
        fastbootResult.setStderr("product: foo-bar\n" + "finished. total time: 0.001s");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any()))
                .thenReturn(fastbootResult);

        mRecoveryTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        assertEquals("foo-bar", mRecoveryTestDevice.getProductType());
    }

    /**
     * Verify that {@link TestDevice#getProductType()} throws an exception if requesting a product
     * type directly fails while the device is in fastboot.
     */
    @Test
    public void testGetProductType_fastbootFail() {
        when(mMockIDevice.getProperty(Mockito.<String>any())).thenReturn(null);
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        // output of this cmd goes to stderr
        fastbootResult.setStdout("");
        fastbootResult.setStderr("product: \n" + "finished. total time: 0.001s");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any()))
                .thenReturn(fastbootResult);

        mTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        try {
            String type = mTestDevice.getProductType();
            fail(
                    String.format(
                            "DeviceNotAvailableException not thrown; productType was '%s'", type));
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#getProductType()} when device is in adb and IDevice has not cached
     * product type property
     */
    @Test
    public void testGetProductType_adbWithRetry() throws Exception {
        final String expectedOutput = "nexusone";
        setGetPropertyExpectation(DeviceProperties.BOARD, null);
        setGetPropertyExpectation(DeviceProperties.HARDWARE, null);

        injectSystemProperty(DeviceProperties.BOARD, expectedOutput);

        assertEquals(expectedOutput, mTestDevice.getProductType());
    }

    /**
     * Verify that {@link TestDevice#getProductType()} throws an exception if requesting a product
     * type directly still fails.
     */
    @Test
    public void testGetProductType_adbFail() throws Exception {
        setGetPropertyExpectation(DeviceProperties.HARDWARE, null);
        injectSystemProperty(DeviceProperties.BOARD, null);
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);

        try {
            mTestDevice.getProductType();
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Verify that {@link TestDevice#getProductType()} falls back to ro.hardware
     *
     * @throws Exception
     */
    @Test
    public void testGetProductType_legacy() throws Exception {
        final String expectedOutput = "nexusone";
        injectSystemProperty(DeviceProperties.BOARD, "");
        injectSystemProperty(DeviceProperties.HARDWARE, expectedOutput);

        assertEquals(expectedOutput, mTestDevice.getProductType());
    }

    /** Test {@link TestDevice#clearErrorDialogs()} when both a error and anr dialog are present. */
    @Test
    public void testClearErrorDialogs() throws Exception {
        final String anrOutput =
                "debugging=false crashing=false null notResponding=true"
                        + " com.android.server.am.AppNotRespondingDialog@4534aaa0 bad=false\n"
                        + " blah\n";
        final String crashOutput =
                "debugging=false crashing=true com.android.server.am.AppErrorDialog@45388a60"
                        + " notResponding=false null bad=falseblah \n";
        // construct a string with 2 error dialogs of each type to ensure proper detection
        final String fourErrors = anrOutput + anrOutput + crashOutput + crashOutput;
        mMockShellResponse = mock(IShellResponse.class);
        when(mMockShellResponse.getResponse()).thenReturn(fourErrors, "");
        injectShellResponse(null, mMockShellResponse);

        mTestDevice.clearErrorDialogs();

        // expect 4 key events to be sent - one for each dialog
        // and expect another dialog query - but return nothing
        verify(mMockIDevice, times(1 + 4 + 1))
                .executeShellCommand(
                        (String) Mockito.any(),
                        (IShellOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
    }

    /**
     * Test that the unresponsive device exception is propagated from the recovery to TestDevice.
     *
     * @throws Exception
     */
    @Test
    public void testRecoverDevice_ThrowException() throws Exception {
        TestDevice testDevice =
                new TestDevice(mMockIDevice, mMockStateMonitor, mMockDvcMonitor) {
                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        testDevice.setRecovery(
                new IDeviceRecovery() {
                    @Override
                    public void recoverDeviceRecovery(IDeviceStateMonitor monitor)
                            throws DeviceNotAvailableException {
                        throw new DeviceNotAvailableException("test", "serial");
                    }

                    @Override
                    public void recoverDeviceBootloader(IDeviceStateMonitor monitor)
                            throws DeviceNotAvailableException {
                        throw new DeviceNotAvailableException("test", "serial");
                    }

                    @Override
                    public void recoverDevice(
                            IDeviceStateMonitor monitor, boolean recoverUntilOnline)
                            throws DeviceNotAvailableException {
                        throw new DeviceUnresponsiveException("test", "serial");
                    }

                    @Override
                    public void recoverDeviceFastbootd(IDeviceStateMonitor monitor)
                            throws DeviceNotAvailableException {
                        throw new DeviceUnresponsiveException("test", "serial");
                    }
                });
        testDevice.setRecoveryMode(RecoveryMode.AVAILABLE);

        try {
            testDevice.recoverDevice();
        } catch (DeviceNotAvailableException dnae) {
            assertTrue(dnae instanceof DeviceUnresponsiveException);
            return;
        }
        fail();
        verify(mMockIDevice, times(1))
                .executeShellCommand(
                        (String) Mockito.any(),
                        (CollectingOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.eq(TimeUnit.MILLISECONDS));
    }

    /**
     * Simple normal case test for {@link TestDevice#executeShellCommand(String,
     * IShellOutputReceiver)}.
     *
     * <p>Verify that the shell command is routed to the IDevice.
     */
    @Test
    public void testExecuteShellCommand_receiver()
            throws IOException, DeviceNotAvailableException, TimeoutException,
                    AdbCommandRejectedException, ShellCommandUnresponsiveException {
        final String testCommand = "simple command";

        mTestDevice.executeShellCommand(testCommand, mMockReceiver);

        // expect shell command to be called
        verify(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
    }

    /**
     * Simple normal case test for {@link TestDevice#executeShellCommand(String)}.
     *
     * <p>Verify that the shell command is routed to the IDevice, and shell output is collected.
     */
    @Test
    public void testExecuteShellCommand() throws Exception {
        final String testCommand = "simple command";
        final String expectedOutput = "this is the output\r\n in two lines\r\n";
        injectShellResponse(testCommand, expectedOutput);

        assertEquals(expectedOutput, mTestDevice.executeShellCommand(testCommand));
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} throws IOException and recovery immediately fails.
     *
     * <p>Verify that a DeviceNotAvailableException is thrown.
     */
    @Test
    public void testExecuteShellCommand_recoveryFail() throws Exception {
        final String testCommand = "simple command";
        // expect shell command to be called
        doThrow(new IOException())
                .when(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockRecovery)
                .recoverDevice(Mockito.eq(mMockStateMonitor), Mockito.eq(false));

        try {
            mRecoveryTestDevice.executeShellCommand(testCommand, mMockReceiver);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} throws IOException and device is in recovery until online mode.
     *
     * <p>Verify that a DeviceNotAvailableException is thrown.
     */
    @Test
    public void testExecuteShellCommand_recoveryUntilOnline() throws Exception {
        final String testCommand = "simple command";
        mRecoveryTestDevice.setRecoveryMode(RecoveryMode.ONLINE);
        doAnswer(
                        invocation -> {
                            if (mAnswerIndex == 0) {
                                mAnswerIndex++;
                                throw (new IOException());
                            }
                            return null;
                        })
                .when(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());

        setEnableAdbRootExpectations();

        mRecoveryTestDevice.executeShellCommand(testCommand, mMockReceiver);

        // expect shell command to be called
        verify(mMockIDevice, times(2))
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} throws IOException and recovery succeeds.
     *
     * <p>Verify that command is re-tried.
     */
    @Test
    public void testExecuteShellCommand_recoveryRetry() throws Exception {
        mTestDevice =
                new TestDevice(mMockIDevice, mMockStateMonitor, mMockDvcMonitor) {
                    @Override
                    IWifiHelper createWifiHelper() {
                        return mMockWifi;
                    }

                    @Override
                    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
                        return false;
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void doReboot(RebootMode rebootMode, @Nullable final String reason)
                            throws DeviceNotAvailableException, UnsupportedOperationException {}
                };
        mTestDevice.setRecovery(mMockRecovery);
        final String testCommand = "simple command";
        doAnswer(
                        invocation -> {
                            if (mAnswerIndex == 0) {
                                mAnswerIndex++;
                                throw (new IOException());
                            }
                            return null;
                        })
                .when(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);
        injectSystemProperty("ro.build.version.sdk", "23");

        mTestDevice.executeShellCommand(testCommand, mMockReceiver);

        // expect shell command to be called
        verify(mMockIDevice, times(2))
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
        assertRecoverySuccess();
    }

    /** Set expectations for a successful recovery operation */
    private void assertRecoverySuccess(int times)
            throws DeviceNotAvailableException, IOException, TimeoutException,
                    AdbCommandRejectedException, ShellCommandUnresponsiveException {
        // expect post boot up steps
        verify(mMockRecovery, times(times))
                .recoverDevice(Mockito.eq(mMockStateMonitor), Mockito.eq(false));
        verify(mMockIDevice, times(times))
                .executeShellCommand(
                        Mockito.eq("dumpsys input"),
                        Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.eq(TimeUnit.MILLISECONDS));
        verify(mMockIDevice, times(times))
                .executeShellCommand(
                        Mockito.eq(TestDevice.DISMISS_KEYGUARD_WM_CMD),
                        (IShellOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
    }

    private void assertRecoverySuccess()
            throws DeviceNotAvailableException, IOException, TimeoutException,
                    AdbCommandRejectedException, ShellCommandUnresponsiveException {
        assertRecoverySuccess(1);
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * command times out and recovery succeeds.
     *
     * <p>Verify that command is re-tried.
     */
    @Test
    public void testExecuteShellCommand_recoveryTimeoutRetry() throws Exception {
        mTestDevice =
                new TestDevice(mMockIDevice, mMockStateMonitor, mMockDvcMonitor) {
                    @Override
                    IWifiHelper createWifiHelper() {
                        return mMockWifi;
                    }

                    @Override
                    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
                        return false;
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void doReboot(RebootMode rebootMode, @Nullable final String reason)
                            throws DeviceNotAvailableException, UnsupportedOperationException {}
                };
        mTestDevice.setRecovery(mMockRecovery);
        final String testCommand = "simple command";
        // expect shell command to be called - and never return from that call
        // then expect shellCommand to be executed again, and succeed
        doAnswer(
                        invocation -> {
                            if (mAnswerIndex == 0) {
                                mAnswerIndex++;
                                throw (new IOException());
                            }
                            return null;
                        })
                .when(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);
        injectSystemProperty("ro.build.version.sdk", "23");

        mTestDevice.executeShellCommand(testCommand, mMockReceiver);

        verify(mMockIDevice, times(2))
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
        assertRecoverySuccess();
        verifySystemProperty("ro.build.version.sdk", "23", 1);
    }

    /**
     * Test {@link TestDevice#executeShellCommand(String, IShellOutputReceiver)} behavior when
     * {@link IDevice} repeatedly throws IOException and recovery succeeds.
     *
     * <p>Verify that DeviceNotAvailableException is thrown.
     */
    @Test
    public void testExecuteShellCommand_recoveryAttempts() throws Exception {
        mTestDevice =
                new TestDevice(mMockIDevice, mMockStateMonitor, mMockDvcMonitor) {
                    @Override
                    IWifiHelper createWifiHelper() {
                        return mMockWifi;
                    }

                    @Override
                    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
                        return false;
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void doReboot(RebootMode rebootMode, @Nullable final String reason)
                            throws DeviceNotAvailableException, UnsupportedOperationException {}
                };
        mTestDevice.setRecovery(mMockRecovery);
        final String testCommand = "simple command";
        doThrow(new IOException())
                .when(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);
        injectSystemProperty("ro.build.version.sdk", "23");

        try {
            mTestDevice.executeShellCommand(testCommand, mMockReceiver);
            fail("DeviceUnresponsiveException not thrown");
        } catch (DeviceUnresponsiveException e) {
            // expected
        }

        assertRecoverySuccess(TestDevice.MAX_RETRY_ATTEMPTS + 1);
        verifySystemProperty("ro.build.version.sdk", "23", 3);
        verify(mMockStateMonitor, times(3)).waitForDeviceOnline();
        verify(mMockIDevice, times(TestDevice.MAX_RETRY_ATTEMPTS + 1))
                .executeShellCommand(
                        Mockito.eq(testCommand),
                        Mockito.eq(mMockReceiver),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any());
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     *
     * <p>Verify that output of 'adb shell df' command is parsed correctly.
     */
    @Test
    public void testGetExternalStoreFreeSpace() throws Exception {
        final String dfOutput =
                "/mnt/sdcard: 3864064K total, 1282880K used, 2581184K available (block size 32768)";
        assertGetExternalStoreFreeSpace(dfOutput, 2581184);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     *
     * <p>Verify that the table-based output of 'adb shell df' command is parsed correctly.
     */
    @Test
    public void testGetExternalStoreFreeSpace_table() throws Exception {
        final String dfOutput =
                "Filesystem             Size   Used   Free   Blksize\n"
                        + "/mnt/sdcard              3G   787M     2G   4096";
        assertGetExternalStoreFreeSpace(dfOutput, 2 * 1024 * 1024);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     *
     * <p>Verify that the coreutils-like output of 'adb shell df' command is parsed correctly.
     */
    @Test
    public void testGetExternalStoreFreeSpace_toybox() throws Exception {
        final String dfOutput =
                "Filesystem      1K-blocks	Used  Available Use% Mounted on\n"
                        + "/dev/fuse        11585536    1316348   10269188  12% /mnt/sdcard";
        assertGetExternalStoreFreeSpace(dfOutput, 10269188);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     *
     * <p>Verify that the coreutils-like output of 'adb shell df' command is parsed correctly. This
     * variant tests the fact that the returned mount point in last column of command output may not
     * match the original path provided as parameter to df.
     */
    @Test
    public void testGetExternalStoreFreeSpace_toybox2() throws Exception {
        final String dfOutput =
                "Filesystem     1K-blocks   Used Available Use% Mounted on\n"
                        + "/dev/fuse       27240188 988872  26251316   4% /storage/emulated";
        assertGetExternalStoreFreeSpace(dfOutput, 26251316);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     *
     * <p>Verify behavior when 'df' command returns unexpected content
     */
    @Test
    public void testGetExternalStoreFreeSpace_badOutput() throws Exception {
        final String dfOutput = "/mnt/sdcard: blaH";
        assertGetExternalStoreFreeSpace(dfOutput, 0);
    }

    /**
     * Unit test for {@link TestDevice#getExternalStoreFreeSpace()}.
     *
     * <p>Verify behavior when first 'df' attempt returns empty output
     */
    @Test
    public void testGetExternalStoreFreeSpace_emptyOutput() throws Exception {
        final String mntPoint = "/mnt/sdcard";
        final String expectedCmd = "df " + mntPoint;
        when(mMockStateMonitor.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).thenReturn(mntPoint);
        // expect shell command to be called, and return the empty df output
        mMockShellResponse = mock(IShellResponse.class);
        final String dfOutput =
                "/mnt/sdcard: 3864064K total, 1282880K used, 2581184K available (block size 32768)";
        when(mMockShellResponse.getResponse()).thenReturn("", dfOutput);
        injectShellResponse(expectedCmd, mMockShellResponse);

        assertEquals(2581184, mTestDevice.getExternalStoreFreeSpace());
    }

    /**
     * Helper method to verify the {@link TestDevice#getExternalStoreFreeSpace()} method under
     * different conditions.
     *
     * @param dfOutput the test output to inject
     * @param expectedFreeSpaceKB the expected free space
     */
    private void assertGetExternalStoreFreeSpace(final String dfOutput, long expectedFreeSpaceKB)
            throws Exception {
        final String mntPoint = "/mnt/sdcard";
        final String expectedCmd = "df " + mntPoint;
        when(mMockStateMonitor.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).thenReturn(mntPoint);
        // expect shell command to be called, and return the test df output
        injectShellResponse(expectedCmd, dfOutput);

        assertEquals(expectedFreeSpaceKB, mTestDevice.getExternalStoreFreeSpace());
    }

    /**
     * Unit test for {@link TestDevice#syncFiles(File, String)}.
     *
     * <p>Verify behavior when given local file does not exist
     */
    @Test
    public void testSyncFiles_missingLocal() throws Exception {

        assertFalse(mTestDevice.syncFiles(new File("idontexist"), "/sdcard"));
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)} success
     * case.
     */
    @Test
    public void testRunInstrumentationTests() throws Exception {
        IRemoteAndroidTestRunner mockRunner = mock(IRemoteAndroidTestRunner.class);
        when(mockRunner.getPackageName()).thenReturn("com.example");
        Collection<ITestLifeCycleReceiver> listeners = new ArrayList<>(0);

        mTestDevice.runInstrumentationTests(mockRunner, listeners);

        // expect runner.run command to be called
        verify(mockRunner).run(Mockito.any(Collection.class));
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)} when
     * recovery fails.
     */
    @Test
    public void testRunInstrumentationTests_recoveryFails() throws Exception {
        IRemoteAndroidTestRunner mockRunner = mock(IRemoteAndroidTestRunner.class);
        Collection<ITestLifeCycleReceiver> listeners = new ArrayList<>(1);
        ITestLifeCycleReceiver listener = mock(ITestLifeCycleReceiver.class);
        listeners.add(listener);

        doThrow(new IOException()).when(mockRunner).run(Mockito.any(Collection.class));
        when(mockRunner.getPackageName()).thenReturn("foo");

        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockRecovery)
                .recoverDevice(Mockito.eq(mMockStateMonitor), Mockito.eq(false));

        try {
            mRecoveryTestDevice.runInstrumentationTests(mockRunner, listeners);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#runInstrumentationTests(IRemoteAndroidTestRunner, Collection)} when
     * recovery succeeds.
     */
    @Test
    public void testRunInstrumentationTests_recoverySucceeds() throws Exception {
        IRemoteAndroidTestRunner mockRunner = mock(IRemoteAndroidTestRunner.class);
        Collection<ITestLifeCycleReceiver> listeners = new ArrayList<>(1);
        ITestLifeCycleReceiver listener = mock(ITestLifeCycleReceiver.class);
        listeners.add(listener);

        doThrow(new IOException()).when(mockRunner).run(Mockito.any(Collection.class));
        when(mockRunner.getPackageName()).thenReturn("foo");

        mTestDevice.runInstrumentationTests(mockRunner, listeners);
    }

    /**
     * Test {@link TestDevice#executeFastbootCommand(String...)} throws an exception when fastboot
     * is not available.
     */
    @Test
    public void testExecuteFastbootCommand_nofastboot() throws Exception {
        try {
            mNoFastbootTestDevice.executeFastbootCommand("");
            fail("UnsupportedOperationException not thrown");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    /**
     * Test {@link TestDevice#executeLongFastbootCommand(String...)} throws an exception when
     * fastboot is not available.
     */
    @Test
    public void testExecuteLongFastbootCommand_nofastboot() throws Exception {
        try {
            mNoFastbootTestDevice.executeFastbootCommand("");
            fail("UnsupportedOperationException not thrown");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    /**
     * Test that state changes are ignore while {@link TestDevice#executeFastbootCommand(String...)}
     * is active.
     */
    @Test
    public void testExecuteFastbootCommand_state() throws Exception {
        final long waitTimeMs = 150;
        // build a fastboot response that will block
        Answer<CommandResult> blockResult =
                invocation -> {
                    synchronized (this) {
                        // first inform this test that fastboot cmd is executing
                        notifyAll();
                        // now wait for test to unblock us when its done testing logic
                        long now = System.currentTimeMillis();
                        long deadline = now + waitTimeMs;
                        while (now < deadline) {
                            wait(deadline - now);
                            now = System.currentTimeMillis();
                        }
                    }
                    return new CommandResult(CommandStatus.SUCCESS);
                };
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq(MOCK_DEVICE_SERIAL),
                        Mockito.eq("foo")))
                .thenAnswer(blockResult);

        mTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        assertEquals(TestDeviceState.FASTBOOT, mTestDevice.getDeviceState());

        // start fastboot command in background thread
        Thread fastbootThread =
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            mTestDevice.executeFastbootCommand("foo");
                        } catch (DeviceNotAvailableException e) {
                            CLog.e(e);
                        }
                    }
                };
        fastbootThread.setName(getClass().getCanonicalName() + "#testExecuteFastbootCommand_state");
        fastbootThread.start();
        try {
            synchronized (blockResult) {
                blockResult.wait(waitTimeMs);
            }
            // expect to ignore this
            mTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
            assertEquals(TestDeviceState.FASTBOOT, mTestDevice.getDeviceState());
        } finally {
            synchronized (blockResult) {
                blockResult.notifyAll();
            }
        }
        fastbootThread.join();
        mTestDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        assertEquals(TestDeviceState.NOT_AVAILABLE, mTestDevice.getDeviceState());
        verify(mMockRecovery, times(2))
                .recoverDeviceBootloader((IDeviceStateMonitor) Mockito.any());
        verify(mMockStateMonitor).setState(TestDeviceState.FASTBOOT);
        verify(mMockStateMonitor).setState(TestDeviceState.NOT_AVAILABLE);
        verify(mMockRunUtil, times(2))
                .runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq(MOCK_DEVICE_SERIAL),
                        Mockito.eq("foo"));
    }

    /** Test recovery mode is entered when fastboot command fails */
    @Test
    public void testExecuteFastbootCommand_recovery()
            throws UnsupportedOperationException, DeviceNotAvailableException {
        CommandResult result = new CommandResult(CommandStatus.EXCEPTION);
        CommandResult successResult = new CommandResult(CommandStatus.SUCCESS);
        successResult.setStderr("");
        successResult.setStdout("");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq(MOCK_DEVICE_SERIAL),
                        Mockito.eq("foo")))
                .thenReturn(result, successResult);

        mTestDevice.executeFastbootCommand("foo");

        verify(mMockRecovery).recoverDeviceBootloader((IDeviceStateMonitor) Mockito.any());
    }

    /**
     * Basic test for encryption if encryption is not supported.
     *
     * <p>Calls {@link TestDevice#encryptDevice(boolean)}, {@link TestDevice#unlockDevice()}, and
     * {@link TestDevice#unencryptDevice()} and makes sure that a {@link
     * UnsupportedOperationException} is thrown for each method.
     */
    @Test
    public void testEncryptionUnsupported() throws Exception {
        mMockShellResponse = mock(IShellResponse.class);
        when(mMockShellResponse.getResponse())
                .thenReturn(
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)",
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)",
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)");
        injectShellResponse("id", mMockShellResponse);
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as root");
        setExecuteAdbCommandExpectations(adbResult, "root");
        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong()))
                .thenReturn(Boolean.TRUE);
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);

        setGetPropertyExpectation("ro.crypto.state", "unsupported");

        try {
            mTestDevice.encryptDevice(false);
            fail("encryptUserData() did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            mTestDevice.unlockDevice();
            fail("decryptUserData() did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        try {
            mTestDevice.unencryptDevice();
            fail("unencryptUserData() did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        return;
    }

    /** Unit test for {@link TestDevice#encryptDevice(boolean)}. */
    @Test
    public void testEncryptDevice_alreadyEncrypted() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        setEncryptedSupported();

        assertTrue(mTestDevice.encryptDevice(false));
        verifyEncryptedSupported();
    }

    /** Unit test for {@link TestDevice#encryptDevice(boolean)}. */
    @Test
    public void testEncryptDevice_encryptionFails() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return false;
                    }
                };
        mMockShellResponse = mock(IShellResponse.class);
        when(mMockShellResponse.getResponse())
                .thenReturn(
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)",
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)");

        injectShellResponse("id", mMockShellResponse);
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as root");
        setExecuteAdbCommandExpectations(adbResult, "root");
        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong()))
                .thenReturn(Boolean.TRUE);
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);

        setGetPropertyExpectation("ro.crypto.state", "encrypted");

        injectShellResponse(
                "vdc cryptfs enablecrypto wipe \"android\"",
                "500 2280 Usage: cryptfs enablecrypto\r\n");
        injectShellResponse("vdc cryptfs enablecrypto wipe default", "200 0 -1\r\n");
        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong()))
                .thenReturn(Boolean.TRUE);
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);

        assertFalse(mTestDevice.encryptDevice(false));

        verifyGetPropertyExpectation("ro.crypto.state", "encrypted", 1);
        verifyShellResponse("id", 4);
        verifyShellResponse("vdc cryptfs enablecrypto wipe \"android\"");
        verifyShellResponse("vdc cryptfs enablecrypto wipe default");
    }

    /** Unit test for {@link TestDevice#unencryptDevice()} with fastboot erase. */
    @Test
    public void testUnencryptDevice_erase() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public void rebootIntoBootloader()
                            throws DeviceNotAvailableException, UnsupportedOperationException {
                        // do nothing.
                    }

                    @Override
                    public void rebootUntilOnline() throws DeviceNotAvailableException {
                        // do nothing.
                    }

                    @Override
                    public CommandResult fastbootWipePartition(String partition)
                            throws DeviceNotAvailableException {
                        return null;
                    }

                    @Override
                    public void postBootSetup() {
                        super.postBootSetup();
                    }
                };
        setEncryptedSupported();
        when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(mMockIDevice);

        assertTrue(mTestDevice.unencryptDevice());
        verifyEncryptedSupported();
    }

    /** Unit test for {@link TestDevice#unencryptDevice()} with fastboot wipe. */
    @Test
    public void testUnencryptDevice_wipe() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public void rebootIntoBootloader()
                            throws DeviceNotAvailableException, UnsupportedOperationException {
                        // do nothing.
                    }

                    @Override
                    public void rebootUntilOnline() throws DeviceNotAvailableException {
                        // do nothing.
                    }

                    @Override
                    public CommandResult fastbootWipePartition(String partition)
                            throws DeviceNotAvailableException {
                        return null;
                    }

                    @Override
                    public void reboot() throws DeviceNotAvailableException {
                        // do nothing.
                    }
                };
        mTestDevice.getOptions().setUseFastbootErase(true);
        setEncryptedSupported();
        injectShellResponse("vdc volume list", "110 sdcard /mnt/sdcard1");
        injectShellResponse("vdc volume format sdcard", "200 0 -1:success");

        assertTrue(mTestDevice.unencryptDevice());
        verifyShellResponse("vdc volume list");
        verifyShellResponse("vdc volume format sdcard");
        verifyEncryptedSupported();
    }

    /**
     * Configure Mockito for a encryption check call, that returns that encryption is unsupported
     */
    private void setEncryptedUnsupportedExpectations() throws Exception {
        setEnableAdbRootExpectations();
        setGetPropertyExpectation("ro.crypto.state", "unsupported");
    }

    private void verifyEncryptedUnsupportedExpectations() throws Exception {
        verifyEnableAdbRootExpectations();
        verifyGetPropertyExpectation("ro.crypto.state", "unsupported", 1);
    }

    /**
     * Configure Mockito for a encryption check call, that returns that encryption is unsupported
     */
    private void setEncryptedSupported() throws Exception {
        setEnableAdbRootExpectations();
        setGetPropertyExpectation("ro.crypto.state", "encrypted");
    }

    private void verifyEncryptedSupported() throws Exception {
        verifyEnableAdbRootExpectations();
        verifyGetPropertyExpectation("ro.crypto.state", "encrypted", 1);
    }

    /** Simple test for {@link TestDevice#switchToAdbUsb()} */
    @Test
    public void testSwitchToAdbUsb() throws Exception {
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "usb");

        mTestDevice.switchToAdbUsb();
    }

    /** Test for {@link TestDevice#switchToAdbTcp()} when device has no ip address */
    @Test
    public void testSwitchToAdbTcp_noIp() throws Exception {
        when(mMockWifi.getIpAddress()).thenReturn(null);

        assertNull(mTestDevice.switchToAdbTcp());
    }

    /** Test normal success case for {@link TestDevice#switchToAdbTcp()}. */
    @Test
    public void testSwitchToAdbTcp() throws Exception {
        when(mMockWifi.getIpAddress()).thenReturn("ip");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("tcpip"),
                        Mockito.eq("5555")))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));

        assertEquals("ip:5555", mTestDevice.switchToAdbTcp());
    }

    /**
     * Test simple success case for {@link TestDevice#installPackage(File, File, boolean,
     * String...)}.
     */
    @Test
    public void testInstallPackages() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String certFile = "foo.dc";
        final String apkFile = "foo.apk";
        when(mMockIDevice.syncPackageToDevice(Mockito.contains(certFile))).thenReturn(certFile);
        when(mMockIDevice.syncPackageToDevice(Mockito.contains(apkFile))).thenReturn(apkFile);
        // expect apk path to be passed as extra arg
        mMockIDevice.installRemotePackage(
                Mockito.eq(certFile),
                Mockito.eq(true),
                Mockito.any(),
                Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                Mockito.eq(TimeUnit.MINUTES),
                Mockito.eq("-l"),
                Mockito.contains(apkFile));
        setMockIDeviceAppOpsToPersist();
        mMockIDevice.removeRemotePackage(certFile);
        mMockIDevice.removeRemotePackage(apkFile);

        assertNull(mTestDevice.installPackage(new File(apkFile), new File(certFile), true, "-l"));
        verifyMockIDeviceAppOpsToPersist();
    }

    private void setMockIDeviceAppOpsToPersist() {
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (OutputStream) Mockito.isNull(),
                        (OutputStream) Mockito.isNull(),
                        Mockito.eq("adb"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("shell"),
                        Mockito.eq("appops"),
                        Mockito.eq("write-settings")))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));
    }

    private void verifyMockIDeviceAppOpsToPersist() {
        verify(mMockRunUtil)
                .runTimedCmd(
                        Mockito.anyLong(),
                        (OutputStream) Mockito.isNull(),
                        (OutputStream) Mockito.isNull(),
                        Mockito.eq("adb"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("shell"),
                        Mockito.eq("appops"),
                        Mockito.eq("write-settings"));
    }

    /** Test when a timeout during installation with certificat is thrown. */
    @Test
    public void testInstallPackages_timeout() throws Exception {
        final String certFile = "foo.dc";
        final String apkFile = "foo.apk";
        when(mMockIDevice.syncPackageToDevice(Mockito.contains(certFile))).thenReturn(certFile);
        when(mMockIDevice.syncPackageToDevice(Mockito.contains(apkFile))).thenReturn(apkFile);
        setMockIDeviceAppOpsToPersist();

        // expect apk path to be passed as extra arg
        doThrow(new InstallException(new TimeoutException()))
                .when(mMockIDevice)
                .installRemotePackage(
                        Mockito.eq(certFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("-l"),
                        Mockito.contains(apkFile));

        assertTrue(
                mTestDevice
                        .installPackage(new File(apkFile), new File(certFile), true, "-l")
                        .contains("InstallException during package installation."));
        verifyMockIDeviceAppOpsToPersist();

        verify(mMockIDevice).removeRemotePackage(certFile);
        verify(mMockIDevice).removeRemotePackage(apkFile);
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting LRX22F
     * build attributes
     *
     * @throws Exception
     */
    @Test
    public void testRuntimePermissionSupportedLmpRelease() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "21");
        injectSystemProperty(DeviceProperties.BUILD_CODENAME, "REL");
        injectSystemProperty(DeviceProperties.BUILD_ID, "1642709");

        assertFalse(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting LMP MR1
     * dev build attributes
     *
     * @throws Exception
     */
    @Test
    public void testRuntimePermissionSupportedLmpMr1Dev() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "22");
        injectSystemProperty(DeviceProperties.BUILD_CODENAME, "REL");
        injectSystemProperty(DeviceProperties.BUILD_ID, "1844090");

        assertFalse(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting random dev
     * build attributes
     *
     * @throws Exception
     */
    @Test
    public void testRuntimePermissionSupportedNonMncLocal() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "21");
        injectSystemProperty(DeviceProperties.BUILD_CODENAME, "LMP");
        injectSystemProperty(DeviceProperties.BUILD_ID, "eng.foo.20150414.190304");

        assertFalse(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting early MNC
     * dev build attributes
     *
     * @throws Exception
     */
    @Test
    public void testRuntimePermissionSupportedEarlyMnc() throws Exception {
        setMockIDeviceRuntimePermissionNotSupported();

        assertFalse(mTestDevice.isRuntimePermissionSupported());
    }

    /**
     * Test that isRuntimePermissionSupported returns correct result for device reporting early MNC
     * dev build attributes
     *
     * @throws Exception
     */
    @Test
    public void testRuntimePermissionSupportedMncPostSwitch() throws Exception {
        setMockIDeviceRuntimePermissionSupported();

        assertTrue(mTestDevice.isRuntimePermissionSupported());
    }

    /** Convenience method for setting up mMockIDevice to not support runtime permission */
    private void setMockIDeviceRuntimePermissionNotSupported() {
        setGetPropertyExpectation("ro.build.version.sdk", "22");
    }

    private void verifyMockIDeviceRuntimePermissionNotSupported() {
        verifyGetPropertyExpectation("ro.build.version.sdk", "22", 1);
    }

    /** Convenience method for setting up mMockIDevice to support runtime permission */
    private void setMockIDeviceRuntimePermissionSupported() {
        setGetPropertyExpectation("ro.build.version.sdk", "23");
    }

    private void verifyMockIDeviceRuntimePermissionSupported() {
        verifyGetPropertyExpectation("ro.build.version.sdk", "23", 1);
    }

    private void verifyMockIDeviceRuntimePermissionSupported(int times) {
        verifyGetPropertyExpectation("ro.build.version.sdk", "23", times);
    }

    /**
     * Test default installPackage on device not supporting runtime permission has expected list of
     * args
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackage_default_runtimePermissionNotSupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionNotSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackage(new File(apkFile), true));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));
    }

    /** Test when a timeout during installation is thrown. */
    @Test
    public void testInstallPackage_default_timeout() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionNotSupported();
        setMockIDeviceAppOpsToPersist();
        doThrow(new InstallException(new TimeoutException()))
                .when(mMockIDevice)
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));

        assertTrue(
                mTestDevice
                        .installPackage(new File(apkFile), true)
                        .contains(
                                "InstallException during package installation. cause:"
                                        + " com.android.ddmlib.InstallException"));
        verifyMockIDeviceRuntimePermissionNotSupported();
        verifyMockIDeviceAppOpsToPersist();
    }

    /**
     * Test default installPackage on device supporting runtime permission has expected list of args
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackage_default_runtimePermissionSupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackage(new File(apkFile), true));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("-g"));
    }

    /**
     * Test default installPackageForUser on device not supporting runtime permission has expected
     * list of args
     *
     * @throws Exception
     */
    @Test
    public void testinstallPackageForUser_default_runtimePermissionNotSupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        int uid = 123;
        setMockIDeviceRuntimePermissionNotSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackageForUser(new File(apkFile), true, uid));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("--user"),
                        Mockito.eq(Integer.toString(uid)));
    }

    /**
     * Test default installPackageForUser on device supporting runtime permission has expected list
     * of args
     *
     * @throws Exception
     */
    @Test
    public void testinstallPackageForUser_default_runtimePermissionSupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        int uid = 123;
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackageForUser(new File(apkFile), true, uid));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("-g"),
                        Mockito.eq("--user"),
                        Mockito.eq(Integer.toString(uid)));
    }

    /**
     * Test runtime permission variant of installPackage throws exception on unsupported device
     * platform
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackage_throw() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionNotSupported();

        try {
            mTestDevice.installPackage(new File(apkFile), true, true);
        } catch (UnsupportedOperationException uoe) {
            // ignore, exception thrown here is expected
            return;
        }
        fail("installPackage did not throw IllegalArgumentException");
    }

    /**
     * Test runtime permission variant of installPackage has expected list of args on a supported
     * device when granting
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackage_grant_runtimePermissionSupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackage(new File(apkFile), true, true));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("-g"));
    }

    /** Test installing an apk that times out without any messages. */
    @Test
    public void testInstallPackage_timeout() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        assertEquals(
                String.format("Installation of %s timed out", new File(apkFile).getAbsolutePath()),
                mTestDevice.installPackage(new File(apkFile), true, true));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("-g"));
    }

    /**
     * Test runtime permission variant of installPackage has expected list of args on a supported
     * device when not granting
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackage_noGrant_runtimePermissionSupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackage(new File(apkFile), true, false));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));
    }

    /**
     * Test grant permission variant of installPackageForUser throws exception on unsupported device
     * platform
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackageForUser_throw() throws Exception {
        final String apkFile = "foo.apk";
        setMockIDeviceRuntimePermissionNotSupported();

        try {
            mTestDevice.installPackageForUser(new File(apkFile), true, true, 123);
        } catch (UnsupportedOperationException uoe) {
            // ignore, exception thrown here is expected
            return;
        }
        fail("installPackage did not throw IllegalArgumentException");
    }

    /**
     * Test grant permission variant of installPackageForUser has expected list of args on a
     * supported device when granting
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackageForUser_grant_runtimePermissionSupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        int uid = 123;
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackageForUser(new File(apkFile), true, true, uid));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("-g"),
                        Mockito.eq("--user"),
                        Mockito.eq(Integer.toString(uid)));
    }

    /**
     * Test grant permission variant of installPackageForUser has expected list of args on a
     * supported device when not granting
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackageForUser_noGrant_runtimePermissionSupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apkFile = "foo.apk";
        int uid = 123;
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackageForUser(new File(apkFile), true, false, uid));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apkFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("--user"),
                        Mockito.eq(Integer.toString(uid)));
    }

    /**
     * Test installation of APEX is done with --apex flag.
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackage_apex() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    InstallReceiver createInstallReceiver() {
                        InstallReceiver receiver = new InstallReceiver();
                        receiver.processNewLines(new String[] {"Success"});
                        return receiver;
                    }
                };
        final String apexFile = "foo.apex";
        setMockIDeviceRuntimePermissionNotSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackage(new File(apexFile), true));
        verify(mMockIDevice, times(1))
                .installPackage(
                        Mockito.contains(apexFile),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_TO_OUTPUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES),
                        Mockito.eq("--apex"));
    }

    /** Test SplitApkInstaller with Split Apk Not Supported */
    @Test
    public void testInstallPackages_splitApkNotSupported() throws Exception {
        List<File> mLocalApks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            mLocalApks.add(File.createTempFile("test", ".apk"));
        }
        List<String> mInstallOptions = new ArrayList<String>();
        try {
            when(mMockIDevice.getVersion())
                    .thenReturn(
                            new AndroidVersion(
                                    AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel() - 1));

            SplitApkInstaller.create(mMockIDevice, mLocalApks, true, mInstallOptions);
            verify(mMockIDevice, times(1)).getVersion();
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // expected
        } finally {
            for (File apkFile : mLocalApks) {
                apkFile.delete();
            }
        }
    }

    /** Test SplitApkInstaller with Split Apk Supported */
    @Test
    public void testInstallPackages_splitApkSupported() throws Exception {
        List<File> mLocalApks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            mLocalApks.add(File.createTempFile("test", ".apk"));
        }
        List<String> mInstallOptions = new ArrayList<String>();
        try {
            when(mMockIDevice.getVersion())
                    .thenReturn(
                            new AndroidVersion(
                                    AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel()));
            SplitApkInstaller.create(mMockIDevice, mLocalApks, true, mInstallOptions);
            verify(mMockIDevice, atLeastOnce()).getVersion();
        } finally {
            for (File apkFile : mLocalApks) {
                apkFile.delete();
            }
        }
    }

    /**
     * Test default installPackages on device without runtime permission support list of args
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackages_default_runtimePermissionNotSupported() throws Exception {
        List<File> mLocalApks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            File apkFile = File.createTempFile("test", ".apk");
            mLocalApks.add(apkFile);
        }
        setMockIDeviceRuntimePermissionNotSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackages(mLocalApks, true));

        verifyMockIDeviceRuntimePermissionNotSupported();
        verifyMockIDeviceAppOpsToPersist();
        ArgumentCaptor<List<File>> filesCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> optionsCapture = ArgumentCaptor.forClass(List.class);
        verify(mMockIDevice, times(1))
                .installPackages(
                        filesCapture.capture(),
                        Mockito.eq(true),
                        optionsCapture.capture(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));
        assertTrue(optionsCapture.getValue().size() == 0);
        for (File apkFile : mLocalApks) {
            assertTrue(filesCapture.getValue().contains(apkFile));
            apkFile.delete();
        }
    }

    /**
     * Test default installPackages on device with runtime permission support list of args
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackages_default_runtimePermissionSupported() throws Exception {
        List<File> mLocalApks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            File apkFile = File.createTempFile("test", ".apk");
            mLocalApks.add(apkFile);
        }
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        assertNull(mTestDevice.installPackages(mLocalApks, true));

        verifyMockIDeviceRuntimePermissionSupported(2);
        verifyMockIDeviceAppOpsToPersist();
        ArgumentCaptor<List<File>> filesCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> optionsCapture = ArgumentCaptor.forClass(List.class);
        verify(mMockIDevice, times(1))
                .installPackages(
                        filesCapture.capture(),
                        Mockito.eq(true),
                        optionsCapture.capture(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));
        assertTrue(optionsCapture.getValue().contains("-g"));
        for (File apkFile : mLocalApks) {
            assertTrue(filesCapture.getValue().contains(apkFile));
            apkFile.delete();
        }
    }

    /** Test installPackagesForUser on device without runtime permission support */
    @Test
    public void testInstallPackagesForUser_default_runtimePermissionNotSupported()
            throws Exception {
        List<File> mLocalApks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            File apkFile = File.createTempFile("test", ".apk");
            mLocalApks.add(apkFile);
        }
        try {
            setMockIDeviceRuntimePermissionNotSupported();
            setMockIDeviceAppOpsToPersist();

            assertNull(mTestDevice.installPackagesForUser(mLocalApks, true, 1));

            verifyMockIDeviceRuntimePermissionNotSupported();
            verifyMockIDeviceAppOpsToPersist();
            ArgumentCaptor<List<String>> optionsCapture = ArgumentCaptor.forClass(List.class);
            verify(mMockIDevice, times(1))
                    .installPackages(
                            Mockito.eq(mLocalApks),
                            Mockito.eq(true),
                            optionsCapture.capture(),
                            Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                            Mockito.eq(TimeUnit.MINUTES));
            assertTrue(optionsCapture.getValue().contains("--user"));
            assertTrue(optionsCapture.getValue().contains("1"));
            assertFalse(optionsCapture.getValue().contains("-g"));
        } finally {
            for (File apkFile : mLocalApks) {
                FileUtil.deleteFile(apkFile);
            }
        }
    }

    /**
     * Test installPackagesForUser on device with runtime permission support
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackagesForUser_default_runtimePermissionSupported() throws Exception {
        List<File> mLocalApks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            File apkFile = File.createTempFile("test", ".apk");
            mLocalApks.add(apkFile);
        }
        try {
            setMockIDeviceRuntimePermissionSupported();
            setMockIDeviceAppOpsToPersist();

            assertNull(mTestDevice.installPackagesForUser(mLocalApks, true, 1));

            ArgumentCaptor<List<String>> optionsCapture = ArgumentCaptor.forClass(List.class);
            verify(mMockIDevice, times(1))
                    .installPackages(
                            Mockito.eq(mLocalApks),
                            Mockito.eq(true),
                            optionsCapture.capture(),
                            Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                            Mockito.eq(TimeUnit.MINUTES));
            assertTrue(optionsCapture.getValue().contains("--user"));
            assertTrue(optionsCapture.getValue().contains("1"));
            assertTrue(optionsCapture.getValue().contains("-g"));
            verifyMockIDeviceRuntimePermissionSupported(2);
            verifyMockIDeviceAppOpsToPersist();
        } finally {
            for (File apkFile : mLocalApks) {
                FileUtil.deleteFile(apkFile);
            }
        }
    }

    /**
     * Test installPackages timeout on device with runtime permission support list of args
     *
     * @throws Exception
     */
    @Test
    public void testInstallPackages_default_timeout() throws Exception {
        List<File> mLocalApks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            File apkFile = File.createTempFile("test", ".apk");
            mLocalApks.add(apkFile);
        }
        setMockIDeviceRuntimePermissionSupported();
        setMockIDeviceAppOpsToPersist();

        doThrow(new InstallException(new TimeoutException()))
                .when(mMockIDevice)
                .installPackages(
                        Mockito.any(),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));

        assertTrue(mTestDevice.installPackages(mLocalApks, true).contains("InstallException"));

        ArgumentCaptor<List<File>> filesCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> optionsCapture = ArgumentCaptor.forClass(List.class);
        verify(mMockIDevice)
                .installPackages(
                        filesCapture.capture(),
                        Mockito.eq(true),
                        optionsCapture.capture(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));
        assertTrue(optionsCapture.getValue().contains("-g"));
        verifyMockIDeviceRuntimePermissionSupported(2);
        verifyMockIDeviceAppOpsToPersist();
        for (File apkFile : mLocalApks) {
            assertTrue(filesCapture.getValue().contains(apkFile));
            FileUtil.deleteFile(apkFile);
        }
    }

    /**
     * Test default installRemotePackagses on device without runtime permission support list of args
     *
     * @throws Exception
     */
    @Test
    public void testInstallRemotePackages_default_runtimePermissionNotSupported() throws Exception {
        List<String> mRemoteApkPaths = new ArrayList<String>();
        mRemoteApkPaths.add("/data/local/tmp/foo.apk");
        mRemoteApkPaths.add("/data/local/tmp/foo.dm");
        setMockIDeviceRuntimePermissionNotSupported();

        assertNull(mTestDevice.installRemotePackages(mRemoteApkPaths, true));

        verifyMockIDeviceRuntimePermissionNotSupported();
        ArgumentCaptor<List<String>> filePathsCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> optionsCapture = ArgumentCaptor.forClass(List.class);
        verify(mMockIDevice, times(1))
                .installRemotePackages(
                        filePathsCapture.capture(),
                        Mockito.eq(true),
                        optionsCapture.capture(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));
        assertTrue(optionsCapture.getValue().size() == 0);
        for (String apkPath : mRemoteApkPaths) {
            assertTrue(filePathsCapture.getValue().contains(apkPath));
        }
    }

    /**
     * Test default installRemotePackages on device with runtime permission support list of args
     *
     * @throws Exception
     */
    @Test
    public void testInstallRemotePackages_default_runtimePermissionSupported() throws Exception {
        List<String> mRemoteApkPaths = new ArrayList<String>();
        mRemoteApkPaths.add("/data/local/tmp/foo.apk");
        mRemoteApkPaths.add("/data/local/tmp/foo.dm");
        setMockIDeviceRuntimePermissionSupported();

        assertNull(mTestDevice.installRemotePackages(mRemoteApkPaths, true));

        verifyMockIDeviceRuntimePermissionSupported(2);
        ArgumentCaptor<List<String>> filePathsCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> optionsCapture = ArgumentCaptor.forClass(List.class);
        verify(mMockIDevice, times(1))
                .installRemotePackages(
                        filePathsCapture.capture(),
                        Mockito.eq(true),
                        optionsCapture.capture(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));
        assertTrue(optionsCapture.getValue().contains("-g"));
        for (String apkPath : mRemoteApkPaths) {
            assertTrue(filePathsCapture.getValue().contains(apkPath));
        }
    }

    /**
     * Test installRemotePackages timeout on device with runtime permission support list of args
     *
     * @throws Exception
     */
    @Test
    public void testInstallRemotePackages_default_timeout() throws Exception {
        List<String> mRemoteApkPaths = new ArrayList<String>();
        mRemoteApkPaths.add("/data/local/tmp/foo.apk");
        mRemoteApkPaths.add("/data/local/tmp/foo.dm");
        setMockIDeviceRuntimePermissionSupported();

        ArgumentCaptor<List<String>> filePathsCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> optionsCapture = ArgumentCaptor.forClass(List.class);
        doThrow(new InstallException(new TimeoutException()))
                .when(mMockIDevice)
                .installRemotePackages(
                        Mockito.any(),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));

        assertTrue(
                mTestDevice
                        .installRemotePackages(mRemoteApkPaths, true)
                        .contains("InstallException during package installation."));

        verify(mMockIDevice)
                .installRemotePackages(
                        filePathsCapture.capture(),
                        Mockito.eq(true),
                        optionsCapture.capture(),
                        Mockito.eq(TestDevice.INSTALL_TIMEOUT_MINUTES),
                        Mockito.eq(TimeUnit.MINUTES));
        assertTrue(optionsCapture.getValue().contains("-g"));
        verifyMockIDeviceRuntimePermissionSupported(2);
        for (String apkPath : mRemoteApkPaths) {
            assertTrue(filePathsCapture.getValue().contains(apkPath));
        }
    }

    /**
     * Helper method to build a response to a executeShellCommand call
     *
     * @param expectedCommand the shell command to expect or null to skip verification of command
     * @param response the response to simulate
     */
    private void injectShellResponse(final String expectedCommand, final String response)
            throws Exception {
        Answer<Object> shellAnswer =
                invocation -> {
                    IShellOutputReceiver receiver =
                            (IShellOutputReceiver) invocation.getArguments()[1];
                    byte[] inputData = response.getBytes();
                    CLog.i("++++ %s -> %s (asStub=false)", expectedCommand, response);
                    receiver.addOutput(inputData, 0, inputData.length);
                    return null;
                };
        if (expectedCommand != null) {
            doAnswer(shellAnswer)
                    .when(mMockIDevice)
                    .executeShellCommand(
                            Mockito.eq(expectedCommand),
                            Mockito.<IShellOutputReceiver>any(),
                            Mockito.anyLong(),
                            Mockito.<TimeUnit>any());
        } else {
            doAnswer(shellAnswer)
                    .when(mMockIDevice)
                    .executeShellCommand(
                            Mockito.<String>any(),
                            Mockito.<IShellOutputReceiver>any(),
                            Mockito.anyLong(),
                            Mockito.<TimeUnit>any());
        }
    }

    private void injectShellResponse(final String expectedCommand, IShellResponse shellResponse)
            throws Exception {
        Answer<Object> shellAnswer =
                invocation -> {
                    IShellOutputReceiver receiver =
                            (IShellOutputReceiver) invocation.getArguments()[1];
                    String response = shellResponse.getResponse();
                    byte[] inputData = response.getBytes();
                    receiver.addOutput(inputData, 0, inputData.length);
                    return null;
                };
        if (expectedCommand != null) {
            doAnswer(shellAnswer)
                    .when(mMockIDevice)
                    .executeShellCommand(
                            Mockito.eq(expectedCommand),
                            Mockito.<IShellOutputReceiver>any(),
                            Mockito.anyLong(),
                            Mockito.<TimeUnit>any());
        } else {
            doAnswer(shellAnswer)
                    .when(mMockIDevice)
                    .executeShellCommand(
                            Mockito.<String>any(),
                            Mockito.<IShellOutputReceiver>any(),
                            Mockito.anyLong(),
                            Mockito.<TimeUnit>any());
        }
    }

    private void verifyShellResponse(final String expectedCommand) throws Exception {
        if (expectedCommand != null) {
            verify(mMockIDevice)
                    .executeShellCommand(
                            Mockito.eq(expectedCommand),
                            Mockito.<IShellOutputReceiver>any(),
                            Mockito.anyLong(),
                            Mockito.<TimeUnit>any());
        } else {
            verify(mMockIDevice)
                    .executeShellCommand(
                            Mockito.<String>any(),
                            Mockito.<IShellOutputReceiver>any(),
                            Mockito.anyLong(),
                            Mockito.<TimeUnit>any());
        }
    }

    private void verifyShellResponse(final String expectedCommand, int times) throws Exception {
        if (expectedCommand != null) {
            verify(mMockIDevice, times(times))
                    .executeShellCommand(
                            Mockito.eq(expectedCommand),
                            Mockito.<IShellOutputReceiver>any(),
                            Mockito.anyLong(),
                            Mockito.<TimeUnit>any());
        } else {
            verify(mMockIDevice, times(times))
                    .executeShellCommand(
                            Mockito.<String>any(),
                            Mockito.<IShellOutputReceiver>any(),
                            Mockito.anyLong(),
                            Mockito.<TimeUnit>any());
        }
    }
    /**
     * Helper method to inject a response to {@link TestDevice#getProperty(String)} calls
     *
     * @param property property name
     * @param value property value
     * @return preset {@link IExpectationSetters} returned by {@link Mockito} where further
     *     expectations can be added
     */
    private void injectSystemProperty(final String property, final String value) {
        setGetPropertyExpectation(property, value);
    }

    private void verifySystemProperty(final String property, final String value, int times) {
        verifyGetPropertyExpectation(property, value, times);
    }

    /**
     * Helper method to build response to a reboot call
     *
     * @throws Exception
     */
    private void setRebootExpectations() throws Exception {
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);

        mMockShellResponse = mock(IShellResponse.class);
        when(mMockShellResponse.getResponse())
                .thenReturn(
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)",
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)");
        injectShellResponse("id", mMockShellResponse);

        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as root");

        setExecuteAdbCommandExpectations(adbResult, "root");

        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong()))
                .thenReturn(Boolean.TRUE);
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);

        setGetPropertyExpectation("ro.crypto.state", "unsupported");

        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);
    }

    private void verifyRebootExpectations() throws Exception {
        verify(mMockStateMonitor, times(3)).waitForDeviceOnline();
        verifyShellResponse("id", 4);
        verifyGetPropertyExpectation("ro.crypto.state", "unsupported", 1);
        verify(mMockStateMonitor).waitForDeviceAvailable(Mockito.anyLong());
    }

    /** Test normal success case for {@link TestDevice#reboot()} */
    @Test
    public void testReboot() throws Exception {
        setRebootExpectations();

        mTestDevice.reboot();
        verifyRebootExpectations();
    }

    /** Test {@link TestDevice#reboot()} attempts a recovery upon failure */
    @Test
    public void testRebootRecovers() throws Exception {
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);
        mMockShellResponse = mock(IShellResponse.class);
        when(mMockShellResponse.getResponse())
                .thenReturn(
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)",
                        "uid=2000(shell) gid=2000(shell)",
                        "uid=0(root) gid=0(root)");

        injectShellResponse("id", mMockShellResponse);
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("restarting adbd as root");
        setExecuteAdbCommandExpectations(adbResult, "root");
        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong()))
                .thenReturn(Boolean.TRUE);
        when(mMockStateMonitor.waitForDeviceOnline()).thenReturn(mMockIDevice);
        setGetPropertyExpectation("ro.crypto.state", "unsupported");
        when(mMockStateMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(null);

        mRecoveryTestDevice.reboot();

        verifyShellResponse("id", 4);
        verifyGetPropertyExpectation("ro.crypto.state", "unsupported", 1);
        verify(mMockRecovery).recoverDevice(mMockStateMonitor, false);
    }

    /** Unit test for {@link TestDevice#getInstalledPackageNames()}. */
    @Test
    public void testGetInstalledPackageNames() throws Exception {
        final String output =
                "package:/system/app/LiveWallpapers.apk=com.android.wallpaper\n"
                    + "package:/system/app/LiveWallpapersPicker.apk=com.android.wallpaper.livepicker";
        injectShellResponse(TestDevice.LIST_PACKAGES_CMD, output);

        Set<String> actualPkgs = mTestDevice.getInstalledPackageNames();
        assertEquals(2, actualPkgs.size());
        assertTrue(actualPkgs.contains("com.android.wallpaper"));
        assertTrue(actualPkgs.contains("com.android.wallpaper.livepicker"));
        verifyShellResponse(TestDevice.LIST_PACKAGES_CMD);
    }

    /** Unit test for {@link TestDevice#isPackageInstalled(String, String)}. */
    @Test
    public void testIsPackageInstalled() throws Exception {
        final String output =
                "package:/system/app/LiveWallpapers.apk=com.android.wallpaper\n"
                        + "package:/system/app/LiveWallpapersPicker.apk="
                        + "com.android.wallpaper.livepicker";
        injectShellResponse(TestDevice.LIST_PACKAGES_CMD + " | grep com.android.wallpaper", output);

        assertTrue(mTestDevice.isPackageInstalled("com.android.wallpaper", null));
        verifyShellResponse(TestDevice.LIST_PACKAGES_CMD + " | grep com.android.wallpaper");
    }

    /** Unit test for {@link TestDevice#isPackageInstalled(String, String)}. */
    @Test
    public void testIsPackageInstalled_withUser() throws Exception {
        final String output =
                "package:/system/app/LiveWallpapers.apk=com.android.wallpaper\n"
                        + "package:/system/app/LiveWallpapersPicker.apk="
                        + "com.android.wallpaper.livepicker";
        injectShellResponse(
                TestDevice.LIST_PACKAGES_CMD + " --user 1 | grep com.android.wallpaper", output);

        assertTrue(mTestDevice.isPackageInstalled("com.android.wallpaper", "1"));
        verifyShellResponse(
                TestDevice.LIST_PACKAGES_CMD + " --user 1 | grep com.android.wallpaper");
    }

    /**
     * Unit test for {@link TestDevice#getInstalledPackageNames()}.
     *
     * <p>Test bad output.
     */
    @Test
    public void testGetInstalledPackageNamesForBadOutput() throws Exception {
        final String output = "junk output";
        injectShellResponse(TestDevice.LIST_PACKAGES_CMD, output);

        Set<String> actualPkgs = mTestDevice.getInstalledPackageNames();
        assertEquals(0, actualPkgs.size());
    }

    /** Unit test for {@link TestDevice#getActiveApexes()}. */
    @Test
    public void testGetActiveApexesPlatformSupportsPath() throws Exception {
        final String output =
                "package:/system/apex/com.android.foo.apex="
                        + "com.android.foo versionCode:100\n"
                        + "package:/system/apex/com.android.bar.apex="
                        + "com.android.bar versionCode:200";
        injectShellResponse(TestDevice.LIST_APEXES_CMD, output);

        Set<ApexInfo> actual = mTestDevice.getActiveApexes();
        assertEquals(2, actual.size());
        ApexInfo foo =
                actual.stream()
                        .filter(apex -> apex.name.equals("com.android.foo"))
                        .findFirst()
                        .get();
        ApexInfo bar =
                actual.stream()
                        .filter(apex -> apex.name.equals("com.android.bar"))
                        .findFirst()
                        .get();
        assertEquals(100, foo.versionCode);
        assertEquals(200, bar.versionCode);
        assertEquals("/system/apex/com.android.foo.apex", foo.sourceDir);
        assertEquals("/system/apex/com.android.bar.apex", bar.sourceDir);
    }

    /** Unit test for {@link TestDevice#getActiveApexes()}. */
    @Test
    public void testGetActiveApexesPlatformDoesNotSupportPath() throws Exception {
        final String output =
                "package:com.android.foo versionCode:100\n"
                        + "package:com.android.bar versionCode:200";
        injectShellResponse(TestDevice.LIST_APEXES_CMD, output);

        Set<ApexInfo> actual = mTestDevice.getActiveApexes();
        assertEquals(2, actual.size());
        ApexInfo foo =
                actual.stream()
                        .filter(apex -> apex.name.equals("com.android.foo"))
                        .findFirst()
                        .get();
        ApexInfo bar =
                actual.stream()
                        .filter(apex -> apex.name.equals("com.android.bar"))
                        .findFirst()
                        .get();
        assertEquals(100, foo.versionCode);
        assertEquals(200, bar.versionCode);
        assertEquals("", foo.sourceDir);
        assertEquals("", bar.sourceDir);
    }

    /**
     * Unit test for {@link TestDevice#getActiveApexes()}.
     *
     * <p>Test bad output.
     */
    @Test
    public void testGetActiveApexesForBadOutput() throws Exception {
        final String output = "junk output";
        injectShellResponse(TestDevice.LIST_APEXES_CMD, output);

        Set<ApexInfo> actual = mTestDevice.getActiveApexes();
        assertEquals(0, actual.size());
    }

    /** Unit test for {@link TestDevice#getMainlineModuleInfo()}. */
    @Test
    public void testGetMainlineModuleInfo() throws Exception {
        final String output =
                "ModuleInfo{foo123456 Module NameFoo} packageName: com.android.foo\n"
                        + "ModuleInfo{bar123456 Module NameBar} packageName: com.android.bar";
        injectSystemProperty("ro.build.version.sdk", "29");
        injectSystemProperty(DeviceProperties.BUILD_CODENAME, "REL");
        injectShellResponse(TestDevice.GET_MODULEINFOS_CMD, output);
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        Set<String> actual = mTestDevice.getMainlineModuleInfo();
        Set<String> expected = new HashSet<>(Arrays.asList("com.android.foo", "com.android.bar"));
        assertEquals(2, actual.size());
        assertEquals(expected, actual);
    }

    /**
     * Unit test for {@link TestDevice#getMainlineModuleInfo()}.
     *
     * <p>Test bad output.
     */
    @Test
    public void testGetMainlineModuleInfoForBadOutput() throws Exception {
        final String output = "junk output";
        injectSystemProperty("ro.build.version.sdk", "29");
        injectSystemProperty(DeviceProperties.BUILD_CODENAME, "REL");
        injectShellResponse(TestDevice.GET_MODULEINFOS_CMD, output);
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        Set<String> actual = mTestDevice.getMainlineModuleInfo();
        assertEquals(0, actual.size());
    }

    /**
     * Unit test to make sure that the simple convenience constructor for {@link
     * MountPointInfo#MountPointInfo(String, String, String, List)} works as expected.
     */
    @Test
    public void testMountInfo_simple() throws Exception {
        List<String> empty = Collections.emptyList();
        MountPointInfo info = new MountPointInfo("filesystem", "mountpoint", "type", empty);
        assertEquals("filesystem", info.filesystem);
        assertEquals("mountpoint", info.mountpoint);
        assertEquals("type", info.type);
        assertEquals(empty, info.options);
    }

    /**
     * Unit test to make sure that the mount-option-parsing convenience constructor for {@link
     * MountPointInfo#MountPointInfo(String, String, String, List)} works as expected.
     */
    @Test
    public void testMountInfo_parseOptions() throws Exception {
        MountPointInfo info = new MountPointInfo("filesystem", "mountpoint", "type", "rw,relatime");
        assertEquals("filesystem", info.filesystem);
        assertEquals("mountpoint", info.mountpoint);
        assertEquals("type", info.type);

        // options should be parsed
        assertNotNull(info.options);
        assertEquals(2, info.options.size());
        assertEquals("rw", info.options.get(0));
        assertEquals("relatime", info.options.get(1));
    }

    /** A unit test to ensure {@link TestDevice#getMountPointInfo()} works as expected. */
    @Test
    public void testGetMountPointInfo() throws Exception {
        String response =
                ArrayUtil.join(
                        "\r\n",
                        "rootfs / rootfs ro,relatime 0 0",
                        "tmpfs /dev tmpfs rw,nosuid,relatime,mode=755 0 0",
                        "devpts /dev/pts devpts rw,relatime,mode=600 0 0",
                        "proc /proc proc rw,relatime 0 0",
                        "sysfs /sys sysfs rw,relatime 0 0",
                        "none /acct cgroup rw,relatime,cpuacct 0 0",
                        "tmpfs /mnt/asec tmpfs rw,relatime,mode=755,gid=1000 0 0",
                        "tmpfs /mnt/obb tmpfs rw,relatime,mode=755,gid=1000 0 0",
                        "none /dev/cpuctl cgroup rw,relatime,cpu 0 0",
                        "/dev/block/vold/179:3 /mnt/secure/asec vfat rw,dirsync,nosuid,nodev,"
                            + "noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702,"
                            + "allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro"
                            + " 0 0",
                        "tmpfs /storage/sdcard0/.android_secure tmpfs "
                                + "ro,relatime,size=0k,mode=000 0 0");
        injectShellResponse("cat /proc/mounts", response);

        List<MountPointInfo> info = mTestDevice.getMountPointInfo();
        verifyShellResponse("cat /proc/mounts");
        assertEquals(11, info.size());

        // spot-check
        MountPointInfo mpi = info.get(0);
        assertEquals("rootfs", mpi.filesystem);
        assertEquals("/", mpi.mountpoint);
        assertEquals("rootfs", mpi.type);
        assertEquals(2, mpi.options.size());
        assertEquals("ro", mpi.options.get(0));
        assertEquals("relatime", mpi.options.get(1));

        mpi = info.get(9);
        assertEquals("/dev/block/vold/179:3", mpi.filesystem);
        assertEquals("/mnt/secure/asec", mpi.mountpoint);
        assertEquals("vfat", mpi.type);
        assertEquals(16, mpi.options.size());
        assertEquals("dirsync", mpi.options.get(1));
        assertEquals("errors=remount-ro", mpi.options.get(15));
    }

    /** A unit test to ensure {@link TestDevice#getMountPointInfo(String)} works as expected. */
    @Test
    public void testGetMountPointInfo_filter() throws Exception {
        injectShellResponse(
                "cat /proc/mounts",
                ArrayUtil.join(
                        "\r\n",
                        "rootfs / rootfs ro,relatime 0 0",
                        "tmpfs /dev tmpfs rw,nosuid,relatime,mode=755 0 0",
                        "devpts /dev/pts devpts rw,relatime,mode=600 0 0",
                        "proc /proc proc rw,relatime 0 0",
                        "sysfs /sys sysfs rw,relatime 0 0",
                        "none /acct cgroup rw,relatime,cpuacct 0 0",
                        "tmpfs /mnt/asec tmpfs rw,relatime,mode=755,gid=1000 0 0",
                        "tmpfs /mnt/obb tmpfs rw,relatime,mode=755,gid=1000 0 0",
                        "none /dev/cpuctl cgroup rw,relatime,cpu 0 0",
                        "/dev/block/vold/179:3 /mnt/secure/asec vfat rw,dirsync,nosuid,nodev,"
                            + "noexec,relatime,uid=1000,gid=1015,fmask=0702,dmask=0702,"
                            + "allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro"
                            + " 0 0",
                        "tmpfs /storage/sdcard0/.android_secure tmpfs "
                                + "ro,relatime,size=0k,mode=000 0 0"));

        MountPointInfo mpi = mTestDevice.getMountPointInfo("/mnt/secure/asec");
        assertEquals("/dev/block/vold/179:3", mpi.filesystem);
        assertEquals("/mnt/secure/asec", mpi.mountpoint);
        assertEquals("vfat", mpi.type);
        assertEquals(16, mpi.options.size());
        assertEquals("dirsync", mpi.options.get(1));
        assertEquals("errors=remount-ro", mpi.options.get(15));

        assertNull(mTestDevice.getMountPointInfo("/a/mountpoint/too/far"));
    }

    @Test
    public void testParseFreeSpaceFromFree() throws Exception {
        assertNotNull(
                "Failed to parse free space size with decimal point",
                mTestDevice.parseFreeSpaceFromFree(
                        "/storage/emulated/legacy",
                        "/storage/emulated/legacy    13.2G   296.4M    12.9G   4096"));
        assertNotNull(
                "Failed to parse integer free space size",
                mTestDevice.parseFreeSpaceFromFree(
                        "/storage/emulated/legacy",
                        "/storage/emulated/legacy     13G   395M    12G   4096"));
    }

    @Test
    public void testIsDeviceInputReady_Ready() throws Exception {
        injectShellResponse(
                "dumpsys input",
                ArrayUtil.join(
                        "\r\n",
                        getDumpsysInputHeader(),
                        "  DispatchEnabled: 1",
                        "  DispatchFrozen: 0",
                        "  FocusedApplication: <null>",
                        "  FocusedWindow: name='Window{2920620f u0 com.android.launcher/"
                                + "com.android.launcher2.Launcher}'",
                        "  TouchStates: <no displays touched>"));

        assertTrue(mTestDevice.isDeviceInputReady());
    }

    @Test
    public void testIsDeviceInputReady_NotReady() throws Exception {
        injectShellResponse(
                "dumpsys input",
                ArrayUtil.join(
                        "\r\n",
                        getDumpsysInputHeader(),
                        "  DispatchEnabled: 0",
                        "  DispatchFrozen: 0",
                        "  FocusedApplication: <null>",
                        "  FocusedWindow: name='Window{2920620f u0 com.android.launcher/"
                                + "com.android.launcher2.Launcher}'",
                        "  TouchStates: <no displays touched>"));

        assertFalse(mTestDevice.isDeviceInputReady());
    }

    @Test
    public void testIsDeviceInputReady_NotSupported() throws Exception {
        injectShellResponse(
                "dumpsys input", ArrayUtil.join("\r\n", "foo", "bar", "foobar", "barfoo"));

        assertNull(mTestDevice.isDeviceInputReady());
    }

    private static String getDumpsysInputHeader() {
        return ArrayUtil.join(
                "\r\n",
                "INPUT MANAGER (dumpsys input)",
                "",
                "Event Hub State:",
                "  BuiltInKeyboardId: -2",
                "  Devices:",
                "    -1: Virtual",
                "      Classes: 0x40000023",
                "Input Dispatcher State:");
    }

    /** Simple test for {@link TestDevice#handleAllocationEvent(DeviceEvent)} */
    @Test
    public void testHandleAllocationEvent() {
        when(mMockIDevice.getSerialNumber()).thenReturn(MOCK_DEVICE_SERIAL);

        assertEquals(DeviceAllocationState.Unknown, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.CONNECTED_ONLINE));
        assertEquals(DeviceAllocationState.Checking_Availability, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.AVAILABLE_CHECK_PASSED));
        assertEquals(DeviceAllocationState.Available, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST));
        assertEquals(DeviceAllocationState.Allocated, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.FREE_AVAILABLE));
        assertEquals(DeviceAllocationState.Available, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.DISCONNECTED));
        assertEquals(DeviceAllocationState.Unknown, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST));
        assertEquals(DeviceAllocationState.Allocated, mTestDevice.getAllocationState());

        assertNotNull(mTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN));
        assertEquals(DeviceAllocationState.Unknown, mTestDevice.getAllocationState());
    }

    /** Test that a single user is handled by {@link TestDevice#listUsers()}. */
    @Test
    public void testListUsers_oneUser() throws Exception {
        final String listUsersCommand = "pm list users";
        injectShellResponse(
                listUsersCommand, ArrayUtil.join("\r\n", "Users:", "UserInfo{0:Foo:13} running"));

        ArrayList<Integer> actual = mTestDevice.listUsers();
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals(0, actual.get(0).intValue());
    }

    /** Test that invalid output is handled by {@link TestDevice#listUsers()}. */
    @Test
    public void testListUsers_invalidOutput() throws Exception {
        final String listUsersCommand = "pm list users";
        final String output = "not really what we are looking for";
        injectShellResponse(listUsersCommand, output);

        try {
            mTestDevice.listUsers();
            fail("Failed to throw DeviceRuntimeException.");
        } catch (DeviceRuntimeException expected) {
            // expected
            assertEquals(
                    String.format("'%s' in not a valid output for 'pm list users'", output),
                    expected.getMessage());
        }
    }

    /** Test that invalid format of users is handled by {@link TestDevice#listUsers()}. */
    @Test
    public void testListUsers_unparsableOutput() throws Exception {
        final String listUsersCommand = "pm list users";
        final String output = "Users:\n" + "\tUserInfo{0:Ownertooshort}";
        injectShellResponse(listUsersCommand, output);

        try {
            mTestDevice.listUsers();
            fail("Failed to throw DeviceRuntimeException.");
        } catch (DeviceRuntimeException expected) {
            // expected
            assertEquals(
                    String.format(
                            "device output: '%s' \n"
                                    + "line: '\tUserInfo{0:Ownertooshort}' was not in the expected"
                                    + " format for user info.",
                            output),
                    expected.getMessage());
        }
    }

    /** Test that multiple user is handled by {@link TestDevice#listUsers()}. */
    @Test
    public void testListUsers_multiUsers() throws Exception {
        final String listUsersCommand = "pm list users";
        injectShellResponse(
                listUsersCommand,
                ArrayUtil.join(
                        "\r\n", "Users:", "UserInfo{0:Foo:13} running", "UserInfo{3:FooBar:14}"));

        ArrayList<Integer> actual = mTestDevice.listUsers();
        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals(0, actual.get(0).intValue());
        assertEquals(3, actual.get(1).intValue());
    }

    /** Test that a single user is handled by {@link TestDevice#listUsers()}. */
    @Test
    public void testListUsersInfo_oneUser() throws Exception {
        final String listUsersCommand = "pm list users";
        injectShellResponse(
                listUsersCommand, ArrayUtil.join("\r\n", "Users:", "UserInfo{0:Foo:13} running"));

        Map<Integer, UserInfo> actual = mTestDevice.getUserInfos();
        assertNotNull(actual);
        assertEquals(1, actual.size());
        UserInfo user0 = actual.get(0);
        assertEquals(0, user0.userId());
        assertEquals("Foo", user0.userName());
        assertEquals(0x13, user0.flag());
        assertEquals(true, user0.isRunning());
    }

    /** Test that multiple user is handled by {@link TestDevice#listUsers()}. */
    @Test
    public void testListUsersInfo_multiUsers() throws Exception {
        final String listUsersCommand = "pm list users";
        injectShellResponse(
                listUsersCommand,
                ArrayUtil.join(
                        "\r\n", "Users:", "UserInfo{0:Foo:13} running", "UserInfo{10:FooBar:14}"));

        Map<Integer, UserInfo> actual = mTestDevice.getUserInfos();
        assertNotNull(actual);
        assertEquals(2, actual.size());
        UserInfo user0 = actual.get(0);
        assertEquals(0, user0.userId());
        assertEquals("Foo", user0.userName());
        assertEquals(0x13, user0.flag());
        assertEquals(true, user0.isRunning());

        UserInfo user10 = actual.get(10);
        assertEquals(10, user10.userId());
        assertEquals("FooBar", user10.userName());
        assertEquals(0x14, user10.flag());
        assertEquals(false, user10.isRunning());
    }

    /**
     * Test that multi user output is handled by {@link TestDevice#getMaxNumberOfUsersSupported()}.
     */
    @Test
    public void testMaxNumberOfUsersSupported() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "Maximum supported users: 4");

        assertEquals(4, mTestDevice.getMaxNumberOfUsersSupported());
    }

    /** Test that invalid output is handled by {@link TestDevice#getMaxNumberOfUsersSupported()}. */
    @Test
    public void testMaxNumberOfUsersSupported_invalid() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "not the output we expect");

        assertEquals(0, mTestDevice.getMaxNumberOfUsersSupported());
    }

    /**
     * Test that multi user output is handled by {@link TestDevice#getMaxNumberOfUsersSupported()}.
     */
    @Test
    public void testMaxNumberOfRunningUsersSupported() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "28");
        injectSystemProperty(DeviceProperties.BUILD_CODENAME, "REL");
        final String getMaxRunningUsersCommand = "pm get-max-running-users";
        injectShellResponse(getMaxRunningUsersCommand, "Maximum supported running users: 4");

        assertEquals(4, mTestDevice.getMaxNumberOfRunningUsersSupported());
    }

    /** Test that invalid output is handled by {@link TestDevice#getMaxNumberOfUsersSupported()}. */
    @Test
    public void testMaxNumberOfRunningUsersSupported_invalid() throws Exception {
        injectSystemProperty("ro.build.version.sdk", "28");
        injectSystemProperty(DeviceProperties.BUILD_CODENAME, "REL");
        final String getMaxRunningUsersCommand = "pm get-max-running-users";
        injectShellResponse(getMaxRunningUsersCommand, "not the output we expect");

        assertEquals(0, mTestDevice.getMaxNumberOfRunningUsersSupported());
    }

    /**
     * Test that single user output is handled by {@link TestDevice#getMaxNumberOfUsersSupported()}.
     */
    @Test
    public void testIsMultiUserSupported_singleUser() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "Maximum supported users: 1");

        assertFalse(mTestDevice.isMultiUserSupported());
    }

    /** Test that {@link TestDevice#isMultiUserSupported()} works. */
    @Test
    public void testIsMultiUserSupported() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "Maximum supported users: 4");

        assertTrue(mTestDevice.isMultiUserSupported());
    }

    /** Test that invalid output is handled by {@link TestDevice#isMultiUserSupported()}. */
    @Test
    public void testIsMultiUserSupported_invalidOutput() throws Exception {
        final String getMaxUsersCommand = "pm get-max-users";
        injectShellResponse(getMaxUsersCommand, "not the output we expect");

        assertFalse(mTestDevice.isMultiUserSupported());
    }

    /** Test that successful user creation is handled by {@link TestDevice#createUser(String)}. */
    @Test
    public void testCreateUser() throws Exception {
        final String createUserCommand = "pm create-user foo";
        injectShellResponse(createUserCommand, "Success: created user id 10");

        assertEquals(10, mTestDevice.createUser("foo"));
    }

    /**
     * Test that successful user creation is handled by {@link TestDevice#createUser(String,
     * boolean, boolean)}.
     */
    @Test
    public void testCreateUserFlags() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Success: created user id 12";
                    }
                };
        assertEquals(12, mTestDevice.createUser("TEST", true, true));
    }

    /** Test that {@link TestDevice#createUser(String, boolean, boolean)} fails when bad output */
    @Test
    public void testCreateUser_wrongOutput() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Success: created user id WRONG";
                    }
                };
        try {
            mTestDevice.createUser("TEST", true, true);
        } catch (IllegalStateException e) {
            // expected
            return;
        }
        fail("CreateUser should have thrown an exception");
    }

    /** Test that a failure to create a user is handled by {@link TestDevice#createUser(String)}. */
    @Test
    public void testCreateUser_failed() throws Exception {
        final String createUserCommand = "pm create-user foo";
        injectShellResponse(createUserCommand, "Error");

        try {
            mTestDevice.createUser("foo");
            fail("IllegalStateException not thrown");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    /**
     * Test that successful user creation is handled by {@link
     * TestDevice#createUserNoThrow(String)}.
     */
    @Test
    public void testCreateUserNoThrow() throws Exception {
        final String createUserCommand = "pm create-user foo";
        injectShellResponse(createUserCommand, "Success: created user id 10");

        assertEquals(10, mTestDevice.createUserNoThrow("foo"));
    }

    /** Test that {@link TestDevice#createUserNoThrow(String)} fails when bad output */
    @Test
    public void testCreateUserNoThrow_wrongOutput() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Success: created user id WRONG";
                    }
                };

        assertEquals(-1, mTestDevice.createUserNoThrow("TEST"));
    }

    /** Test that successful user removal is handled by {@link TestDevice#removeUser(int)}. */
    @Test
    public void testRemoveUser() throws Exception {
        final String removeUserCommand = "pm remove-user 10";
        injectShellResponse(removeUserCommand, "Success: removed user\n");

        assertTrue(mTestDevice.removeUser(10));
    }

    /** Test that a failure to remove a user is handled by {@link TestDevice#removeUser(int)}. */
    @Test
    public void testRemoveUser_failed() throws Exception {
        final String removeUserCommand = "pm remove-user 10";
        injectShellResponse(removeUserCommand, "Error: couldn't remove user id 10");

        assertFalse(mTestDevice.removeUser(10));
    }

    /**
     * Test that trying to run a test with a user with {@link
     * TestDevice#runInstrumentationTestsAsUser(IRemoteAndroidTestRunner, int, Collection)} fails if
     * the {@link IRemoteAndroidTestRunner} is not an instance of {@link RemoteAndroidTestRunner}.
     */
    @Test
    public void testrunInstrumentationTestsAsUser_failed() throws Exception {
        IRemoteAndroidTestRunner mockRunner = mock(IRemoteAndroidTestRunner.class);
        when(mockRunner.getPackageName()).thenReturn("com.example");
        Collection<ITestLifeCycleReceiver> listeners = new ArrayList<>();

        try {
            mTestDevice.runInstrumentationTestsAsUser(mockRunner, 12, listeners);
            fail("IllegalStateException not thrown.");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /** Test that successful user start is handled by {@link TestDevice#startUser(int)}. */
    @Test
    public void testStartUser() throws Exception {
        final String startUserCommand = "am start-user 10";
        injectShellResponse(startUserCommand, "Success: user started\n");

        assertTrue(mTestDevice.startUser(10));
    }

    /** Test that a failure to start user is handled by {@link TestDevice#startUser(int)}. */
    @Test
    public void testStartUser_failed() throws Exception {
        final String startUserCommand = "am start-user 10";
        injectShellResponse(startUserCommand, "Error: could not start user\n");

        assertFalse(mTestDevice.startUser(10));
    }

    /** Test that successful user start is handled by {@link TestDevice#startUser(int, boolean)}. */
    @Test
    public void testStartUser_wait() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 29;
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "Q\n";
                    }
                };
        injectShellResponse("am start-user -w 10", "Success: user started\n");
        injectShellResponse("am get-started-user-state 10", "RUNNING_UNLOCKED\n");

        assertTrue(mTestDevice.startUser(10, true));
    }

    /** Test that waitFlag for startUser throws when called on API level before it was supported. */
    @Test
    public void testStartUser_wait_api27() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 27;
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "O\n";
                    }
                };
        try {
            mTestDevice.startUser(10, true);
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    /**
     * Test that remount works as expected on a device not supporting dm verity
     *
     * @throws Exception
     */
    @Test
    public void testRemount_verityUnsupported() throws Exception {
        injectSystemProperty("partition.system.verified", "");
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        mTestDevice.remountSystemWritable();
    }

    /**
     * Test that remount vendor works as expected on a device not supporting dm verity
     *
     * @throws Exception
     */
    @Test
    public void testRemountVendor_verityUnsupported() throws Exception {
        injectSystemProperty("partition.vendor.verified", "");
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        mTestDevice.remountVendorWritable();
    }

    /**
     * Test that remount works as expected on a device supporting dm verity v1
     *
     * @throws Exception
     */
    @Test
    public void testRemount_veritySupportedV1() throws Exception {
        injectSystemProperty("partition.system.verified", "1");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        mTestDevice.remountSystemWritable();
        verifyRebootExpectations();
    }
    /**
     * Test that remount vendor works as expected on a device supporting dm verity v1
     *
     * @throws Exception
     */
    @Test
    public void testRemountVendor_veritySupportedV1() throws Exception {
        injectSystemProperty("partition.vendor.verified", "1");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        mTestDevice.remountVendorWritable();
        verifyRebootExpectations();
    }

    /**
     * Test that remount works as expected on a device supporting dm verity v2
     *
     * @throws Exception
     */
    @Test
    public void testRemount_veritySupportedV2() throws Exception {
        injectSystemProperty("partition.system.verified", "2");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        mTestDevice.remountSystemWritable();
        verifyRebootExpectations();
    }

    /**
     * Test that remount vendor works as expected on a device supporting dm verity v2
     *
     * @throws Exception
     */
    @Test
    public void testRemountVendor_veritySupportedV2() throws Exception {
        injectSystemProperty("partition.vendor.verified", "2");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        mTestDevice.remountVendorWritable();
        verifyRebootExpectations();
    }

    /**
     * Test that remount works as expected on a device supporting dm verity but with unknown version
     *
     * @throws Exception
     */
    @Test
    public void testRemount_veritySupportedNonNumerical() throws Exception {
        injectSystemProperty("partition.system.verified", "foo");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        mTestDevice.remountSystemWritable();
        verifyRebootExpectations();
    }

    /**
     * Test that remount vendor works as expected on a device supporting dm verity but with unknown
     * version
     *
     * @throws Exception
     */
    @Test
    public void testRemountVendor_veritySupportedNonNumerical() throws Exception {
        injectSystemProperty("partition.vendor.verified", "foo");
        setExecuteAdbCommandExpectations(
                new CommandResult(CommandStatus.SUCCESS), "disable-verity");
        setRebootExpectations();
        setExecuteAdbCommandExpectations(new CommandResult(CommandStatus.SUCCESS), "remount");
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        mTestDevice.remountVendorWritable();
        verifyRebootExpectations();
    }

    /**
     * Test that {@link TestDevice#getBuildSigningKeys()} works for the typical "test-keys" case
     *
     * @throws Exception
     */
    @Test
    public void testGetBuildSigningKeys_test_keys() throws Exception {
        injectSystemProperty(DeviceProperties.BUILD_TAGS, "test-keys");

        assertEquals("test-keys", mTestDevice.getBuildSigningKeys());
    }

    /**
     * Test that {@link TestDevice#getBuildSigningKeys()} works for the case where build tags is a
     * comma separated list
     *
     * @throws Exception
     */
    @Test
    public void testGetBuildSigningKeys_test_keys_commas() throws Exception {
        injectSystemProperty(DeviceProperties.BUILD_TAGS, "test-keys,foo,bar,yadda");

        assertEquals("test-keys", mTestDevice.getBuildSigningKeys());
    }

    /**
     * Test that {@link TestDevice#getBuildSigningKeys()} returns null for non-matching case
     *
     * @throws Exception
     */
    @Test
    public void testGetBuildSigningKeys_not_matched() throws Exception {
        injectSystemProperty(DeviceProperties.BUILD_TAGS, "huh,foo,bar,yadda");

        assertNull(mTestDevice.getBuildSigningKeys());
    }

    /**
     * Test that {@link TestDevice#getCurrentUser()} returns the current user id.
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentUser() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "3\n";
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_GET_CURRENT_USER;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }
                };
        int res = mTestDevice.getCurrentUser();
        assertEquals(3, res);
    }

    /**
     * Test that {@link TestDevice#getCurrentUser()} returns INVALID_USER_ID when output is not
     * expected.
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentUser_invalid() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("not found.");
                        return res;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_GET_CURRENT_USER;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }
                };
        int res = mTestDevice.getCurrentUser();
        assertEquals(NativeDevice.INVALID_USER_ID, res);
    }

    /**
     * Test that {@link TestDevice#getCurrentUser()} returns null when api level is too low
     *
     * @throws Exception
     */
    @Test
    public void testGetCurrentUser_lowApi() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 15;
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("REL");
                        return res;
                    }
                };
        try {
            mTestDevice.getCurrentUser();
        } catch (IllegalArgumentException e) {
            // expected
            return;
        }
        fail("getCurrentUser should have thrown an exception.");
    }

    /** Unit test for {@link TestDevice#getUserFlags(int)}. */
    @Test
    public void testGetUserFlag() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\n\tUserInfo{0:Owner:13} running";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        int flags = mTestDevice.getUserFlags(0);
        // Expected 19 because using radix 16 (so 13 becomes (1 * 16^1 + 3 * 16^0) = 19)
        assertEquals(19, flags);
    }

    /**
     * Unit test for {@link TestDevice#getUserFlags(int)} when command return empty list of users.
     */
    @Test
    public void testGetUserFlag_emptyReturn() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        int flags = mTestDevice.getUserFlags(2);
        assertEquals(NativeDevice.INVALID_USER_ID, flags);
    }

    /**
     * Unit test for {@link TestDevice#getUserFlags(int)} when there is multiple users in the list.
     */
    @Test
    public void testGetUserFlag_multiUser() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\n\tUserInfo{0:Owner:13}\n\tUserInfo{WRONG:Owner:14}\n\t"
                                + "UserInfo{}\n\tUserInfo{3:Owner:15} Running";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        int flags = mTestDevice.getUserFlags(3);
        assertEquals(21, flags);
    }

    /** Unit test for {@link TestDevice#isUserSecondary(int)} */
    @Test
    public void testIsUserSecondary() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return String.format(
                                "Users:\n\tUserInfo{0:Owner:0}\n\t"
                                        + "UserInfo{10:Primary:%x} Running\n\t"
                                        + "UserInfo{11:Guest:%x}\n\t"
                                        + "UserInfo{12:Secondary:0}\n\t"
                                        + "UserInfo{13:Managed:%x}\n\t"
                                        + "UserInfo{100:Restricted:%x}\n\t",
                                UserInfo.FLAG_PRIMARY,
                                UserInfo.FLAG_GUEST,
                                UserInfo.FLAG_MANAGED_PROFILE,
                                UserInfo.FLAG_RESTRICTED);
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        assertEquals(false, mTestDevice.isUserSecondary(0));
        assertEquals(false, mTestDevice.isUserSecondary(-1));
        assertEquals(false, mTestDevice.isUserSecondary(10));
        assertEquals(false, mTestDevice.isUserSecondary(11));
        assertEquals(true, mTestDevice.isUserSecondary(12));
        assertEquals(false, mTestDevice.isUserSecondary(13));
        assertEquals(false, mTestDevice.isUserSecondary(100));
    }

    /** Unit test for {@link TestDevice#getUserSerialNumber(int)} */
    @Test
    public void testGetUserSerialNumber() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\nUserInfo{0:Owner:13} serialNo=666";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        int serial = mTestDevice.getUserSerialNumber(0);
        assertEquals(666, serial);
    }

    /**
     * Unit test for {@link TestDevice#getUserSerialNumber(int)} when the dumpsys return some bad
     * data.
     */
    @Test
    public void testGetUserSerialNumber_badData() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\nUserInfo{0:Owner:13} serialNo=WRONG";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        int serial = mTestDevice.getUserSerialNumber(0);
        assertEquals(NativeDevice.INVALID_USER_ID, serial);
    }

    /**
     * Unit test for {@link TestDevice#getUserSerialNumber(int)} when the dumpsys return an empty
     * serial
     */
    @Test
    public void testGetUserSerialNumber_emptySerial() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\nUserInfo{0:Owner:13} serialNo=";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        int serial = mTestDevice.getUserSerialNumber(0);
        assertEquals(NativeDevice.INVALID_USER_ID, serial);
    }

    /**
     * Unit test for {@link TestDevice#getUserSerialNumber(int)} when there is multiple users in the
     * dumpsys
     */
    @Test
    public void testGetUserSerialNumber_multiUsers() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\n"
                                + "UserInfo{0:Owner:13} serialNo=1\n"
                                + "UserInfo{1:Owner:13} serialNo=2\n"
                                + "UserInfo{2:Owner:13} serialNo=3";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        int serial = mTestDevice.getUserSerialNumber(2);
        assertEquals(3, serial);
    }

    /**
     * Unit test for {@link TestDevice#switchUser(int)} when user requested is already is current
     * user.
     */
    @Test
    public void testSwitchUser_alreadySameUser() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 0;
                    }

                    @Override
                    public void prePostBootSetup() {
                        // skip for this test
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_GET_CURRENT_USER;
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }
                };
        assertTrue(mTestDevice.switchUser(0));
    }

    /** Unit test for {@link TestDevice#switchUser(int)} when user switch instantly. */
    @Test
    public void testSwitchUser() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    int ret = 0;

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return ret;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        ret = 10;
                        return "";
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public void prePostBootSetup() {
                        // skip for this test
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_GET_CURRENT_USER;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }
                };
        assertTrue(mTestDevice.switchUser(10));
    }

    /** Unit test for {@link TestDevice#switchUser(int)} when user switch with a short delay. */
    @Test
    public void testSwitchUser_delay() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    int ret = 0;

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return ret;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        if (!started) {
                            started = true;
                            test.setDaemon(true);
                            test.setName(getClass().getCanonicalName() + "#testSwitchUser_delay");
                            test.start();
                        }
                        return "";
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_GET_CURRENT_USER;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }

                    @Override
                    public void prePostBootSetup() {
                        // skip for this test
                    }

                    @Override
                    protected long getCheckNewUserSleep() {
                        return 100;
                    }

                    boolean started = false;
                    Thread test =
                            new Thread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            RunUtil.getDefault().sleep(100);
                                            ret = 10;
                                        }
                                    });
                };
        assertTrue(mTestDevice.switchUser(10));
    }

    /** Unit test for {@link TestDevice#switchUser(int)} when user never change. */
    @Test
    public void testSwitchUser_noChange() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    int ret = 0;

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return ret;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        ret = 0;
                        return "";
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_GET_CURRENT_USER;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }

                    @Override
                    protected long getCheckNewUserSleep() {
                        return 50;
                    }

                    @Override
                    public void prePostBootSetup() {
                        // skip for this test
                    }
                };
        assertFalse(mTestDevice.switchUser(10, 100));
    }

    /** Unit test for {@link TestDevice#switchUser(int)} for post API 30. */
    @Test
    public void testSwitchUser_api30() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    int ret = 0;

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return ret;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        RunUtil.getDefault().sleep(100);
                        ret = 10;
                        return "";
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 30;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "R\n";
                    }
                };
        assertTrue(mTestDevice.switchUser(10));
    }

    /** Unit test for {@link TestDevice#switchUser(int)} when user switch with a short delay. */
    @Test
    public void testSwitchUser_delay_api30() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    int ret = 0;

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return ret;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        RunUtil.getDefault().sleep(100);
                        if (!started) {
                            started = true;
                            test.setDaemon(true);
                            test.setName(getClass().getCanonicalName() + "#testSwitchUser_delay");
                            test.start();
                        }
                        return "";
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("codename");
                        return res;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 30;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "R\n";
                    }

                    @Override
                    protected long getCheckNewUserSleep() {
                        return 100;
                    }

                    boolean started = false;
                    Thread test =
                            new Thread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            RunUtil.getDefault().sleep(100);
                                            ret = 10;
                                        }
                                    });
                };
        assertTrue(mTestDevice.switchUser(10));
    }

    /** Unit test for {@link TestDevice#switchUser(int)} when user switch with a short delay. */
    @Test
    public void testSwitchUser_error_api30() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    int ret = 0;

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return ret;
                    }

                    @Override
                    public CommandResult executeShellV2Command(String command)
                            throws DeviceNotAvailableException {
                        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
                        res.setStdout("Error:");
                        return res;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 30;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "R\n";
                    }

                    @Override
                    protected long getCheckNewUserSleep() {
                        return 100;
                    }

                    @Override
                    public String getSerialNumber() {
                        return "serial";
                    }
                };
        assertFalse(mTestDevice.switchUser(10, /* timeout= */ 300));
    }

    /** Unit test for {@link TestDevice#stopUser(int)}, cannot stop current user. */
    @Test
    public void testStopUser_notCurrent() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Can't stop current user";
                    }

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 0;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_STOP_USER;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }
                };
        assertFalse(mTestDevice.stopUser(0));
    }

    /** Unit test for {@link TestDevice#stopUser(int)}, cannot stop system */
    @Test
    public void testStopUser_notSystem() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Error: Can't stop system user 0";
                    }

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 10;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_STOP_USER;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }
                };
        assertFalse(mTestDevice.stopUser(0));
    }

    /**
     * Unit test for {@link TestDevice#stopUser(int, boolean, boolean)}, for a success stop in API
     * 22.
     */
    @Test
    public void testStopUser_success_api22() throws Exception {
        verifyStopUserSuccess(22, false, false, "am stop-user 10");
    }

    /**
     * Unit test for {@link TestDevice#stopUser(int, boolean, boolean)}, for a success stop in API
     * 23.
     */
    @Test
    public void testStopUser_success_api23() throws Exception {
        verifyStopUserSuccess(23, true, false, "am stop-user -w 10");
    }

    /**
     * Unit test for {@link TestDevice#stopUser(int, boolean, boolean)}, for a success stop in API
     * 24.
     */
    @Test
    public void testStopUser_success_api24() throws Exception {
        verifyStopUserSuccess(24, true, true, "am stop-user -w -f 10");
    }

    private void verifyStopUserSuccess(
            int apiLevel, boolean wait, boolean force, String expectedCommand) throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        if (command.contains("am")) {
                            assertEquals(expectedCommand, command);
                        } else if (command.contains("pm")) {
                            assertEquals("pm list users", command);
                        } else {
                            fail("Unexpected command");
                        }
                        return "Users:\n\tUserInfo{0:Test:13}";
                    }

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 0;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return apiLevel;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }
                };
        assertTrue(mTestDevice.stopUser(10, wait, force));
    }

    @Test
    public void testStopUser_wait_api22() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        try {
            mTestDevice.stopUser(10, true, false);
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testStopUser_force_api22() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        try {
            mTestDevice.stopUser(10, false, true);
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    /** Unit test for {@link TestDevice#stopUser(int)}, for a failed stop */
    @Test
    public void testStopUser_failed() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        if (command.contains("am")) {
                            assertEquals("am stop-user 0", command);
                        } else if (command.contains("pm")) {
                            assertEquals("pm list users", command);
                        } else {
                            fail("Unexpected command");
                        }
                        return "Users:\n\tUserInfo{0:Test:13} running";
                    }

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 10;
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_STOP_USER;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "N\n";
                    }
                };
        assertFalse(mTestDevice.stopUser(0));
    }

    /** Unit test for {@link TestDevice#isUserRunning(int)}. */
    @Test
    public void testIsUserIdRunning_true() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\n\tUserInfo{0:Test:13} running";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return MIN_API_LEVEL_GET_CURRENT_USER;
                    }
                };
        assertTrue(mTestDevice.isUserRunning(0));
    }

    /** Unit test for {@link TestDevice#isUserRunning(int)}. */
    @Test
    public void testIsUserIdRunning_false() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\n\tUserInfo{0:Test:13} running\n\tUserInfo{10:New user:10}";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        assertFalse(mTestDevice.isUserRunning(10));
    }

    /** Unit test for {@link TestDevice#isUserRunning(int)}. */
    @Test
    public void testIsUserIdRunning_badFormat() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Users:\n\tUserInfo{WRONG:Test:13} running";
                    }

                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        assertFalse(mTestDevice.isUserRunning(0));
    }

    /** Unit test for {@link TestDevice#hasFeature(String)} on success. */
    @Test
    public void testHasFeature_true() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "feature:com.google.android.feature.EXCHANGE_6_2\n"
                                + "feature:com.google.android.feature.GOOGLE_BUILD\n"
                                + "feature:com.google.android.feature.GOOGLE_EXPERIENCE";
                    }
                };
        assertTrue(mTestDevice.hasFeature("feature:com.google.android.feature.EXCHANGE_6_2"));
    }

    @Test
    public void testHasFeature_flexible() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "feature:com.google.android.feature.EXCHANGE_6_2\n"
                                + "feature:com.google.android.feature.GOOGLE_BUILD\n"
                                + "feature:com.google.android.feature.GOOGLE_EXPERIENCE";
                    }
                };
        assertTrue(mTestDevice.hasFeature("com.google.android.feature.EXCHANGE_6_2"));
    }

    /** Unit test for {@link TestDevice#hasFeature(String)} on failure. */
    @Test
    public void testHasFeature_fail() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "feature:com.google.android.feature.EXCHANGE_6_2\n"
                                + "feature:com.google.android.feature.GOOGLE_BUILD\n"
                                + "feature:com.google.android.feature.GOOGLE_EXPERIENCE";
                    }
                };
        assertFalse(mTestDevice.hasFeature("feature:test"));
    }

    /** Unit test for {@link TestDevice#hasFeature(String)} on partly matching case. */
    @Test
    public void testHasFeature_partly_matching() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "feature:com.google.android.feature.EXCHANGE_6_2\n"
                                + "feature:com.google.android.feature.GOOGLE_BUILD\n"
                                + "feature:com.google.android.feature.GOOGLE_EXPERIENCE";
                    }
                };
        assertFalse(mTestDevice.hasFeature("feature:com.google.android.feature"));
        assertTrue(mTestDevice.hasFeature("feature:com.google.android.feature.EXCHANGE_6_2"));
    }

    /** Unit test for {@link TestDevice#hasFeature(String)} on versioned feature. */
    @Test
    public void testHasFeature_versioned() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String getSerialNumber() {
                        return "serial";
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "feature:com.google.android.feature.EXCHANGE_6_2\n"
                                + "feature:com.google.android.feature.GOOGLE_BUILD_VERSIONED=2\n"
                                + "feature:org.com.google.android.feature.GOOGLE_BUILD_ORG=1\n"
                                + "feature:com.google.android.feature.GOOGLE_BUILD_EXT";
                    }
                };
        assertFalse(mTestDevice.hasFeature("feature:com.google.android.feature"));
        assertFalse(mTestDevice.hasFeature("com.google.android.feature.GOOGLE_BUILD_ORG"));
        assertFalse(mTestDevice.hasFeature("com.google.android.feature.GOOGLE_BUILD"));
        assertTrue(
                mTestDevice.hasFeature(
                        "feature:com.google.android.feature.GOOGLE_BUILD_VERSIONED"));
    }

    /** Unit test for {@link TestDevice#getSetting(int, String, String)}. */
    @Test
    public void testGetSetting() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "78";
                    }
                };
        assertEquals("78", mTestDevice.getSetting(0, "system", "screen_brightness"));
    }

    /** Unit test for {@link TestDevice#getSetting(String, String)}. */
    @Test
    public void testGetSetting_SystemUser() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "78";
                    }
                };
        assertEquals("78", mTestDevice.getSetting("system", "screen_brightness"));
    }

    /** Unit test for {@link TestDevice#getSetting(int, String, String)}. */
    @Test
    public void testGetSetting_nulloutput() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "null";
                    }
                };
        assertNull(mTestDevice.getSetting(0, "system", "screen_brightness"));
    }

    /**
     * Unit test for {@link TestDevice#getSetting(int, String, String)} with a namespace that is not
     * in {global, system, secure}.
     */
    @Test
    public void testGetSetting_unexpectedNamespace() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        assertNull(mTestDevice.getSetting(0, "TEST", "screen_brightness"));
    }

    /** Unit test for {@link TestDevice#getAllSettings(String)}. */
    @Test
    public void testGetAllSettings() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "mobile_data=1\nmulti_sim_data_call=-1\n";
                    }
                };
        Map<String, String> map = mTestDevice.getAllSettings("system");
        assertEquals("1", map.get("mobile_data"));
        assertEquals("-1", map.get("multi_sim_data_call"));
    }

    /** Unit test for {@link TestDevice#getAllSettings(String)} with emtpy setting value */
    @Test
    public void testGetAllSettings_EmptyValue() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Phenotype_flags=\nmulti_sim_data_call=-1\n";
                    }
                };
        Map<String, String> map = mTestDevice.getAllSettings("system");
        assertEquals("", map.get("Phenotype_flags"));
        assertEquals("-1", map.get("multi_sim_data_call"));
    }

    /**
     * Unit test for {@link TestDevice#getAllSettings(String)} with a namespace that is not in
     * {global, system, secure}.
     */
    @Test
    public void testGetAllSettings_unexpectedNamespace() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        assertNull(mTestDevice.getAllSettings("TEST"));
    }

    /**
     * Unit test for {@link TestDevice#setSetting(int, String, String, String)} with a namespace
     * that is not in {global, system, secure}.
     */
    @Test
    public void testSetSetting_unexpectedNamespace() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        try {
            mTestDevice.setSetting(0, "TEST", "screen_brightness", "75");
        } catch (IllegalArgumentException e) {
            // expected
            return;
        }
        fail("putSettings should have thrown an exception.");
    }

    /**
     * Unit test for {@link TestDevice#setSetting(int, String, String, String)} with a normal case.
     */
    @Test
    public void testSetSettings() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        // Make sure it doesn't throw
        mTestDevice.setSetting(0, "system", "screen_brightness", "75");
    }

    /** Unit test for {@link TestDevice#setSetting(String, String, String)} with a normal case. */
    @Test
    public void testSetSettings_SystemUser() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }
                };
        // Make sure it doesn't throw
        mTestDevice.setSetting("system", "screen_brightness", "75");
    }

    /**
     * Unit test for {@link TestDevice#setSetting(int, String, String, String)} when API level is
     * too low
     */
    @Test
    public void testSetSettings_lowApi() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 21;
                    }
                };
        try {
            mTestDevice.setSetting(0, "system", "screen_brightness", "75");
        } catch (HarnessRuntimeException e) {
            assertTrue(e.getMessage().contains("Changing settings not supported"));
            // expected
            return;
        }
        fail("putSettings should have thrown an exception.");
    }

    /** Unit test for {@link TestDevice#getAndroidId(int)}. */
    @Test
    public void testGetAndroidId() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "4433829313704884235";
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        assertEquals("4433829313704884235", mTestDevice.getAndroidId(0));
    }

    /** Unit test for {@link TestDevice#getAndroidId(int)} when db containing the id is not found */
    @Test
    public void testGetAndroidId_notFound() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Error: unable to open database"
                                + "\"/data/0/com.google.android.gsf/databases/gservices.db\": "
                                + "unable to open database file";
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        assertNull(mTestDevice.getAndroidId(0));
    }

    /** Unit test for {@link TestDevice#getAndroidId(int)} when adb root not enabled. */
    @Test
    public void testGetAndroidId_notRoot() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return false;
                    }
                };
        assertNull(mTestDevice.getAndroidId(0));
    }

    /** Unit test for {@link TestDevice#getAndroidIds()} */
    @Test
    public void testGetAndroidIds() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
                        ArrayList<Integer> test = new ArrayList<Integer>();
                        test.add(0);
                        test.add(1);
                        return test;
                    }

                    @Override
                    public String getAndroidId(int userId) throws DeviceNotAvailableException {
                        return "44444";
                    }
                };
        Map<Integer, String> expected = new HashMap<Integer, String>();
        expected.put(0, "44444");
        expected.put(1, "44444");
        assertEquals(expected, mTestDevice.getAndroidIds());
    }

    /** Unit test for {@link TestDevice#getAndroidIds()} when no user are found. */
    @Test
    public void testGetAndroidIds_noUser() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
                        return null;
                    }
                };
        assertNull(mTestDevice.getAndroidIds());
    }

    /** Unit test for {@link TestDevice#getAndroidIds()} when no match is found for user ids. */
    @Test
    public void testGetAndroidIds_noMatch() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
                        ArrayList<Integer> test = new ArrayList<Integer>();
                        test.add(0);
                        test.add(1);
                        return test;
                    }

                    @Override
                    public String getAndroidId(int userId) throws DeviceNotAvailableException {
                        return null;
                    }
                };
        Map<Integer, String> expected = new HashMap<Integer, String>();
        expected.put(0, null);
        expected.put(1, null);
        assertEquals(expected, mTestDevice.getAndroidIds());
    }

    /** Test for {@link TestDevice#getScreenshot()} when action failed. */
    @Test
    public void testGetScreenshot_failure() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    protected boolean performDeviceAction(
                            String actionDescription, DeviceAction action, int retryAttempts)
                            throws DeviceNotAvailableException {
                        return false;
                    }
                };
        InputStreamSource res = mTestDevice.getScreenshot();
        assertNotNull(res);
        assertEquals(
                "Error: device reported null for screenshot.",
                StreamUtil.getStringFromStream(res.createInputStream()));
    }

    /** Test for {@link TestDevice#getScreenshot()} when action succeed. */
    @Test
    public void testGetScreenshot() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    protected boolean performDeviceAction(
                            String actionDescription, DeviceAction action, int retryAttempts)
                            throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    byte[] compressRawImage(RawImage rawImage, String format, boolean rescale) {
                        return "image".getBytes();
                    }
                };
        InputStreamSource data = mTestDevice.getScreenshot();
        assertNotNull(data);
        assertTrue(data instanceof ByteArrayInputStreamSource);
    }

    /** Helper to retrieve the test file */
    private File getTestImageResource() throws Exception {
        InputStream imageZip = getClass().getResourceAsStream(RAWIMAGE_RESOURCE);
        File imageZipFile = FileUtil.createTempFile("rawImage", ".zip");
        try {
            FileUtil.writeToFile(imageZip, imageZipFile);
            File dir = ZipUtil2.extractZipToTemp(imageZipFile, "test-raw-image");
            return new File(dir, "rawImageScreenshot.raw");
        } finally {
            FileUtil.deleteFile(imageZipFile);
        }
    }

    /** Helper to create the rawImage to test. */
    private RawImage prepareRawImage(File rawImageFile) throws Exception {
        String data = FileUtil.readStringFromFile(rawImageFile);
        RawImage sRawImage = new RawImage();
        sRawImage.alpha_length = 8;
        sRawImage.alpha_offset = 24;
        sRawImage.blue_length = 8;
        sRawImage.blue_offset = 16;
        sRawImage.bpp = 32;
        sRawImage.green_length = 8;
        sRawImage.green_offset = 8;
        sRawImage.height = 1920;
        sRawImage.red_length = 8;
        sRawImage.red_offset = 0;
        sRawImage.size = 8294400;
        sRawImage.version = 1;
        sRawImage.width = 1080;
        sRawImage.data = data.getBytes();
        return sRawImage;
    }

    /**
     * Test for {@link TestDevice#compressRawImage(RawImage, String, boolean)} properly reduce the
     * image size with different encoding.
     */
    @Test
    public void testCompressScreenshot() throws Exception {
        InputStream imageData = getClass().getResourceAsStream("/testdata/SmallRawImage.raw");
        File testImageFile = FileUtil.createTempFile("raw-to-buffered", ".raw");
        FileUtil.writeToFile(imageData, testImageFile);
        RawImage testImage = null;
        try {
            testImage = prepareRawImage(testImageFile);
            // We used the small image so we adapt the size.
            testImage.height = 25;
            testImage.size = 2000;
            testImage.width = 25;
            // Size of the raw test data
            Assert.assertEquals(3000, testImage.data.length);
            byte[] result = mTestDevice.compressRawImage(testImage, "PNG", true);
            // Size after compressing can vary a bit depending of the JDK
            if (result.length != 107 && result.length != 117 && result.length != 139) {
                fail(
                        String.format(
                                "Should have compress the length as expected, got %s, "
                                        + "expected 107, 117 or 139",
                                result.length));
            }

            // Do it again with JPEG encoding
            Assert.assertEquals(3000, testImage.data.length);
            result = mTestDevice.compressRawImage(testImage, "JPEG", true);
            // Size after compressing as JPEG
            if (result.length != 1041 && result.length != 851) {
                fail(
                        String.format(
                                "Should have compress the length as expected, got %s, "
                                        + "expected 851 or 1041",
                                result.length));
            }
        } finally {
            if (testImage != null) {
                testImage.data = null;
            }
            FileUtil.deleteFile(testImageFile);
        }
    }

    /**
     * Test for {@link TestDevice#rawImageToBufferedImage(RawImage, String)}.
     *
     * @throws Exception
     */
    @Test
    public void testRawImageToBufferedImage() throws Exception {
        InputStream imageData = getClass().getResourceAsStream("/testdata/SmallRawImage.raw");
        File testImageFile = FileUtil.createTempFile("raw-to-buffered", ".raw");
        FileUtil.writeToFile(imageData, testImageFile);
        RawImage testImage = null;
        try {
            testImage = prepareRawImage(testImageFile);
            // We used the small image so we adapt the size.
            testImage.height = 25;
            testImage.size = 2000;
            testImage.width = 25;

            // Test PNG format
            BufferedImage bufferedImage = mTestDevice.rawImageToBufferedImage(testImage, "PNG");
            assertEquals(testImage.width, bufferedImage.getWidth());
            assertEquals(testImage.height, bufferedImage.getHeight());
            assertEquals(BufferedImage.TYPE_INT_ARGB, bufferedImage.getType());

            // Test JPEG format
            bufferedImage = mTestDevice.rawImageToBufferedImage(testImage, "JPEG");
            assertEquals(testImage.width, bufferedImage.getWidth());
            assertEquals(testImage.height, bufferedImage.getHeight());
            assertEquals(BufferedImage.TYPE_3BYTE_BGR, bufferedImage.getType());
        } finally {
            if (testImage != null) {
                testImage.data = null;
            }
            FileUtil.deleteFile(testImageFile);
        }
    }

    /**
     * Test for {@link TestDevice#rescaleImage(BufferedImage)}.
     *
     * @throws Exception
     */
    @Test
    public void testRescaleImage() throws Exception {
        File testImageFile = getTestImageResource();
        RawImage testImage = null;
        try {
            testImage = prepareRawImage(testImageFile);
            BufferedImage bufferedImage = mTestDevice.rawImageToBufferedImage(testImage, "PNG");

            BufferedImage scaledImage = mTestDevice.rescaleImage(bufferedImage);
            assertEquals(bufferedImage.getWidth() / 2, scaledImage.getWidth());
            assertEquals(bufferedImage.getHeight() / 2, scaledImage.getHeight());
        } finally {
            if (testImage != null) {
                testImage.data = null;
            }
            FileUtil.recursiveDelete(testImageFile.getParentFile());
        }
    }

    /**
     * Test for {@link TestDevice#compressRawImage(RawImage, String, boolean)} does not rescale
     * image if specified.
     */
    @Test
    public void testCompressScreenshotNoRescale() throws Exception {
        File testImageFile = getTestImageResource();
        final RawImage[] testImage = new RawImage[1];
        testImage[0] = prepareRawImage(testImageFile);

        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    byte[] getImageData(BufferedImage image, String format) {
                        assertEquals(testImage[0].width, image.getWidth());
                        assertEquals(testImage[0].height, image.getHeight());
                        return super.getImageData(image, format);
                    }
                };
        try {
            byte[] result = mTestDevice.compressRawImage(testImage[0], "PNG", false);
            assertNotNull(result);
        } finally {
            FileUtil.recursiveDelete(testImageFile.getParentFile());
            testImage[0].data = null;
            testImage[0] = null;
        }
    }

    /** Test for {@link TestDevice#getKeyguardState()} produces the proper output. */
    @Test
    public void testGetKeyguardState() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "KeyguardController:\n"
                                + "  mKeyguardShowing=true\n"
                                + "  mKeyguardGoingAway=false\n"
                                + "  mOccluded=false\n";
                    }
                };
        KeyguardControllerState state = mTestDevice.getKeyguardState();
        Assert.assertTrue(state.isKeyguardShowing());
        Assert.assertFalse(state.isKeyguardOccluded());
    }

    /** New output of dumpsys is not as clean and has stuff in front. */
    @Test
    public void testGetKeyguardState_new() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "isHomeRecentsComponent=true  KeyguardController:\n"
                                + "  mKeyguardShowing=true\n"
                                + "  mKeyguardGoingAway=false\n"
                                + "  mOccluded=false\n";
                    }
                };
        KeyguardControllerState state = mTestDevice.getKeyguardState();
        Assert.assertTrue(state.isKeyguardShowing());
        Assert.assertFalse(state.isKeyguardOccluded());
    }

    /** Test for {@link TestDevice#getKeyguardState()} when the device does not support it. */
    @Test
    public void testGetKeyguardState_unsupported() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "\n";
                    }
                };
        assertNull(mTestDevice.getKeyguardState());
    }

    @Test
    public void testSetDeviceOwner_success() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Success: Device owner set to package ComponentInfo{xxx/yyy}";
                    }
                };
        assertTrue(mTestDevice.setDeviceOwner("xxx/yyy", 0));
    }

    @Test
    public void testSetDeviceOwner_fail() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "java.lang.IllegalStateException: Trying to set the device owner";
                    }
                };
        assertFalse(mTestDevice.setDeviceOwner("xxx/yyy", 0));
    }

    @Test
    public void testRemoveAdmin_success() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "Success: Admin removed";
                    }
                };
        assertTrue(mTestDevice.removeAdmin("xxx/yyy", 0));
    }

    @Test
    public void testRemoveAdmin_fail() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "java.lang.SecurityException: Attempt to remove non-test admin";
                    }
                };
        assertFalse(mTestDevice.removeAdmin("xxx/yyy", 0));
    }

    @Test
    public void testRemoveOwners() throws Exception {
        mTestDevice =
                Mockito.spy(
                        new TestableTestDevice() {
                            @Override
                            public String executeShellCommand(String command)
                                    throws DeviceNotAvailableException {
                                return "Current Device Policy Manager state:\n"
                                        + "  Device Owner: \n"
                                        + "    admin=ComponentInfo{aaa/aaa}\n"
                                        + "    name=\n"
                                        + "    package=aaa\n"
                                        + "    User ID: 0\n"
                                        + "\n"
                                        + "  Profile Owner (User 10): \n"
                                        + "    admin=ComponentInfo{bbb/bbb}\n"
                                        + "    name=bbb\n"
                                        + "    package=bbb\n";
                            }
                        });
        mTestDevice.removeOwners();

        // Verified removeAdmin is called to remove owners.
        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> intCaptor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(mTestDevice, Mockito.times(2))
                .removeAdmin(stringCaptor.capture(), intCaptor.capture());
        List<String> stringArgs = stringCaptor.getAllValues();
        List<Integer> intArgs = intCaptor.getAllValues();

        assertEquals("aaa/aaa", stringArgs.get(0));
        assertEquals(Integer.valueOf(0), intArgs.get(0));

        assertEquals("bbb/bbb", stringArgs.get(1));
        assertEquals(Integer.valueOf(10), intArgs.get(1));
    }

    @Test
    public void testRemoveOwnersWithAdditionalLines() throws Exception {
        mTestDevice =
                Mockito.spy(
                        new TestableTestDevice() {
                            @Override
                            public String executeShellCommand(String command)
                                    throws DeviceNotAvailableException {
                                return "Current Device Policy Manager state:\n"
                                        + "  Device Owner: \n"
                                        + "    admin=ComponentInfo{aaa/aaa}\n"
                                        + "    name=\n"
                                        + "    package=aaa\n"
                                        + "    moreLines=true\n"
                                        + "    User ID: 0\n"
                                        + "\n"
                                        + "  Profile Owner (User 10): \n"
                                        + "    admin=ComponentInfo{bbb/bbb}\n"
                                        + "    name=bbb\n"
                                        + "    package=bbb\n";
                            }
                        });
        mTestDevice.removeOwners();

        // Verified removeAdmin is called to remove owners.
        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> intCaptor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(mTestDevice, Mockito.times(2))
                .removeAdmin(stringCaptor.capture(), intCaptor.capture());
        List<String> stringArgs = stringCaptor.getAllValues();
        List<Integer> intArgs = intCaptor.getAllValues();

        assertEquals("aaa/aaa", stringArgs.get(0));
        assertEquals(Integer.valueOf(0), intArgs.get(0));

        assertEquals("bbb/bbb", stringArgs.get(1));
        assertEquals(Integer.valueOf(10), intArgs.get(1));
    }

    /** Test that the output of cryptfs allows for encryption for newest format. */
    @Test
    public void testIsEncryptionSupported_newformat() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        setGetPropertyExpectation("ro.crypto.state", "encrypted");

        assertTrue(mTestDevice.isEncryptionSupported());
    }

    /** Test that the output of cryptfs does not allow for encryption. */
    @Test
    public void testIsEncryptionSupported_failure() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        setGetPropertyExpectation("ro.crypto.state", "unsupported");

        assertFalse(mTestDevice.isEncryptionSupported());
    }

    /** Test when getting the heapdump is successful. */
    @Test
    public void testGetHeapDump() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public File pullFile(String remoteFilePath) throws DeviceNotAvailableException {
                        return new File("test");
                    }
                };
        injectShellResponse("pidof system_server", "929");
        injectShellResponse("am dumpheap 929 /data/dump.hprof", "");
        injectShellResponse("ls \"/data/dump.hprof\"", "/data/dump.hprof");
        injectShellResponse("rm -rf /data/dump.hprof", "");

        File res = mTestDevice.dumpHeap("system_server", "/data/dump.hprof");
        assertNotNull(res);
        verifyShellResponse("pidof system_server");
        verifyShellResponse("am dumpheap 929 /data/dump.hprof");
        verifyShellResponse("ls \"/data/dump.hprof\"");
        verifyShellResponse("rm -rf /data/dump.hprof");
    }

    /** Test when we fail to get the process pid. */
    @Test
    public void testGetHeapDump_nopid() throws Exception {
        injectShellResponse("pidof system_server", "\n");

        File res = mTestDevice.dumpHeap("system_server", "/data/dump.hprof");
        assertNull(res);
        verifyShellResponse("pidof system_server");
    }

    @Test
    public void testGetHeapDump_nullPath() throws DeviceNotAvailableException {
        try {
            mTestDevice.dumpHeap("system_server", null);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testGetHeapDump_emptyPath() throws DeviceNotAvailableException {
        try {
            mTestDevice.dumpHeap("system_server", "");
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testGetHeapDump_nullService() throws DeviceNotAvailableException {
        try {
            mTestDevice.dumpHeap(null, "/data/hprof");
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testGetHeapDump_emptyService() throws DeviceNotAvailableException {
        try {
            mTestDevice.dumpHeap("", "/data/hprof");
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    /**
     * Test that the wifi helper is uninstalled at postInvocationTearDown if it was installed
     * before.
     */
    @Test
    public void testPostInvocationWifiTearDown() throws Exception {
        // A TestDevice with a no-op recoverDevice() implementation
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public void recoverDevice() throws DeviceNotAvailableException {
                        // ignore
                    }

                    @Override
                    public String installPackage(
                            File packageFile, boolean reinstall, String... extraArgs)
                            throws DeviceNotAvailableException {
                        // Fake install is successfull
                        return null;
                    }

                    @Override
                    IWifiHelper createWifiHelper() throws DeviceNotAvailableException {
                        super.createWifiHelper(true);
                        return mMockWifi;
                    }

                    @Override
                    IWifiHelper createWifiHelper(boolean doSetup)
                            throws DeviceNotAvailableException {
                        return mMockWifi;
                    }

                    @Override
                    ContentProviderHandler getContentProvider() throws DeviceNotAvailableException {
                        return null;
                    }
                };
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        when(mMockWifi.getIpAddress()).thenReturn("ip");
        // Wifi is cleaned up by the post invocation tear down.

        mTestDevice.getIpAddress();
        mTestDevice.postInvocationTearDown(null);

        verify(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq("dumpsys package com.android.tradefed.utils.wifi"),
                        Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.any());
        verify(mMockWifi).cleanUp();
    }

    /** Test that displays can be collected. */
    @Test
    public void testListDisplayId() throws Exception {
        OutputStream stdout = null, stderr = null;
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        res.setStdout("Display 0 color modes:\nDisplay 5 color modes:\n");
        when(mMockRunUtil.runTimedCmd(
                        100L,
                        stdout,
                        stderr,
                        "adb",
                        "-s",
                        "serial",
                        "shell",
                        "dumpsys",
                        "SurfaceFlinger",
                        "|",
                        "grep",
                        "'color",
                        "modes:'"))
                .thenReturn(res);

        Set<Long> displays = mTestDevice.listDisplayIds();
        assertEquals(2, displays.size());
        assertTrue(displays.contains(0L));
        assertTrue(displays.contains(5L));
    }

    /** Test for {@link TestDevice#getScreenshot(long)}. */
    @Test
    public void testScreenshotByDisplay() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public File pullFile(String remoteFilePath) throws DeviceNotAvailableException {
                        assertEquals("/data/local/tmp/display_0.png", remoteFilePath);
                        return new File("fakewhatever");
                    }
                };
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        OutputStream outStream = null;
        when(mMockRunUtil.runTimedCmd(
                        120000L,
                        outStream,
                        outStream,
                        "adb",
                        "-s",
                        "serial",
                        "shell",
                        "screencap",
                        "-p",
                        "-d",
                        "0",
                        "/data/local/tmp/display_0.png"))
                .thenReturn(res);

        InputStreamSource source = mTestDevice.getScreenshot(0);
        assertNotNull(source);
        StreamUtil.close(source);

        verify(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq("rm -rf /data/local/tmp/display_0.png"),
                        Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.any());
    }

    /** Test {@link TestDevice#doesFileExist(String)}. */
    @Test
    public void testDoesFileExists() throws Exception {
        injectShellResponse("ls \"/data/local/tmp/file\"", "file");

        assertTrue(mTestDevice.doesFileExist("/data/local/tmp/file"));
        verifyShellResponse("ls \"/data/local/tmp/file\"");
    }

    /** Test {@link TestDevice#doesFileExist(String)} when the file does not exists. */
    @Test
    public void testDoesFileExists_notExists() throws Exception {
        injectShellResponse(
                "ls \"/data/local/tmp/file\"",
                "ls: cannot access 'file': No such file or directory\n");

        assertFalse(mTestDevice.doesFileExist("/data/local/tmp/file"));
        verifyShellResponse("ls \"/data/local/tmp/file\"");
    }

    /**
     * Test {@link TestDevice#doesFileExist(String)} using content provider when the file is in
     * external storage path.
     */
    @Test
    public void testDoesFileExists_sdcard() throws Exception {
        mTestDevice = createTestDevice();

        TestableTestDevice spy = (TestableTestDevice) Mockito.spy(mTestDevice);
        ContentProviderHandler cp = Mockito.mock(ContentProviderHandler.class);
        doReturn(cp).when(spy).getContentProvider();

        final String fakeFile = "/sdcard/file";
        final String targetFilePath = "/storage/emulated/10/file";

        doReturn("").when(spy).executeShellCommand(Mockito.contains("content query --user 10"));

        spy.doesFileExist(fakeFile);

        verify(spy, times(1)).getContentProvider();
        verify(cp, times(1)).doesFileExist(targetFilePath);
    }

    /** Push a file using the content provider. */
    @Test
    public void testPushFile_contentProvider() throws Exception {
        mTestDevice = createTestDevice();
        TestableTestDevice spy = (TestableTestDevice) Mockito.spy(mTestDevice);
        setupContentProvider(spy);

        final String fakeRemotePath = "/sdcard/";
        File tmpFile = FileUtil.createTempFile("push", ".test");

        CommandResult writeContent = new CommandResult(CommandStatus.SUCCESS);
        writeContent.setStdout("");
        doReturn(writeContent)
                .when(spy)
                .executeShellV2Command(Mockito.contains("content write"), (File) Mockito.any());

        try {
            boolean res = spy.pushFile(tmpFile, fakeRemotePath);

            assertTrue(res);
            verify(spy, times(1))
                    .installPackage(Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            ContentProviderHandler cp = spy.getContentProvider();
            assertFalse(cp.contentProviderNotFound());
            // Since it didn't fail, we did not re-install the content provider
            verify(spy, times(1))
                    .installPackage(Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            cp.tearDown();
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /** Push a file using the content provider. */
    @Test
    public void testPushFile_contentProvider_notFound() throws Exception {
        mTestDevice = createTestDevice();
        TestableTestDevice spy = (TestableTestDevice) Mockito.spy(mTestDevice);
        setupContentProvider(spy);

        final String fakeRemotePath = "/sdcard/";
        File tmpFile = FileUtil.createTempFile("push", ".test");

        CommandResult writeContent = new CommandResult(CommandStatus.SUCCESS);
        writeContent.setStdout("");
        writeContent.setStderr(
                "java.lang.IllegalStateException: Could not find provider: "
                        + "android.tradefed.contentprovider");
        doReturn(writeContent)
                .when(spy)
                .executeShellV2Command(Mockito.contains("content write"), (File) Mockito.any());
        doReturn(null).when(spy).uninstallPackage(Mockito.eq("android.tradefed.contentprovider"));

        try {
            boolean res = spy.pushFile(tmpFile, fakeRemotePath);

            assertFalse(res);
            // Tried twice due to retry
            verify(spy, times(2))
                    .installPackage(Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            // Since it fails, requesting the content provider again will re-do setup.
            ContentProviderHandler cp = spy.getContentProvider();
            assertFalse(cp.contentProviderNotFound());
            verify(spy, times(3))
                    .installPackage(Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
            cp.tearDown();
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    @Test
    public void testGetFoldableStates() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public CommandResult executeShellV2Command(String cmd)
                            throws DeviceNotAvailableException {
                        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
                        result.setStdout(
                                "Supported states: [\n"
                                        + " DeviceState{identifier=0, name='CLOSED'},\n"
                                        + " DeviceState{identifier=1, name='HALF_OPENED'},\n"
                                        + " DeviceState{identifier=2, name='OPENED'},\n"
                                        + "]\n");
                        return result;
                    }
                };

        Set<DeviceFoldableState> states = mTestDevice.getFoldableStates();
        assertEquals(3, states.size());
    }

    @Test
    public void testGetCurrentFoldableState() throws Exception {
        mTestDevice =
                new TestableTestDevice() {
                    @Override
                    public CommandResult executeShellV2Command(String cmd)
                            throws DeviceNotAvailableException {
                        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
                        result.setStdout(
                                "Committed state: DeviceState{identifier=2, name='DEFAULT'}\n");
                        return result;
                    }
                };

        DeviceFoldableState state = mTestDevice.getCurrentFoldableState();
        assertEquals(2, state.getIdentifier());
    }

    private void setGetPropertyExpectation(String property, String value) {
        CommandResult stubResult = new CommandResult(CommandStatus.SUCCESS);
        stubResult.setStdout(value);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (OutputStream) Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.eq("adb"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("shell"),
                        Mockito.eq("getprop"),
                        Mockito.eq(property)))
                .thenReturn(stubResult);
    }

    private void verifyGetPropertyExpectation(String property, String value, int times) {
        verify(mMockRunUtil, times(times))
                .runTimedCmd(
                        Mockito.anyLong(),
                        (OutputStream) Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.eq("adb"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("shell"),
                        Mockito.eq("getprop"),
                        Mockito.eq(property));
    }

    private void setupContentProvider(TestableTestDevice spy) throws Exception {
        doReturn(null)
                .when(spy)
                .installPackage(Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        CommandResult setLegacy = new CommandResult(CommandStatus.SUCCESS);
        doReturn(setLegacy).when(spy).executeShellV2Command(Mockito.contains("cmd appops set"));

        CommandResult getLegacy = new CommandResult(CommandStatus.SUCCESS);
        getLegacy.setStdout("LEGACY_STORAGE: allow");
        doReturn(getLegacy).when(spy).executeShellV2Command(Mockito.contains("cmd appops get"));

        doReturn(null).when(spy).uninstallPackage(Mockito.eq("android.tradefed.contentprovider"));
    }

    private TestableTestDevice createTestDevice() {
        return new TestableTestDevice() {
            @Override
            public int getApiLevel() throws DeviceNotAvailableException {
                return 29;
            }

            @Override
            public int getCurrentUser() throws DeviceNotAvailableException, DeviceRuntimeException {
                return 10;
            }

            @Override
            public boolean isPackageInstalled(String packageName, String userId)
                    throws DeviceNotAvailableException {
                return false;
            }
        };
    }
}
