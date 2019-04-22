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

import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import java.util.Arrays;
import java.util.List;

/** Utility to create another user in Cuttlefish VM. New user will allow to run a second device. */
public class MultiUserSetupUtil {

    /** Files that must be copied between users to avoid conflicting ownership */
    private static final List<String> FILE_TO_BE_COPIED =
            Arrays.asList(
                    "android-info.txt",
                    "boot.img",
                    "cache.img",
                    "product.img",
                    "system.img",
                    "vendor.img");

    /** Files that can simply be shared between the different users */
    private static final List<String> FILE_TO_BE_LINKED = Arrays.asList("bin", "config", "lib64");

    /** Setup a new remote user on an existing Cuttlefish VM. */
    public static CommandResult prepareRemoteUser(
            String username,
            GceAvdInfo remoteInstance,
            TestDeviceOptions options,
            IRunUtil runUtil,
            long timeoutMs) {
        // First create the user
        String createUserCommand =
                "sudo useradd " + username + " -G sudo,kvm,cvdnetwork -m -s /bin/bash -p '*'";
        CommandResult createUserRes =
                RemoteSshUtil.remoteSshCommandExec(
                        remoteInstance, options, runUtil, timeoutMs, createUserCommand.split(" "));
        if (!CommandStatus.SUCCESS.equals(createUserRes.getStatus())) {
            return createUserRes;
        }
        return null;
    }

    /** Setup a new remote user on an existing Cuttlefish VM. */
    public static CommandResult prepareRemoteHomeDir(
            String mainRootUser,
            String username,
            GceAvdInfo remoteInstance,
            TestDeviceOptions options,
            IRunUtil runUtil,
            long timeoutMs) {
        StringBuilder copyCommandBuilder = new StringBuilder("sudo cp ");
        for (String file : FILE_TO_BE_COPIED) {
            copyCommandBuilder.append(" /home/" + mainRootUser + "/" + file);
        }
        copyCommandBuilder.append(" /home/" + username + "/");
        CommandResult cpRes =
                RemoteSshUtil.remoteSshCommandExec(
                        remoteInstance,
                        options,
                        runUtil,
                        timeoutMs,
                        copyCommandBuilder.toString().split(" "));
        if (!CommandStatus.SUCCESS.equals(cpRes.getStatus())) {
            return cpRes;
        }
        // Own the copied files
        String chownUser = getChownCommand(username);
        CommandResult chownRes =
                RemoteSshUtil.remoteSshCommandExec(
                        remoteInstance, options, runUtil, timeoutMs, chownUser);
        if (!CommandStatus.SUCCESS.equals(chownRes.getStatus())) {
            return chownRes;
        }
        // Link files that can be shared between users
        for (String file : FILE_TO_BE_LINKED) {
            String copyDevice =
                    "sudo ln -s /home/" + mainRootUser + "/" + file + " /home/" + username + "/";
            CommandResult copyRes =
                    RemoteSshUtil.remoteSshCommandExec(
                            remoteInstance, options, runUtil, timeoutMs, copyDevice.split(" "));
            if (!CommandStatus.SUCCESS.equals(copyRes.getStatus())) {
                return copyRes;
            }
        }
        return null;
    }

    /** Gets the command for a user to own the main directory. */
    public static String getChownCommand(String username) {
        return "find /home/" + username + " | sudo xargs chown " + username;
    }
}
