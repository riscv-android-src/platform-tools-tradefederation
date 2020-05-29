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
package com.android.tradefed.targetprep;

import com.android.helper.aoa.AoaDevice;
import com.android.helper.aoa.UsbHelper;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RegexTrie;

import com.google.common.annotations.VisibleForTesting;

import java.awt.Point;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * {@link ITargetPreparer} that executes a series of actions (e.g. clicks and swipes) using the
 * Android Open Accessory (AOAv2) protocol. This allows controlling an Android device without
 * enabling USB debugging.
 *
 * <p>Accepts a list of strings which correspond to {@link AoaDevice} methods:
 *
 * <ul>
 *   <li>Click using x and y coordinates, e.g. "click 0 0" or "longClick 360 640".
 *   <li>Swipe between two sets of coordinates in a specified number of milliseconds, e.g. "swipe 0
 *       0 100 360 640" to swipe from (0, 0) to (360, 640) in 100 milliseconds.
 *   <li>Write a string of alphanumeric text, e.g. "write hello world" will type "hello world".
 *   <li>Repeated keystrokes using USB HID usages, e.g. "key 5* 0x2B" to press TAB five times.
 *   <li>Key combinations using USB HID usages, e.g. "key 0x52 0x51 0x28" to press UP, DOWN, ENTER.
 *   <li>Wake up the device with "wake".
 *   <li>Press the home button with "home".
 *   <li>Press the back button with "back".
 *   <li>Wait for specified number of milliseconds, e.g. "sleep 1000" to wait for 1000 milliseconds.
 * </ul>
 */
@OptionClass(alias = "aoa-preparer")
public class AoaTargetPreparer extends BaseTargetPreparer {

    private static final String POINT = "(\\d{1,3}) (\\d{1,3})";
    private static final String KEY = "(\\d{1,3}|0[xX][0-9a-fA-F]{1,2})";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @FunctionalInterface
    private interface Action extends BiConsumer<AoaDevice, List<String>> {}

    // Trie of possible actions, parses the input string and determines the operation to execute
    private static final RegexTrie<Action> ACTIONS = new RegexTrie<>();

    static {
        // clicks
        ACTIONS.put(
                (device, args) -> device.click(parsePoint(args.get(0), args.get(1))),
                String.format("click %s", POINT));
        ACTIONS.put(
                (device, args) -> device.longClick(parsePoint(args.get(0), args.get(1))),
                String.format("longClick %s", POINT));

        // swipes
        ACTIONS.put(
                (device, args) -> {
                    Point from = parsePoint(args.get(0), args.get(1));
                    Duration duration = parseMillis(args.get(2));
                    Point to = parsePoint(args.get(3), args.get(4));
                    device.swipe(from, to, duration);
                },
                String.format("swipe %s (\\d+) %s", POINT, POINT));

        // keys
        ACTIONS.put((device, args) -> device.write(args.get(0)), "write ([a-zA-Z0-9 ]+)");
        ACTIONS.put(
                (device, args) -> {
                    int count = Integer.decode(args.get(0));
                    Integer[] keyCodes = new Integer[count];
                    Arrays.fill(keyCodes, Integer.decode(args.get(1)));
                    device.key(keyCodes);
                },
                String.format("key (\\d+)\\* %s", KEY));
        ACTIONS.put(
                (device, args) -> {
                    Integer[] keyCodes =
                            WHITESPACE
                                    .splitAsStream(args.get(0))
                                    .map(Integer::decode)
                                    .toArray(Integer[]::new);
                    device.key(keyCodes);
                },
                String.format("key (%s(?: %s)*)", KEY, KEY));

        // other
        ACTIONS.put((device, args) -> device.wakeUp(), "wake");
        ACTIONS.put((device, args) -> device.goHome(), "home");
        ACTIONS.put((device, args) -> device.goBack(), "back");
        ACTIONS.put(
                (device, args) -> {
                    Duration duration = parseMillis(args.get(0));
                    device.sleep(duration);
                },
                "sleep (\\d+)");
    }

    @Option(name = "device-timeout", description = "Maximum time to wait for device")
    private Duration mDeviceTimeout = Duration.ofMinutes(1L);

    @Option(
        name = "wait-for-device-online",
        description = "Checks whether the device is online after preparation."
    )
    private boolean mWaitForDeviceOnline = true;

    @Option(name = "action", description = "AOAv2 action to perform. Can be repeated.")
    private List<String> mActions = new ArrayList<>();

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mActions.isEmpty()) {
            return;
        }
        ITestDevice device = testInfo.getDevice();
        try {
            configure(device.getSerialNumber());
        } catch (RuntimeException e) {
            throw new TargetSetupError(e.getMessage(), e, device.getDeviceDescriptor());
        }

        if (mWaitForDeviceOnline) {
            // Verify that the device is online after executing AOA actions
            device.waitForDeviceOnline();
        }
    }

    // Connect to device using its serial number and perform actions
    private void configure(String serialNumber) throws DeviceNotAvailableException {
        try (UsbHelper usb = getUsbHelper();
                AoaDevice device = usb.getAoaDevice(serialNumber, mDeviceTimeout)) {
            if (device == null) {
                throw new DeviceNotAvailableException(
                        "AOAv2-compatible device not found", serialNumber);
            }
            CLog.d("Performing %d actions on device %s", mActions.size(), serialNumber);
            mActions.forEach(action -> execute(device, action));
        }
    }

    @VisibleForTesting
    UsbHelper getUsbHelper() {
        return new UsbHelper();
    }

    // Parse and execute an action
    @VisibleForTesting
    void execute(AoaDevice device, String input) {
        CLog.v("Executing '%s' on %s", input, device.getSerialNumber());
        List<List<String>> args = new ArrayList<>();
        Action action = ACTIONS.retrieve(args, input);
        if (action == null) {
            throw new IllegalArgumentException(String.format("Invalid action %s", input));
        }
        action.accept(device, args.get(0));
    }

    // Construct point from string coordinates
    private static Point parsePoint(String x, String y) {
        return new Point(Integer.decode(x), Integer.decode(y));
    }

    // Construct duration from string milliseconds
    private static Duration parseMillis(String millis) {
        return Duration.ofMillis(Long.parseLong(millis));
    }
}
