/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ota.tests;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.TimeoutException;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.LogReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * A test that will perform repeated flash + install OTA actions on a device.
 * <p/>
 * adb must have root.
 * <p/>
 * Expects a {@link OtaDeviceBuildInfo}.
 * <p/>
 * Note: this test assumes that the {@link ITargetPreparer}s included in this test's
 * {@link IConfiguration} will flash the device back to a baseline build, and prepare the device to
 * receive the OTA to a new build.
 */
@OptionClass(alias = "ota-stability")
public class SideloadOtaStabilityTest implements IDeviceTest, IBuildReceiver,
        IConfigurationReceiver, IResumableTest {

    private static final String UNCRYPT_FILE_PATH = "/cache/recovery/uncrypt_file";
    private static final String BLOCK_MAP_PATH = "@/cache/recovery/block.map";
    private static final String RECOVERY_COMMAND_PATH = "/cache/recovery/command";
    private static final String LOG_RECOV = "/cache/recovery/last_log";
    private static final String LOG_KMSG = "/cache/recovery/last_kmsg";

    private static final String KMSG_CMD = "cat /proc/kmsg";

    private OtaDeviceBuildInfo mOtaDeviceBuild;
    private IConfiguration mConfiguration;
    private ITestDevice mDevice;

    @Option(name = "run-name", description =
            "The name of the ota stability test run. Used to report metrics.")
    private String mRunName = "ota-stability";

    @Option(name = "iterations", description =
            "Number of ota stability 'flash + wait for ota' iterations to run.")
    private int mIterations = 20;

    @Option(name = "resume", description = "Resume the ota test run if an device setup error "
            + "stopped the previous test run.")
    private boolean mResumeMode = false;

    @Option(name = "max-install-time", description =
            "The maximum time to wait for an ota to install in seconds.")
    private int mMaxInstallOnlineTimeSec = 5 * 60;

    @Option(name = "package-data-path", description =
            "path on /data for the package to be saved to")
    /* This is currently the only path readable by uncrypt on the userdata partition */
    private String mPackageDataPath = "/data/data/com.google.android.gsf/app_download/update.zip";

    @Option(name = "max-reboot-time", description =
            "The maximum time to wait for a device to reboot out of recovery if it fails")
    private long mMaxRebootTimeSec = 5 * 60;

    /** controls if this test should be resumed. Only used if mResumeMode is enabled */
    private boolean mResumable = true;

    private String mExpectedBootloaderVersion, mExpectedBasebandVersion;
    private LogReceiver mKmsgReceiver;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mOtaDeviceBuild = (OtaDeviceBuildInfo)buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set the run name
     */
    void setRunName(String runName) {
        mRunName = runName;
    }

    /**
     * Return the number of iterations.
     * <p/>
     * Exposed for unit testing
     */
    public int getIterations() {
        return mIterations;
    }

    /**
     * Set the iterations
     */
    void setIterations(int iterations) {
        mIterations = iterations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // started run, turn to off
        mResumable = false;
        mKmsgReceiver = new LogReceiver(getDevice(), KMSG_CMD, "kmsg");
        checkFields();

        CLog.i("Starting OTA sideload test from %s to %s, for %d iterations",
                mOtaDeviceBuild.getDeviceImageVersion(),
                mOtaDeviceBuild.getOtaBuild().getOtaPackageVersion(), mIterations);

        getBasebandBootloaderVersions(mOtaDeviceBuild.getOtaBuild());

        long startTime = System.currentTimeMillis();
        listener.testRunStarted(mRunName, 0);
        int actualIterations = 0;
        try {
            while (actualIterations < mIterations) {
                if (actualIterations != 0) {
                    // don't need to flash device on first iteration
                    flashDevice();
                }
                installOta(listener, mOtaDeviceBuild.getOtaBuild());
                actualIterations++;
                CLog.i("Device %s successfully OTA-ed to build %s. Iteration: %d of %d",
                        mDevice.getSerialNumber(),
                        mOtaDeviceBuild.getOtaBuild().getOtaPackageVersion(),
                        actualIterations, mIterations);
            }
        } catch (AssertionFailedError error) {
            CLog.e(error);
        } catch (TargetSetupError e) {
            CLog.i("Encountered TargetSetupError, marking this test as resumable");
            mResumable = true;
            CLog.e(e);
            // throw up an exception so this test can be resumed
            Assert.fail(e.toString());
        } catch (BuildError e) {
            CLog.e(e);
        } catch (ConfigurationException e) {
            CLog.e(e);
        } finally {
            // if the device is down, we need to recover it so we can safely pull logs
            IManagedTestDevice managedDevice = (IManagedTestDevice) mDevice;
            if (!managedDevice.getDeviceState().equals(TestDeviceState.ONLINE)) {
                // not all IDeviceRecovery implementations can handle getting out of recovery mode,
                // so we should just reboot in that case since we no longer need to be in
                // recovery
                CLog.i("Device is not online, attempting to recover before capturing logs");
                if (managedDevice.getDeviceState().equals(TestDeviceState.RECOVERY)) {
                    CLog.i("Rebooting to exit recovery");
                    try {
                        // we don't want to enable root until the reboot is fully finished and
                        // the device is available, or it may get stuck in recovery and time out
                        managedDevice.getIDevice().reboot(null);
                        managedDevice.waitForDeviceAvailable(mMaxRebootTimeSec * 1000);
                        managedDevice.postBootSetup();
                    } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
                        CLog.e("Failed to reboot due to %s, trying last-ditch recovery", e);
                    }
                }
                mConfiguration.getDeviceRecovery().recoverDevice(managedDevice.getMonitor(), false);
            }
            double updateTime = sendRecoveryLog(listener);
            Map<String, String> metrics = new HashMap<String, String>(1);
            metrics.put("iterations", Integer.toString(actualIterations));
            metrics.put("failed_iterations", Integer.toString(mIterations - actualIterations));
            metrics.put("update_time", Double.toString(updateTime));
            long endTime = System.currentTimeMillis() - startTime;
            listener.testRunEnded(endTime, metrics);
        }
    }

    /**
     * Flash the device back to baseline build.
     * <p/>
     * Currently does this by re-running {@link ITargetPreparer#setUp(ITestDevice, IBuildInfo)}
     *
     * @throws DeviceNotAvailableException
     * @throws BuildError
     * @throws TargetSetupError
     * @throws ConfigurationException
     */
    private void flashDevice() throws TargetSetupError, BuildError, DeviceNotAvailableException,
            ConfigurationException {
        // assume the target preparers will flash the device back to device build
        for (ITargetPreparer preparer : mConfiguration.getTargetPreparers()) {
            preparer.setUp(mDevice, mOtaDeviceBuild);
        }
    }

    /**
     * Get the {@link IRunUtil} instance to use.
     * <p/>
     * Exposed so unit tests can mock.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    private void checkFields() {
        if (mDevice == null) {
            throw new IllegalArgumentException("missing device");
        }
        if (mConfiguration == null) {
            throw new IllegalArgumentException("missing configuration");
        }
        if (mOtaDeviceBuild == null) {
            throw new IllegalArgumentException("missing build info");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResumable() {
        return mResumeMode && mResumable;
    }

    private void installOta(ITestInvocationListener listener, IDeviceBuildInfo otaBuild)
            throws DeviceNotAvailableException {
        mKmsgReceiver.start();
        CLog.i("Pushing OTA package %s", otaBuild.getOtaPackageFile().getAbsolutePath());
        Assert.assertTrue(mDevice.pushFile(otaBuild.getOtaPackageFile(), mPackageDataPath));
        // this file needs to be uncrypted, since /data isn't mounted in recovery
        // block.map should be empty since cache should be cleared
        mDevice.pushString(mPackageDataPath + "\n", UNCRYPT_FILE_PATH);
        try {
            doUncrypt(SocketFactory.getInstance(), listener);
            String installOtaCmd = String.format("--update_package=%s\n", BLOCK_MAP_PATH);
            mDevice.pushString(installOtaCmd, RECOVERY_COMMAND_PATH);
            CLog.i("Rebooting to install OTA");
        } finally {
            // Kmsg contents during the OTA will be capture in last_kmsg, so we can turn off the
            // kmsg receiver now
            mKmsgReceiver.postLog(listener);
            mKmsgReceiver.stop();
        }
        try {
            mDevice.rebootIntoRecovery();
        } catch (DeviceNotAvailableException e) {
            // The device will only enter the RECOVERY state if it hits the recovery menu.
            // Since we added a command to /cache/recovery/command, recovery mode executes the
            // command rather than booting into the menu. While applying the update as a result
            // of the installed command, the device reports its state as NOT_AVAILABLE. If the
            // device *actually* becomes unavailable, we will catch the resulting DNAE in the
            // next call to waitForDeviceOnline.
            CLog.i("Didn't go to recovery, went straight to update");
        }

        try {
            mDevice.waitForDeviceOnline(mMaxInstallOnlineTimeSec * 1000);
        } catch (DeviceNotAvailableException e) {
            CLog.e("Device %s did not come back online after recovery", mDevice.getSerialNumber());
            listener.testRunFailed("Device did not come back online after recovery");
            throw e;
        }

        try {
            mDevice.waitForDeviceAvailable();
        } catch (DeviceNotAvailableException e) {
            CLog.e("Device %s did not boot up successfully after installing OTA",
                    mDevice.getSerialNumber());
            listener.testRunFailed("Device failed to boot after OTA");
            throw e;
        }

    }

    private InputStreamSource pullLogFile(String location)
            throws DeviceNotAvailableException {
        File destFile = null;
        InputStreamSource destSource = null;
        try {
            // get recovery log
            destFile = FileUtil.createTempFile("recovery", "log");
            boolean gotFile = mDevice.pullFile(location, destFile);
            if (gotFile) {
                destSource = new SnapshotInputStreamSource(new FileInputStream(destFile));
                return destSource;
            }
        } catch (IOException e) {
            CLog.e("Failed to get recovery log from device %s", mDevice.getSerialNumber());
            CLog.e(e);
        }
        return null;
    }

    protected double sendRecoveryLog(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        InputStreamSource lastLog = pullLogFile(LOG_RECOV);
        double elapsedTime = 0;
        // last_log contains a timing metric in its last line, capture it here and return it
        // for the metrics map to report
        try {
            String[] lastLogLines = StreamUtil.getStringFromSource(lastLog).split("\n");
            String endLine = lastLogLines[lastLogLines.length-1];
            elapsedTime = Double.parseDouble(
                    endLine.substring(endLine.indexOf('[') + 1, endLine.indexOf(']')).trim());
        } catch (IOException|NumberFormatException|NullPointerException e) {
            CLog.w("Couldn't get elapsed time from last_log due to exception %s", e);
            return 0;
        }
        listener.testLog(this.mRunName + "_recovery_log", LogDataType.TEXT,
                lastLog);
        listener.testLog(this.mRunName + "_recovery_kmsg", LogDataType.TEXT,
                pullLogFile(LOG_KMSG));
        lastLog.cancel();
        return elapsedTime;
    }

    private void getBasebandBootloaderVersions(IDeviceBuildInfo otaBuild) {
        try {
            FlashingResourcesParser parser = new FlashingResourcesParser(
                    otaBuild.getDeviceImageFile());
            mExpectedBootloaderVersion = parser.getRequiredBootloaderVersion();
            mExpectedBasebandVersion = parser.getRequiredBasebandVersion();
        } catch (TargetSetupError e) {
            throw new RuntimeException("Error when trying to set up OTA version info");
        }
    }

    /*
     * Uncrypt needs to attach to a socket before it will actually begin work, so we need to
     * attach a socket to it.
     */
    public int doUncrypt(ISocketFactory sockets, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // init has to start uncrypt or the socket will not be allocated
        CLog.i("Starting uncrypt service");
        mDevice.executeShellCommand("setprop ctl.start uncrypt");
        int port;
        try {
            port = getFreePort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // The socket uncrypt wants to run on is a local unix socket, so we can forward a tcp
        // port to connect to it.
        CLog.i("Connecting to uncrypt on port %d", port);
        mDevice.executeAdbCommand("forward", "tcp:" + port, "localreserved:uncrypt");
        // connect to uncrypt!
        Socket uncrypt = null;
        DataInputStream dis = null;
        DataOutputStream dos = null;
        try {
            uncrypt = sockets.createClientSocket("localhost", port);
            int status = Integer.MIN_VALUE;
            dis = new DataInputStream(uncrypt.getInputStream());
            dos = new DataOutputStream(uncrypt.getOutputStream());
            while (true) {
                status = dis.readInt();
                if (status == 100) {
                    CLog.i("Uncrypt finished successfully");
                    dos.writeInt(0);
                    break;
                } else if (status > 100 || status < 0) {
                    // error, acknowledge it to let uncrypt finish
                    CLog.w("Uncrypt sent error status %d", status);
                    dos.writeInt(0);
                    return Integer.MIN_VALUE;
                }
            }
            CLog.i("Final uncrypt status: %d", status);
            return status;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (dos != null) dos.close();
                if (dis != null) dis.close();
                if (uncrypt != null) uncrypt.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    protected int getFreePort() throws IOException {
        try (ServerSocket sock = new ServerSocket(0)) {
            return sock.getLocalPort();
        }
    }

    /**
     * Provides a client socket. Allows for providing mock sockets to doUncrypt in unit testing.
     */
    public interface ISocketFactory {
        public Socket createClientSocket(String host, int port) throws IOException;
    }

    /**
     * Default implementation of {@link ISocketFactory}, which provides a {@link Socket}.
     */
    protected static class SocketFactory implements ISocketFactory {
        private static SocketFactory sInstance;
        private SocketFactory() { }
        public static SocketFactory getInstance() {
            if (sInstance == null) {
                sInstance = new SocketFactory();
            }
            return sInstance;
        }

        @Override
        public Socket createClientSocket(String host, int port) throws IOException {
            return new Socket(host, port);
        }
    }
}
