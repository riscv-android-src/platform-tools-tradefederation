/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.sandbox;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.sandbox.SandboxConfigDump.DumpCmd;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.keystore.IKeyStoreClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A utility class for managing {@link IConfiguration} when doing sandboxing. */
public class SandboxConfigUtil {

    private static final long DUMP_TIMEOUT = 2 * 60 * 1000; // 2min

    /**
     * Create a subprocess based on the Tf jars from any version, and dump the xml {@link
     * IConfiguration} based on the command line args.
     *
     * @param rootDir the directory containing all the jars from TF.
     * @param runUtil the {@link IRunUtil} to use to run the command.
     * @param args the command line args.
     * @param dump the {@link DumpCmd} driving some of the outputs.
     * @return A {@link File} containing the xml dump from the command line.
     * @throws ConfigurationException if the dump is not successful.
     */
    public static File dumpConfigForVersion(
            File rootDir, IRunUtil runUtil, String[] args, DumpCmd dump)
            throws ConfigurationException {
        File destination;
        try {
            destination = FileUtil.createTempFile("config-container", ".xml");
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage());
        }
        List<String> mCmdArgs = new ArrayList<>();
        mCmdArgs.add("java");
        mCmdArgs.add("-cp");
        mCmdArgs.add(new File(rootDir, "*").getAbsolutePath());
        mCmdArgs.add(SandboxConfigDump.class.getCanonicalName());
        mCmdArgs.add(dump.toString());
        mCmdArgs.add(destination.getAbsolutePath());
        for (String arg : args) {
            mCmdArgs.add(arg);
        }
        CommandResult result = runUtil.runTimedCmd(DUMP_TIMEOUT, mCmdArgs.toArray(new String[0]));
        if (CommandStatus.SUCCESS.equals(result.getStatus())) {
            return destination;
        }
        FileUtil.deleteFile(destination);
        throw new ConfigurationException(result.getStderr());
    }

    /**
     * Create a {@link IConfiguration} based on the command line and sandbox provided.
     *
     * @param args the command line for the run.
     * @param sandbox the {@link ISandbox} used for the run.
     * @param configFactory the {@link IConfigurationFactory} used to load the config on parent side
     * @param keystore the {@link IKeyStoreClient} where to load the key from.
     * @return a {@link IConfiguration} valid for the sandbox.
     * @throws ConfigurationException
     */
    public static IConfiguration createSandboxConfiguration(
            String args[],
            ISandbox sandbox,
            IConfigurationFactory configFactory,
            IKeyStoreClient keystore)
            throws ConfigurationException {
        IConfiguration config = null;
        File xmlConfig = null;
        try {
            File tfDir = sandbox.getTradefedEnvironment(args);
            xmlConfig =
                    dumpConfigForVersion(tfDir, new RunUtil(), args, DumpCmd.NON_VERSIONED_CONFIG);
            // Get the non version part of the configuration in order to do proper allocation
            // of devices and such.
            config =
                    configFactory.createConfigurationFromArgs(
                            new String[] {xmlConfig.getAbsolutePath()}, null, keystore);
            // Reset the command line to the original one.
            config.setCommandLine(args);
            config.setConfigurationObject(Configuration.SANDBOX_TYPE_NAME, sandbox);
        } catch (ConfigurationException e) {
            CLog.e(e);
            sandbox.tearDown();
            throw e;
        } finally {
            FileUtil.deleteFile(xmlConfig);
        }
        return config;
    }
}
