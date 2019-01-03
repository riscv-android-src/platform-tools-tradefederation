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
package com.android.tradefed.invoker;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.command.Console;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.cloud.GceAvdInfo;
import com.android.tradefed.device.cloud.GceManager;
import com.android.tradefed.device.cloud.ManagedRemoteDevice;
import com.android.tradefed.device.cloud.RemoteFileUtil;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.proto.FileProtoResultReporter;
import com.android.tradefed.result.proto.ProtoResultParser;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TimeUtil;
import com.android.tradefed.util.proto.TestRecordProtoUtil;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Implementation of {@link InvocationExecution} that drives a remote execution. */
public class RemoteInvocationExecution extends InvocationExecution {

    public static final long PUSH_TF_TIMEOUT = 120000L;
    public static final long PULL_RESULT_TIMEOUT = 180000L;

    public static final String REMOTE_USER_DIR = "/home/{$USER}/";
    public static final String PROTO_RESULT_NAME = "output.pb";
    public static final String STDOUT_FILE = "stdout.txt";
    public static final String STDERR_FILE = "stderr.txt";

    private String mRemoteTradefedDir = null;
    private String mRemoteFinalResult = null;

    @Override
    public boolean fetchBuild(
            IInvocationContext context,
            IConfiguration config,
            IRescheduler rescheduler,
            ITestInvocationListener listener)
            throws DeviceNotAvailableException, BuildRetrievalError {
        // TODO: handle multiple devices/build config
        updateInvocationContext(context, config);
        StubBuildProvider stubProvider = new StubBuildProvider();

        String deviceName = config.getDeviceConfig().get(0).getDeviceName();
        OptionCopier.copyOptionsNoThrow(
                config.getDeviceConfig().get(0).getBuildProvider(), stubProvider);

        IBuildInfo info = stubProvider.getBuild();
        if (info == null) {
            return false;
        }
        context.addDeviceBuildInfo(deviceName, info);
        updateBuild(info, config);
        return true;
    }

    @Override
    public void runTests(
            IInvocationContext context, IConfiguration config, ITestInvocationListener listener)
            throws Throwable {
        ManagedRemoteDevice device = (ManagedRemoteDevice) context.getDevices().get(0);
        GceAvdInfo info = device.getRemoteAvdInfo();
        // Run remote TF (new tests?)
        IRunUtil runUtil = new RunUtil();

        TestDeviceOptions options = device.getOptions();
        String mainRemoteDir = getRemoteMainDir(options);

        String tfPath = System.getProperty("TF_JAR_DIR");
        if (tfPath == null) {
            listener.invocationFailed(new RuntimeException("Failed to find $TF_JAR_DIR."));
            return;
        }
        File currentTf = new File(tfPath).getAbsoluteFile();
        if (tfPath.equals(".")) {
            currentTf = new File("").getAbsoluteFile();
        }
        mRemoteTradefedDir = mainRemoteDir + "tradefed/";
        CommandResult createRemoteDir =
                GceManager.remoteSshCommandExecution(
                        info, options, runUtil, 120000L, "mkdir", "-p", mRemoteTradefedDir);
        if (!CommandStatus.SUCCESS.equals(createRemoteDir.getStatus())) {
            listener.invocationFailed(new RuntimeException("Failed to create remote dir."));
            return;
        }

        boolean result =
                RemoteFileUtil.pushFileToRemote(
                        info,
                        options,
                        Arrays.asList("-r"),
                        runUtil,
                        PUSH_TF_TIMEOUT,
                        mRemoteTradefedDir,
                        currentTf);
        if (!result) {
            CLog.e("Failed to push Tradefed.");
            listener.invocationFailed(new RuntimeException("Failed to push Tradefed."));
            return;
        }

        mRemoteTradefedDir = mRemoteTradefedDir + currentTf.getName() + "/";
        CommandResult listRemoteDir =
                GceManager.remoteSshCommandExecution(
                        info, options, runUtil, 120000L, "ls", "-l", mRemoteTradefedDir);
        CLog.d("stdout: %s", listRemoteDir.getStdout());
        CLog.d("stderr: %s", listRemoteDir.getStderr());
        mRemoteFinalResult = mRemoteTradefedDir + PROTO_RESULT_NAME;

        // Setup the remote reporting to a proto file
        FileProtoResultReporter reporter = new FileProtoResultReporter();
        reporter.setFileOutput(new File(mRemoteFinalResult));
        config.setTestInvocationListener(reporter);

        for (IDeviceConfiguration deviceConfig : config.getDeviceConfig()) {
            deviceConfig.getDeviceRequirements().setSerial();
            if (deviceConfig.getDeviceRequirements() instanceof DeviceSelectionOptions) {
                ((DeviceSelectionOptions) deviceConfig.getDeviceRequirements())
                        .setDeviceTypeRequested(null);
            }
        }

        File configFile = FileUtil.createTempFile(config.getName(), ".xml");
        File globalConfig = null;
        config.dumpXml(new PrintWriter(configFile));
        try {
            try (InputStreamSource source = new FileInputStreamSource(configFile)) {
                listener.testLog("remote-configuration", LogDataType.XML, source);
            }
            CLog.d("Pushing Tradefed XML configuration to remote.");
            boolean resultPush =
                    RemoteFileUtil.pushFileToRemote(
                            info,
                            options,
                            null,
                            runUtil,
                            PUSH_TF_TIMEOUT,
                            mRemoteTradefedDir,
                            configFile);
            if (!resultPush) {
                CLog.e("Failed to push Tradefed Configuration.");
                listener.invocationFailed(
                        new RuntimeException("Failed to push Tradefed Configuration."));
                return;
            }

            String[] whitelistConfigs =
                    new String[] {
                        GlobalConfiguration.SCHEDULER_TYPE_NAME,
                        GlobalConfiguration.HOST_OPTIONS_TYPE_NAME,
                        "android-build"
                    };
            try {
                globalConfig =
                        GlobalConfiguration.getInstance()
                                .cloneConfigWithFilter(new HashSet<>(), whitelistConfigs);
            } catch (IOException e) {
                listener.invocationFailed(e);
                return;
            }
            try (InputStreamSource source = new FileInputStreamSource(globalConfig)) {
                listener.testLog("global-remote-configuration", LogDataType.XML, source);
            }
            boolean resultPushGlobal =
                    RemoteFileUtil.pushFileToRemote(
                            info,
                            options,
                            null,
                            runUtil,
                            PUSH_TF_TIMEOUT,
                            mRemoteTradefedDir,
                            globalConfig);
            if (!resultPushGlobal) {
                CLog.e("Failed to push Tradefed Global Configuration.");
                listener.invocationFailed(
                        new RuntimeException("Failed to push Tradefed Global Configuration."));
                return;
            }

            resetAdb(info, options, runUtil);
            runRemote(listener, configFile, info, options, runUtil, config, globalConfig);
        } finally {
            FileUtil.recursiveDelete(configFile);
            FileUtil.recursiveDelete(globalConfig);
        }
    }

    @Override
    public void doSetup(
            IInvocationContext context, IConfiguration config, ITestInvocationListener listener)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // Skip
    }

    @Override
    public void doTeardown(IInvocationContext context, IConfiguration config, Throwable exception)
            throws Throwable {
        // Only run device post invocation teardown
        super.runDevicePostInvocationTearDown(context, config);
    }

    @Override
    public void doCleanUp(IInvocationContext context, IConfiguration config, Throwable exception) {
        // Skip
    }

    private void runRemote(
            ITestInvocationListener currentInvocationListener,
            File configFile,
            GceAvdInfo info,
            TestDeviceOptions options,
            IRunUtil runUtil,
            IConfiguration config,
            File globalConfig)
            throws InvalidProtocolBufferException, IOException {
        List<String> remoteTfCommand = new ArrayList<>();
        remoteTfCommand.add("pushd");
        remoteTfCommand.add(mRemoteTradefedDir + ";");

        remoteTfCommand.add("TF_GLOBAL_CONFIG=" + globalConfig.getName());
        remoteTfCommand.add("nohup");
        remoteTfCommand.add("./tradefed.sh");
        remoteTfCommand.add("run");
        remoteTfCommand.add("commandAndExit");
        remoteTfCommand.add(mRemoteTradefedDir + configFile.getName());
        if (config.getCommandOptions().shouldUseRemoteSandboxMode()) {
            remoteTfCommand.add("--" + CommandOptions.USE_SANDBOX);
        }
        remoteTfCommand.add("> " + STDOUT_FILE);
        remoteTfCommand.add("2> " + STDERR_FILE);
        remoteTfCommand.add("&");

        // Kick off the actual remote run
        CommandResult resultRemoteExecution =
                GceManager.remoteSshCommandExecution(
                        info, options, runUtil, 0L, remoteTfCommand.toArray(new String[0]));
        if (!CommandStatus.SUCCESS.equals(resultRemoteExecution.getStatus())) {
            CLog.e("Error running the remote command: %s", resultRemoteExecution.getStdout());
            currentInvocationListener.invocationFailed(
                    new RuntimeException(resultRemoteExecution.getStderr()));
            return;
        }

        // Monitor the remote invocation to ensure it's completing
        long maxTimeout = config.getCommandOptions().getInvocationTimeout();
        Long endTime = null;
        if (maxTimeout > 0L) {
            endTime = System.currentTimeMillis() + maxTimeout;
        }

        boolean stillRunning = true;
        while (stillRunning) {
            CommandResult psRes =
                    GceManager.remoteSshCommandExecution(
                            info,
                            options,
                            runUtil,
                            120000L,
                            "ps",
                            "-ef",
                            "| grep",
                            Console.class.getCanonicalName());
            CLog.d("ps -ef: stdout: %s\nstderr:\n", psRes.getStdout(), psRes.getStderr());
            stillRunning = psRes.getStdout().contains(configFile.getName());
            CLog.d("still running: %s", stillRunning);
            if (endTime != null && System.currentTimeMillis() > endTime) {
                currentInvocationListener.invocationFailed(
                        new RuntimeException(
                                String.format(
                                        "Remote invocation timeout after %s",
                                        TimeUtil.formatElapsedTime(maxTimeout))));
                break;
            }
            RunUtil.getDefault().sleep(15000L);
        }
        File resultFile = null;
        if (!stillRunning) {
            resultFile =
                    RemoteFileUtil.fetchRemoteFile(
                            info, options, runUtil, PULL_RESULT_TIMEOUT, mRemoteFinalResult);
            if (resultFile == null) {
                currentInvocationListener.invocationFailed(
                        new RuntimeException(
                                String.format(
                                        "Could not find remote result file at %s",
                                        mRemoteFinalResult)));
            } else {
                CLog.d("Fetched remote result file!");
            }
        }

        // Fetch the logs
        File stdoutFile =
                RemoteFileUtil.fetchRemoteFile(
                        info,
                        options,
                        runUtil,
                        PULL_RESULT_TIMEOUT,
                        mRemoteTradefedDir + STDOUT_FILE);
        if (stdoutFile != null) {
            try (InputStreamSource source = new FileInputStreamSource(stdoutFile, true)) {
                currentInvocationListener.testLog("stdout", LogDataType.TEXT, source);
            }
        }

        // TODO: extract potential exception from stderr
        File stderrFile =
                RemoteFileUtil.fetchRemoteFile(
                        info,
                        options,
                        runUtil,
                        PULL_RESULT_TIMEOUT,
                        mRemoteTradefedDir + STDERR_FILE);
        if (stderrFile != null) {
            try (InputStreamSource source = new FileInputStreamSource(stderrFile, true)) {
                currentInvocationListener.testLog("stderr", LogDataType.TEXT, source);
            }
        }

        if (resultFile != null) {
            // Report result to listener.
            ProtoResultParser parser = new ProtoResultParser(currentInvocationListener, false);
            parser.processFinalizedProto(TestRecordProtoUtil.readFromFile(resultFile));
        }
    }

    /** Returns the main remote working directory. */
    private String getRemoteMainDir(TestDeviceOptions options) {
        return REMOTE_USER_DIR.replace("{$USER}", options.getInstanceUser());
    }

    /**
     * Sometimes remote adb version is a bit weird and is not running properly the first time. Try
     * it out once to ensure it starts.
     */
    private void resetAdb(GceAvdInfo info, TestDeviceOptions options, IRunUtil runUtil) {
        CommandResult probAdb =
                GceManager.remoteSshCommandExecution(
                        info, options, runUtil, 120000L, "adb", "devices");
        CLog.d("remote adb prob: %s", probAdb.getStdout());
    }
}
