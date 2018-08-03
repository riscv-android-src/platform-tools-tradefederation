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

package com.android.tradefed.util;

import com.android.tradefed.config.ConfigurationDef.OptionDef;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationDescriptor.LocalTestRunner;

/** Utility to compile the instruction to run test locally. */
public class LocalRunInstructionBuilder {

    /**
     * Compile the instruction to run test locally.
     *
     * @param configDescriptor {@link ConfigurationDescriptor} to create instruction for.
     * @param runner {@link LocalTestRunner} to be used to build instruction.
     * @return {@link String} of the instruction.
     */
    public static String getInstruction(
            ConfigurationDescriptor configDescriptor, LocalTestRunner runner) {
        if (runner == null) {
            return null;
        }
        switch (runner) {
            case ATEST:
                return getAtestInstruction(configDescriptor);
            default:
                return null;
        }
    }

    /**
     * Compile the instruction to run test locally using atest.
     *
     * @param configDescriptor {@link ConfigurationDescriptor} to create instruction for.
     * @return {@link String} of the instruction.
     */
    private static String getAtestInstruction(ConfigurationDescriptor configDescriptor) {
        StringBuilder instruction = new StringBuilder();
        instruction.append("Run following command to try the test in a local setup:\n");
        instruction.append(String.format("atest %s --", configDescriptor.getModuleName()));
        if (configDescriptor.getAbi() != null) {
            instruction.append(String.format(" --abi %s", configDescriptor.getAbi().getName()));
        }
        for (OptionDef optionDef : configDescriptor.getRerunOptions()) {
            StringBuilder option =
                    new StringBuilder(
                            String.format(
                                    "--module-arg %s:%s:",
                                    configDescriptor.getModuleName(), optionDef.name));
            if (optionDef.key == null) {
                option.append(String.format("%s", optionDef.value));
            } else {
                option.append(String.format("%s:=%s", optionDef.key, optionDef.value));
            }
            instruction.append(" " + option);
        }
        return instruction.toString();
    }
}
