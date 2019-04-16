/*
 * Copyright (C) 2019 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.List;

/** Utility helper to control Launch_cvd in the Cuttlefish VM. */
public class LaunchCvdHelper {

    /**
     * Create the command line to start an additional device for a user.
     *
     * @param username The user that will run the device.
     * @param daemon Whether or not to start the device as a daemon.
     * @return The created command line;
     */
    public static List<String> createSimpleDeviceCommand(String username, boolean daemon) {
        List<String> command = new ArrayList<>();
        command.add("sudo -u " + username);
        command.add("/home/" + username + "/bin/launch_cvd");
        command.add("-data_policy");
        command.add("always_create");
        command.add("-blank_data_image_mb");
        command.add("8000");
        if (daemon) {
            command.add("-daemon");
        }
        return command;
    }
}
