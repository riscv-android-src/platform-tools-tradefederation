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

package com.android.tradefed.targetprep;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.MultiMap;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ITargetPreparer} that configures a device for testing based on provided {@link Option}s.
 * <p>
 * Requires a device where 'adb root' is possible, typically a userdebug build type.
 * </p><p>
 * Should be performed *after* a new build is flashed.
 * </p><p>
 * The preparer {@link DeviceSetup} requires all settings requires all properties and commands
 * to be run manually, while this preparer emphasizes using {@link Option}s to enable or disable
 * features.
 * </p>
 */
@OptionClass(alias = "device-setup-2")
public class DeviceSetup2 implements ITargetPreparer, ITargetCleaner {

    /**
     * Enum used to record ON/OFF state with a DEFAULT no-op state.
     */
    protected enum BinaryState {
        DEFAULT,
        ON,
        OFF;
    }

    // Networking
    @Option(name = "airplane-mode",
            description = "Turn airplane mode on or off")
    protected BinaryState mAirplaneMode = BinaryState.DEFAULT;
    // ON:  settings put global airplane_mode_on 1
    //      am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
    // OFF: settings put global airplane_mode_on 0
    //      am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false

    @Option(name = "wifi",
            description = "Turn wifi on or off")
    protected BinaryState mWifi = BinaryState.DEFAULT;
    // ON:  settings put global wifi_on 1
    //      svc wifi enable
    // OFF: settings put global wifi_off 0
    //      svc wifi disable

    @Option(name = "wifi-network",
            description = "The SSID of the network to connect to. Will only attempt to " +
            "connect to a network if set")
    protected String mWifiSsid = null;

    @Option(name = "wifi-psk",
            description = "The passphrase used to connect to a secured network")
    protected String mWifiPsk = null;

    @Option(name = "wifi-watchdog",
            description = "Turn wifi watchdog on or off")
    protected BinaryState mWifiWatchdog = BinaryState.DEFAULT;
    // ON:  settings put global wifi_watchdog 1
    // OFF: settings put global wifi_watchdog 0

    @Option(name = "wifi-scan-always-enabled",
            description = "Turn wifi scan always enabled on or off")
    protected BinaryState mWifiScanAlwaysEnabled = BinaryState.DEFAULT;
    // ON:  settings put global wifi_scan_always_enabled 1
    // OFF: settings put global wifi_scan_always_enabled 0

    @Option(name = "ethernet",
            description = "Turn ethernet on or off")
    protected BinaryState mEthernet = BinaryState.DEFAULT;
    // ON:  ifconfig eth0 up
    // OFF: ifconfig eth0 down

    @Option(name = "bluetooth",
            description = "Turn bluetooth on or off")
    protected BinaryState mBluetooth = BinaryState.DEFAULT;
    // ON:  service call bluetooth_manager 6
    // OFF: service call bluetooth_manager 8

    // Screen
    @Option(name = "screen-adaptive-brightness",
            description = "Turn screen adaptive brightness on or off")
    protected BinaryState mScreenAdaptiveBrightness = BinaryState.DEFAULT;
    // ON:  settings put system screen_brightness_mode 1
    // OFF: settings put system screen_brightness_mode 0

    @Option(name = "screen-brightness",
            description = "Set the screen brightness. This is uncalibrated from product to product")
    protected Integer mScreenBrightness = null;
    // settings put system screen_brightness $N

    @Option(name = "screen-always-on",
            description = "Turn 'screen always on' on or off. If ON, then screen-timeout-secs " +
            "must be unset. Will only work when the device is plugged in")
    protected BinaryState mScreenAlwaysOn = BinaryState.DEFAULT;
    // ON:  svc power stayon true
    // OFF: svc power stayon false

    @Option(name = "screen-timeout-secs",
            description = "Set the screen timeout in seconds. If set, then screen-always-on must " +
            "be OFF or DEFAULT")
    protected Long mScreenTimeoutSecs = null;
    // settings put system screen_off_timeout $(N * 1000)

    @Option(name = "screen-ambient-mode",
            description = "Turn screen ambient mode on or off")
    protected BinaryState mScreenAmbientMode = BinaryState.DEFAULT;
    // ON:  settings put secure doze_enabled 1
    // OFF: settings put secure doze_enabled 0

    @Option(name = "wake-gesture",
            description = "Turn wake gesture on or off")
    protected BinaryState mWakeGesture = BinaryState.DEFAULT;
    // ON:  settings put secure wake_gesture_enabled 1
    // OFF: settings put secure wake_gesture_enabled 0

    @Option(name = "screen-saver",
            description = "Turn screen saver on or off")
    protected BinaryState mScreenSaver = BinaryState.DEFAULT;
    // ON:  settings put secure screensaver_enabled 1
    // OFF: settings put secure screensaver_enabled 0

    @Option(name = "notification-led",
            description = "Turn the notification led on or off")
    protected BinaryState mNotificationLed = BinaryState.DEFAULT;
    // ON:  settings put system notification_light_pulse 1
    // OFF: settings put system notification_light_pulse 0

    // Media
    @Option(name = "trigger-media-mounted",
            description = "Trigger a MEDIA_MOUNTED broadcast")
    protected boolean mTriggerMediaMounted = false;
    // am broadcast -a android.intent.action.MEDIA_MOUNTED -d file://${EXTERNAL_STORAGE}

    // Location
    @Option(name = "location-gps",
            description = "Turn the GPS location on or off")
    protected BinaryState mLocationGps = BinaryState.DEFAULT;
    // ON:  settings put secure location_providers_allowed +gps
    // OFF: settings put secure location_providers_allowed -gps

    @Option(name = "location-network",
            description = "Turn the network location on or off")
    protected BinaryState mLocationNetwork = BinaryState.DEFAULT;
    // ON:  settings put secure location_providers_allowed +network
    // OFF: settings put secure location_providers_allowed -network

    // Sensor
    @Option(name = "auto-rotate",
            description = "Turn auto rotate on or off")
    protected BinaryState mAutoRotate = BinaryState.DEFAULT;
    // ON:  settings put system accelerometer_rotation 1
    // OFF: settings put system accelerometer_rotation 0

    // Power
    @Option(name = "battery-saver-mode",
            description = "Turn battery saver mode manually on or off. If OFF but battery is " +
            "less battery-saver-trigger, the device will still go into battery saver mode")
    protected BinaryState mBatterySaver = BinaryState.DEFAULT;
    // ON:  settings put global low_power 1
    // OFF: settings put global low_power 0

    @Option(name = "battery-saver-trigger",
            description = "Set the battery saver trigger level. Should be [1-99] to enable, or " +
            "0 to disable automatic battery saver mode")
    protected Integer mBatterySaverTrigger = null;
    // settings put global low_power_trigger_level $N

    // Time
    @Option(name = "auto-update-time",
            description = "Turn auto update time on or off")
    protected BinaryState mAutoUpdateTime = BinaryState.DEFAULT;
    // ON:  settings put system auto_time 1
    // OFF: settings put system auto_time 0

    @Option(name = "auto-update-timezone",
            description = "Turn auto update timezone on or off")
    protected BinaryState mAutoUpdateTimezone = BinaryState.DEFAULT;
    // ON:  settings put system auto_timezone 1
    // OFF: settings put system auto_timezone 0

    // Calling
    @Option(name = "disable-dialing",
            description = "Disable dialing")
    protected boolean mDisableDialing = true;
    // setprop ro.telephony.disable-call true"

    @Option(name = "default-sim-data",
            description = "Set the default sim card slot for data. Leave unset for single SIM " +
            "devices")
    protected Integer mDefaultSimData = null;
    // settings put global multi_sim_data_call $N

    @Option(name = "default-sim-voice",
            description = "Set the default sim card slot for voice calls. Leave unset for single " +
            "SIM devices")
    protected Integer mDefaultSimVoice = null;
    // settings put global multi_sim_voice_call $N

    @Option(name = "default-sim-sms",
            description = "Set the default sim card slot for SMS. Leave unset for single SIM " +
            "devices")
    protected Integer mDefaultSimSms = null;
    // settings put global multi_sim_sms $N

    // Audio
    @Option(name = "disable-audio",
            description = "Disable the audio")
    protected boolean mDisableAudio = true;
    // setprop ro.audio.silent 1"

    // Test harness
    @Option(name = "disable",
            description = "Disable the entire setup")
    protected boolean mDisable = false;

    @Option(name = "force-skip-system-props",
            description = "Force setup to not modify any device system properties. All other " +
            "system property options will be ignored")
    protected boolean mForceSkipSystemProps = false;

    @Option(name = "force-skip-settings",
            description = "Force setup to not modify any device settings. All other setting " +
            "options will be ignored.")
    protected boolean mForceSkipSettings = false;

    @Option(name = "force-skip-run-commands",
            description = "Force setup to not run any additional commands. All other commands " +
            "will be ignored.")
    protected boolean mForceSkipRunCommands = false;

    @Option(name = "set-test-harness",
            description = "Set the read-only test harness flag on boot")
    protected boolean mSetTestHarness = true;
    // setprop ro.monkey 1"
    // setprop ro.test_harness 1"

    @Option(name = "disable-dalvik-verifier",
            description = "Disable the dalvik verifier on device. Allows package-private " +
            "framework tests to run.")
    protected boolean mDisableDalvikVerifier = false;
    // setprop dalvik.vm.dexopt-flags v=n

    @Option(name="setprop",
            description="Set the specified property on boot. May be repeated.")
    protected Map<String, String> mSetProps = new HashMap<>();

    @Option(name = "system-setting",
            description = "Change a system (non-secure) setting. May be repeated.")
    protected MultiMap<String, String> mSystemSettings = new MultiMap<>();

    @Option(name = "secure-setting",
            description = "Change a secure setting. May be repeated.")
    protected MultiMap<String, String> mSecureSettings = new MultiMap<>();

    @Option(name = "global-setting",
            description = "Change a global setting. May be repeated.")
    protected MultiMap<String, String> mGlobalSettings = new MultiMap<>();

    protected List<String> mRunCommandBeforeSettings = new ArrayList<>();

    @Option(name = "run-command",
            description = "Run an adb shell command. May be repeated")
    protected List<String> mRunCommandAfterSettings = new ArrayList<>();

    @Option(name = "disconnect-wifi-after-test",
            description = "Disconnect from wifi network after test completes.")
    private boolean mDisconnectWifiAfterTest = true;

    @Option(name = "min-external-store-space-kb",
            description="The minimum amount of free space in KB that must be present on device's " +
            "external storage.")
    protected long mMinExternalStoreSpace = 500;

    @Option(name = "local-data-path",
            description = "Optional local file path of test data to sync to device's external " +
            "storage. Use --remote-data-path to set remote location.")
    protected File mLocalDataFile = null;

    @Option(name = "remote-data-path",
            description = "Optional file path on device's external storage to sync test data. " +
            "Must be used with --local-data-path.")
    protected String mRemoteDataPath = null;

    private static final String PERSIST_PREFIX = "persist.";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mDisable) {
            return;
        }

        CLog.i("Performing setup on %s", device.getSerialNumber());

        if (!device.enableAdbRoot()) {
            throw new TargetSetupError(String.format("Failed to enable adb root on %s",
                    device.getSerialNumber()));
        }

        processOptions(device);
        changeSystemProps(device);
        runCommands(device, mRunCommandBeforeSettings);
        changeSettings(device);
        syncTestData(device);
        runCommands(device, mRunCommandAfterSettings);
        checkExternalStoreSpace(device);

        device.clearErrorDialogs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable) {
            return;
        }

        CLog.i("Performing teardown on %s", device.getSerialNumber());

        if (e instanceof DeviceFailedToBootError) {
            CLog.d("boot failure: skipping teardown");
            return;
        }

        // Only try to disconnect if wifi ssid is set since isWifiEnabled() is a heavy operation
        // which should be avoided when possible
        if (mDisconnectWifiAfterTest && mWifiSsid != null && device.isWifiEnabled()) {
            boolean result = device.disconnectFromWifi();
            if (result) {
                CLog.i("Successfully disconnected from wifi network on %s",
                        device.getSerialNumber());
            } else {
                CLog.w("Failed to disconnect from wifi network on %s", device.getSerialNumber());
            }
        }
    }

    /**
     * Process all the {@link Option}s and turn them into system props, settings, or run commands.
     * Does not run any commands on the device at this time.
     * <p>
     * Exposed so that children classes may override this.
     * </p>
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if the {@link Option}s conflict
     */
    @SuppressWarnings("unused")
    protected void processOptions(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        setSettingForBinaryState(mWifi, mGlobalSettings, "wifi_on", "1", "0");
        setCommandForBinaryState(mWifi, mRunCommandAfterSettings,
                "svc wifi enable", "svc wifi disable");

        setSettingForBinaryState(mWifiWatchdog, mGlobalSettings, "wifi_watchdog", "1", "0");

        setSettingForBinaryState(mWifiScanAlwaysEnabled, mGlobalSettings,
                "wifi_scan_always_enabled", "1", "0");

        setCommandForBinaryState(mEthernet, mRunCommandAfterSettings,
                "ifconfig eth0 up", "ifconfig eth0 down");

        setCommandForBinaryState(mBluetooth, mRunCommandAfterSettings,
                "service call bluetooth_manager 6", "service call bluetooth_manager 8");

        if (mScreenBrightness != null && BinaryState.ON.equals(mScreenAdaptiveBrightness)) {
            throw new TargetSetupError("Option screen-brightness cannot be set when " +
                    "screen-adaptive-brightness is set to ON");
        }

        setSettingForBinaryState(mScreenAdaptiveBrightness, mSystemSettings,
                "screen_brightness_mode", "1", "0");

        if (mScreenBrightness != null) {
            mSystemSettings.put("screen_brightness", Integer.toString(mScreenBrightness));
        }

        // Screen timeout cannot be set if screen always on is set to ON
        if (mScreenTimeoutSecs != null && BinaryState.ON.equals(mScreenAlwaysOn)) {
            throw new TargetSetupError("Option screen-timeout-secs cannot be set when " +
                    "screen-always-on is set to ON");
        }

        setCommandForBinaryState(mScreenAlwaysOn, mRunCommandAfterSettings,
                "svc power stayon true", "svc power stayon false");

        if (mScreenTimeoutSecs != null) {
            mSystemSettings.put("screen_off_timeout", Long.toString(mScreenTimeoutSecs * 1000));
        }

        setSettingForBinaryState(mScreenAmbientMode, mSecureSettings, "doze_enabled", "1", "0");

        setSettingForBinaryState(mWakeGesture, mSecureSettings, "wake_gesture_enabled", "1", "0");

        setSettingForBinaryState(mScreenSaver, mSecureSettings, "screensaver_enabled", "1", "0");

        setSettingForBinaryState(mNotificationLed, mSystemSettings,
                "notification_light_pulse", "1", "0");

        if (mTriggerMediaMounted) {
            mRunCommandAfterSettings.add("am broadcast -a android.intent.action.MEDIA_MOUNTED -d " +
                    "file://${EXTERNAL_STORAGE}");
        }

        setSettingForBinaryState(mLocationGps, mSecureSettings,
                "location_providers_allowed", "+gps", "-gps");

        setSettingForBinaryState(mLocationNetwork, mSecureSettings,
                "location_providers_allowed", "+network", "-network");

        setSettingForBinaryState(mAutoRotate, mSystemSettings, "accelerometer_rotation", "1", "0");

        setCommandForBinaryState(mBatterySaver, mRunCommandBeforeSettings,
                "dumpsys battery set usb 0", null);
        setSettingForBinaryState(mBatterySaver, mGlobalSettings, "low_power", "1", "0");

        if (mBatterySaverTrigger != null) {
            mGlobalSettings.put("low_power_trigger_level", Integer.toString(mBatterySaverTrigger));
        }

        setSettingForBinaryState(mAutoUpdateTime, mSystemSettings, "auto_time", "1", "0");

        setSettingForBinaryState(mAutoUpdateTimezone, mSystemSettings, "auto_timezone", "1", "0");

        if (mDisableDialing) {
            mSetProps.put("ro.telephony.disable-call", "true");
        }

        if (mDefaultSimData != null) {
            mGlobalSettings.put("multi_sim_data_call", Integer.toString(mDefaultSimData));
        }

        if (mDefaultSimVoice != null) {
            mGlobalSettings.put("multi_sim_voice_call", Integer.toString(mDefaultSimVoice));
        }

        if (mDefaultSimSms != null) {
            mGlobalSettings.put("multi_sim_sms", Integer.toString(mDefaultSimSms));
        }

        if (mDisableAudio) {
            mSetProps.put("ro.audio.silent", "1");
        }

        if (mSetTestHarness) {
            // set both ro.monkey and ro.test_harness, for compatibility with older platforms
            mSetProps.put("ro.monkey", "1");
            mSetProps.put("ro.test_harness", "1");
        }

        if (mDisableDalvikVerifier) {
            mSetProps.put("dalvik.vm.dexopt-flags", "v=n");
        }
    }

    /**
     * Change the system properties on the device.
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if there was a failure setting the system properties
     */
    private void changeSystemProps(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mForceSkipSystemProps) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> prop : mSetProps.entrySet()) {
            if (prop.getKey().startsWith(PERSIST_PREFIX)) {
                String command = String.format("setprop %s %s", prop.getKey(), prop.getValue());
                device.executeShellCommand(command);
            } else {
                sb.append(String.format("%s=%s\n", prop.getKey(), prop.getValue()));
            }
        }

        if (sb.length() == 0) {
            return;
        }

        boolean result = device.pushString(sb.toString(), "/data/local.prop");
        if (!result) {
            throw new TargetSetupError(String.format("Failed to push file to %s",
                    device.getSerialNumber()));
        }
        // Set reasonable permissions for /data/local.prop
        device.executeShellCommand("chmod 644 /data/local.prop");
        CLog.i("Setup requires system property change. Reboot of %s required",
                device.getSerialNumber());
        device.reboot();
    }

    /**
     * Change the settings on the device.
     * <p>
     * Exposed so children classes may override.
     * </p>
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if there was a failure setting the settings
     */
    protected void changeSettings(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mForceSkipSettings) {
            return;
        }

        if (mSystemSettings.isEmpty() && mSecureSettings.isEmpty() && mGlobalSettings.isEmpty()) {
            return;
        }

        if (device.getApiLevel() < 22) {
            throw new TargetSetupError(String.format("Changing setting not supported on %s, " +
                    "must be API 22+", device.getSerialNumber()));
        }

        // Special case airplane mode since it needs to be set before other connectivity settings
        // For example, it is possible to enable airplane mode and then turn wifi on
        String command = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state %s";
        switch (mAirplaneMode) {
            case ON:
                CLog.d("Changing global setting airplane_mode_on to 1");
                device.executeShellCommand("settings put global airplane_mode_on 1");
                if (!mForceSkipRunCommands) {
                    device.executeShellCommand(String.format(command, "true"));
                }
                break;
            case OFF:
                CLog.d("Changing global setting airplane_mode_on to 0");
                device.executeShellCommand("settings put global airplane_mode_on 0");
                if (!mForceSkipRunCommands) {
                    device.executeShellCommand(String.format(command, "false"));
                }
                break;
            case DEFAULT:
                // No-op
                break;
        }

        for (String key : mSystemSettings.keySet()) {
            for (String value : mSystemSettings.get(key)) {
                CLog.d("Changing system setting %s to %s", key, value);
                device.executeShellCommand(String.format("settings put system %s %s", key, value));
            }
        }
        for (String key : mSecureSettings.keySet()) {
            for (String value : mSecureSettings.get(key)) {
                CLog.d("Changing secure setting %s to %s", key, value);
                device.executeShellCommand(String.format("settings put secure %s %s", key, value));
            }
        }

        for (String key : mGlobalSettings.keySet()) {
            for (String value : mGlobalSettings.get(key)) {
                CLog.d("Changing global setting %s to %s", key, value);
                device.executeShellCommand(String.format("settings put global %s %s", key, value));
            }
        }
    }

    /**
     * Execute additional commands on the device.
     *
     * @param device The {@link ITestDevice}
     * @param commands The list of commands to run
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if there was a failure setting the settings
     */
    private void runCommands(ITestDevice device, List<String> commands)
            throws DeviceNotAvailableException, TargetSetupError {
        if (mForceSkipRunCommands) {
            return;
        }

        for (String command : commands) {
            device.executeShellCommand(command);
        }

        if (mWifiSsid != null) {
            if (!device.connectToWifiNetwork(mWifiSsid, mWifiPsk)) {
                throw new TargetSetupError(String.format(
                        "Failed to connect to wifi network %s on %s", mWifiSsid,
                        device.getSerialNumber()));
            }
        }
    }

    /**
     * Syncs a set of test data files, specified via local-data-path, to devices external storage.
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if data fails to sync
     */
    private void syncTestData(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mLocalDataFile == null) {
            return;
        }

        if (!mLocalDataFile.exists() || !mLocalDataFile.isDirectory()) {
            throw new TargetSetupError(String.format(
                    "local-data-path %s is not a directory", mLocalDataFile.getAbsolutePath()));
        }
        String fullRemotePath = device.getIDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        if (fullRemotePath == null) {
            throw new TargetSetupError(String.format(
                    "failed to get external storage path on device %s", device.getSerialNumber()));
        }
        if (mRemoteDataPath != null) {
            fullRemotePath = String.format("%s/%s", fullRemotePath, mRemoteDataPath);
        }
        boolean result = device.syncFiles(mLocalDataFile, fullRemotePath);
        if (!result) {
            // TODO: get exact error code and respond accordingly
            throw new TargetSetupError(String.format(
                    "failed to sync test data from local-data-path %s to %s on device %s",
                    mLocalDataFile.getAbsolutePath(), fullRemotePath, device.getSerialNumber()));
        }
    }

    /**
     * Check that device external store has the required space
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available or if the device does not
     * have the required space
     */
    private void checkExternalStoreSpace(ITestDevice device) throws DeviceNotAvailableException {
        if (mMinExternalStoreSpace <= 0) {
            return;
        }

        long freeSpace = device.getExternalStoreFreeSpace();
        if (freeSpace < mMinExternalStoreSpace) {
            throw new DeviceNotAvailableException(String.format(
                    "External store free space %dK is less than required %dK for device %s",
                    freeSpace , mMinExternalStoreSpace, device.getSerialNumber()));
        }
    }

    /**
     * Helper method to add an ON/OFF setting to a setting map.
     *
     * @param state The {@link BinaryState}
     * @param settingsMap The {@link MultiMap} used to store the settings.
     * @param setting The setting key
     * @param onValue The value if ON
     * @param offValue The value if OFF
     */
    protected void setSettingForBinaryState(BinaryState state, MultiMap<String, String> settingsMap,
            String setting, String onValue, String offValue) {
        switch (state) {
            case ON:
                settingsMap.put(setting, onValue);
                break;
            case OFF:
                settingsMap.put(setting, offValue);
                break;
            case DEFAULT:
                // Do nothing
                break;
        }
    }

    /**
     * Helper method to add an ON/OFF run command to be executed on the device.
     *
     * @param state The {@link BinaryState}
     * @param commands The list of commands to add the on or off command to.
     * @param onCommand The command to run if ON. Ignored if the command is {@code null}
     * @param offCommand The command to run if OFF. Ignored if the command is {@code null}
     */
    protected void setCommandForBinaryState(BinaryState state, List<String> commands,
            String onCommand, String offCommand) {
        switch (state) {
            case ON:
                if (onCommand != null) {
                    commands.add(onCommand);
                }
                break;
            case OFF:
                if (offCommand != null) {
                    commands.add(offCommand);
                }
                break;
            case DEFAULT:
                // Do nothing
                break;
        }
    }
}
