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

package com.android.tradefed.testtype;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestLifeCycleReceiver;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TimeUtil;

import org.junit.Assert;

import java.util.HashMap;

/**
 * An {@link IRemoteTest} that checks for a minimum battery charge, and waits for the battery to
 * reach a second charging threshold if the minimum charge isn't present.
 */
@OptionClass(alias = "battery-checker")
public class DeviceBatteryLevelChecker implements IRemoteTest {

    private ITestDevice mTestDevice = null;
    private TestDescription mTestDescription = new TestDescription("BatteryCharging", "charge");

    /**
     * We use max-battery here to coincide with a {@link DeviceSelectionOptions} option of the same
     * name.  Thus, DeviceBatteryLevelChecker
     */
    @Option(name = "max-battery", description = "Charge level below which we force the device to " +
            "sit and charge.  Range: 0-100.")
    private Integer mMaxBattery = 20;

    @Option(name = "resume-level", description = "Charge level at which we release the device to " +
            "begin testing again. Range: 0-100.")
    private int mResumeLevel = 80;

    /**
     * This is decoupled from the log poll time below specifically to allow this invocation to be
     * killed without having to wait for the full log period to lapse.
     */
    @Option(name = "poll-time", description = "Time in minutes to wait between battery level " +
            "polls. Decimal times accepted.")
    private double mChargingPollTime = 1.0;

    @Option(name = "batt-log-period", description = "Min time in minutes to wait between " +
            "printing current battery level to log.  Decimal times accepted.")
    private double mLoggingPollTime = 10.0;

    @Option(name = "reboot-charging-devices", description = "Whether to reboot a device when we " +
            "detect that it should be held for charging.  This would hopefully kill any battery-" +
            "draining processes and allow the device to charge at its fastest rate.")
    private boolean mRebootChargeDevices = false;

    @Option(name = "stop-runtime", description = "Whether to stop runtime.")
    private boolean mStopRuntime = false;

    @Option(name = "stop-logcat", description = "Whether to stop logcat during the recharge. "
            + "this option is enabled by default.")
    private boolean mStopLogcat = true;

    @Option(
        name = "max-run-time",
        description = "The max run time the battery level checker can run before stopping.",
        isTimeVal = true
    )
    private long mMaxRunTime = 30 * 60 * 1000L;

    Integer checkBatteryLevel(ITestDevice device) {
        return device.getBattery();
    }

    private void stopDeviceRuntime() throws DeviceNotAvailableException {
        mTestDevice.executeShellCommand("stop");
    }

    private void startDeviceRuntime() throws DeviceNotAvailableException {
        mTestDevice.executeShellCommand("start");
        mTestDevice.waitForDeviceAvailable();
    }

    /** {@inheritDoc} */
    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        mTestDevice = testInfo.getDevice();
        Assert.assertNotNull(mTestDevice);

        long startTime = System.currentTimeMillis();
        listener.testRunStarted("BatteryCharging", 1);
        listener.testStarted(mTestDescription);
        try {
            runTest(testInfo, listener);
        } finally {
            listener.testEnded(mTestDescription, new HashMap<String, Metric>());
            listener.testRunEnded(
                    System.currentTimeMillis() - startTime, new HashMap<String, Metric>());
        }
    }

    private void runTest(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        mTestDevice = testInfo.getDevice();
        Assert.assertNotNull(mTestDevice);

        Integer batteryLevel = checkBatteryLevel(mTestDevice);

        if (batteryLevel == null) {
            CLog.w("Failed to determine battery level for device %s.",
                    mTestDevice.getSerialNumber());
            listener.testFailed(
                    mTestDescription,
                    FailureDescription.create("Failed to determine battery level"));
            return;
        } else if (batteryLevel < mMaxBattery) {
            // Time-out.  Send the device to the corner
            CLog.w("Battery level %d is below the min level %d; holding for device %s to charge " +
                    "to level %d", batteryLevel, mMaxBattery, mTestDevice.getSerialNumber(),
                    mResumeLevel);
        } else {
            // Good to go
            CLog.d("Battery level %d is above the minimum of %d; %s is good to go.", batteryLevel,
                    mMaxBattery, mTestDevice.getSerialNumber());
            return;
        }

        if (mRebootChargeDevices) {
            // reboot the device, in an attempt to kill any battery-draining processes
            CLog.d("Rebooting device %s prior to holding", mTestDevice.getSerialNumber());
            mTestDevice.reboot();
        }

        // turn screen off
        turnScreenOff(mTestDevice);

        try {
            if (mStopRuntime) {
                stopDeviceRuntime();
            }
            // Stop our logcat receiver
            if (mStopLogcat) {
                mTestDevice.stopLogcat();
            }

            runBatteryCharging(listener, mTestDescription);
            // TODO: Check the charging speed
        } finally {
            if (mStopRuntime) {
                // Restart runtime if it was stopped
                startDeviceRuntime();
            }
        }

        CLog.i(
                "Device %s is now charged to battery level %d; releasing.",
                mTestDevice.getSerialNumber(), batteryLevel);
    }

    private void turnScreenOff(ITestDevice device) throws DeviceNotAvailableException {
        // TODO: Handle the case where framework is not working, both command below require it.
        // disable always on
        device.executeShellCommand("svc power stayon false");
        // set screen timeout to 1s
        device.executeShellCommand("settings put system screen_off_timeout 1000");
        // pause for 5s to ensure that screen would be off
        getRunUtil().sleep(5000);
    }

    private Integer runBatteryCharging(ITestLifeCycleReceiver listener, TestDescription test) {
        // If we're down here, it's time to hold the device until it reaches mResumeLevel
        Long lastReportTime = System.currentTimeMillis();
        Integer batteryLevel = checkBatteryLevel(mTestDevice);

        long startTime = System.currentTimeMillis();
        while (batteryLevel != null && batteryLevel < mResumeLevel) {
            if (System.currentTimeMillis() - lastReportTime > mLoggingPollTime * 60 * 1000) {
                // Log the battery level status every mLoggingPollTime minutes
                CLog.i(
                        "Battery level for device %s is currently %d",
                        mTestDevice.getSerialNumber(), batteryLevel);
                lastReportTime = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - startTime > mMaxRunTime) {
                CLog.i(
                        "DeviceBatteryLevelChecker has been running for %s. terminating.",
                        TimeUtil.formatElapsedTime(mMaxRunTime));
                break;
            }

            getRunUtil().sleep((long) (mChargingPollTime * 60 * 1000));
            Integer newLevel = checkBatteryLevel(mTestDevice);
            if (newLevel == null) {
                // weird
                CLog.w("Breaking out of wait loop because battery level read failed for device %s",
                        mTestDevice.getSerialNumber());
                listener.testFailed(
                        test, FailureDescription.create("Failed to read battery level"));
                break;
            } else if (newLevel < batteryLevel) {
                // also weird
                CLog.w("Warning: battery discharged from %d to %d on device %s during the last " +
                        "%.02f minutes.", batteryLevel, newLevel, mTestDevice.getSerialNumber(),
                        mChargingPollTime);
            } else {
                CLog.v("Battery level for device %s is currently %d", mTestDevice.getSerialNumber(),
                        newLevel);
            }
            batteryLevel = newLevel;
        }
        return batteryLevel;
    }

    /**
     * Get a RunUtil instance
     *
     * <p>Exposed for unit testing
     */
    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    protected void setResumeLevel(int level) {
        mResumeLevel = level;
    }
}

