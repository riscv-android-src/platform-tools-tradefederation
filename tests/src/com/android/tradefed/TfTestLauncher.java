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
package com.android.tradefed;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.EnvPriority;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.SubprocessTestResultsParser;

import junit.framework.Assert;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A {@link IRemoteTest} for running tests against a separate TF installation.
 * <p/>
 * Launches an external java process to run the tests. Used for running the TF unit or
 * functional tests continuously.
 */
public class TfTestLauncher implements IRemoteTest, IBuildReceiver {

    @Option(name = "max-run-time", description =
            "the maximum time in minutes to allow for a TF test run.", isTimeVal = true)
    private long mMaxTfRunTimeMin = 20;

    @Option(name = "remote-debug", description =
            "start the TF java process in remote debug mode.")
    private boolean mRemoteDebug = false;

    @Option(name = "jacoco-code-coverage", description = "Enable jacoco code coverage on the java "
            + "sub process. Run will be slightly slower because of the overhead.")
    private boolean mEnableCoverage = false;

    @Option(name = "ant-config-res", description = "The name of the ant resource configuration to "
            + "transform the results in readable format.")
    private String mAntConfigResource = "/jacoco/ant-tf-coverage.xml";

    private IBuildInfo mBuildInfo;

    @Option(name = "config-name", description = "the config that runs the TF tests")
    private String mConfigName;

    @Option(name = "sub-branch", description = "the branch to be provided to the sub invocation, "
            + "if null, the branch in build info will be used.")
    private String mSubBranch = null;

    @Option(name = "sub-build-flavor", description = "the build flavor to be provided to the "
            + "sub invocation, if null, the build flavor in build info will be used.")
    private String mSubBuildFlavor = null;

    @Option(name = "sub-build-id", description = "the build id that the sub invocation will try "
            + "to use in case where it needs its own device.")
    private String mSubBuildId = null;

    @Option(name = "use-virtual-device", description =
            "flag if the subprocess is going to need to instantiate a virtual device to run.")
    private boolean mNeedDevice = false;

    @Option(name = "sub-apk-path", description = "The name of all the Apks that needs to be "
            + "installed by the subprocess invocation. Apk need to be inside the downloaded zip. "
            + "Can be repeated.")
    private List<String> mSubApkPath = new ArrayList<String>();

    @Option(name = "sub-global-config", description = "The global config name to pass to the"
            + "sub process, can be local or from jar resources. Be careful of conflicts with "
            + "parent process.")
    private String mGlobalConfig = null;

    @Option(name = "use-event-streaming", description = "Use a socket to receive results as they"
            + "arrived instead of using a temporary file and parsing at the end.")
    private boolean mEventStreaming = true;

    private static final String TF_GLOBAL_CONFIG = "TF_GLOBAL_CONFIG";
    private static final long COVERAGE_REPORT_TIMEOUT = 2 * 60 * 1000;
    /** Timeout to wait for the events received from subprocess to finish being processed.*/
    private static final long EVENT_THREAD_JOIN_TIMEOUT_MS = 30 * 1000;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mBuildInfo);
        Assert.assertNotNull(mConfigName);
        IFolderBuildInfo tfBuild = (IFolderBuildInfo)mBuildInfo;
        String rootDir = tfBuild.getRootDir().getAbsolutePath();
        String jarClasspath = FileUtil.getPath(rootDir, "*");

        List<String> args = new ArrayList<String>();
        args.add("java");

        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("subprocess-" + tfBuild.getBuildId());
            args.add(String.format("-Djava.io.tmpdir=%s", tmpDir.getAbsolutePath()));
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }

        File destCoverageFile = null;
        File agent = null;
        if (mEnableCoverage) {
            try {
                destCoverageFile = FileUtil.createTempFile("coverage", ".exec");
                agent = extractJacocoAgent();
                addCoverageArgs(agent, args, destCoverageFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (mRemoteDebug) {
            args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=10088");
        }
        args.add("-cp");

        args.add(jarClasspath);
        args.add("com.android.tradefed.command.CommandRunner");
        args.add(mConfigName);
        if (!mNeedDevice) {
            args.add("-n");
        } else {
            // if it needs a device we also enable more logs
            args.add("--log-level");
            args.add("VERBOSE");
            args.add("--log-level-display");
            args.add("VERBOSE");
        }
        args.add("--test-tag");
        args.add(mBuildInfo.getTestTag());
        if (mSubBuildId != null) {
            args.add("--build-id");
            args.add(mSubBuildId);
        }
        args.add("--branch");
        if (mSubBranch != null) {
            args.add(mSubBranch);
        } else if (mBuildInfo.getBuildBranch() != null) {
            args.add(mBuildInfo.getBuildBranch());
        } else {
            throw new RuntimeException("Branch option is required for the sub invocation.");
        }
        args.add("--build-flavor");
        if (mSubBuildFlavor != null) {
            args.add(mSubBuildFlavor);
        } else if (mBuildInfo.getBuildFlavor() != null) {
            args.add(mBuildInfo.getBuildFlavor());
        } else {
            throw new RuntimeException("Build flavor option is required for the sub invocation.");
        }

        for (String apk : mSubApkPath) {
            args.add("--apk-path");
            String apkPath = String.format("%s%s%s",
                    ((IFolderBuildInfo)mBuildInfo).getRootDir().getAbsolutePath(),
                    File.separator, apk);
            args.add(apkPath);
        }

        File stdoutFile = null;
        File stderrFile = null;
        File eventFile = null;
        SubprocessTestResultsParser eventParser = null;
        FileOutputStream stdout = null;
        FileOutputStream stderr = null;

        IRunUtil runUtil = new RunUtil();
        // clear the TF_GLOBAL_CONFIG env, so another tradefed will not reuse the global config file
        runUtil.unsetEnvVariable(TF_GLOBAL_CONFIG);
        if (mGlobalConfig != null) {
            // We allow overriding this global config and then set it for the subprocess.
            runUtil.setEnvVariablePriority(EnvPriority.SET);
            runUtil.setEnvVariable(TF_GLOBAL_CONFIG, mGlobalConfig);
        }
        boolean exception = false;
        try {
            stdoutFile = FileUtil.createTempFile("stdout_subprocess_", ".log");
            stderrFile = FileUtil.createTempFile("stderr_subprocess_", ".log");
            stderr = new FileOutputStream(stderrFile);
            stdout = new FileOutputStream(stdoutFile);
            if (mEventStreaming) {
                eventParser = new SubprocessTestResultsParser(listener, true);
                args.add("--subprocess-report-port");
                args.add(Integer.toString(eventParser.getSocketServerPort()));
            } else {
                eventFile = FileUtil.createTempFile("event_subprocess_", ".log");
                eventParser = new SubprocessTestResultsParser(listener);
                args.add("--subprocess-report-file");
                args.add(eventFile.getAbsolutePath());
            }

            CommandResult result = runUtil.runTimedCmd(mMaxTfRunTimeMin * 60 * 1000, stdout, stderr,
                    args.toArray(new String[0]));
            // We possibly allow for a little more time if the thread is still processing events.
            if (!eventParser.joinReceiver(EVENT_THREAD_JOIN_TIMEOUT_MS)) {
                throw new RuntimeException(String.format("Event receiver thread did not complete:"
                        + "\n%s", FileUtil.readStringFromFile(stderrFile)));
            }
            if (result.getStatus().equals(CommandStatus.SUCCESS)) {
                CLog.d("Successfully ran TF tests for build %s", mBuildInfo.getBuildId());
            } else {
                CLog.w("Failed ran TF tests for build %s, status %s",
                        mBuildInfo.getBuildId(), result.getStatus());
                CLog.v("TF tests output:\nstdout:\n%s\nstderror:\n%s",
                        result.getStdout(), result.getStderr());
                exception = true;
                throw new RuntimeException(
                        String.format("%s Tests subprocess failed due to:\n %s\n", mConfigName,
                                FileUtil.readStringFromFile(stderrFile)));
            }
        } catch (IOException e) {
            exception = true;
            throw new RuntimeException(e);
        } finally {
            StreamUtil.close(stdout);
            StreamUtil.close(stderr);
            logAndCleanFile(stdoutFile, listener);
            logAndCleanFile(stderrFile, listener);
            if (eventFile != null) {
                eventParser.parseFile(eventFile);
                logAndCleanFile(eventFile, listener);
            }
            StreamUtil.close(eventParser);
            FileUtil.deleteFile(agent);

            if (tmpDir != null) {
                testTmpDirClean(tmpDir, listener);
            }

            // Evaluate coverage from the subprocess
            if (mEnableCoverage) {
                InputStreamSource coverage = null;
                File csvResult = null;
                try {
                    csvResult = processExecData(destCoverageFile, rootDir);
                    coverage = new FileInputStreamSource(csvResult);
                    listener.testLog("coverage_csv", LogDataType.TEXT, coverage);
                } catch (IOException e) {
                    if (exception) {
                        // If exception was thrown above, we only log this one since it's most
                        // likely related to it.
                        CLog.e(e);
                    } else {
                        throw new RuntimeException(e);
                    }
                } finally {
                    FileUtil.deleteFile(destCoverageFile);
                    StreamUtil.cancel(coverage);
                    FileUtil.deleteFile(csvResult);
                }
            }
        }
    }

    private void logAndCleanFile(File fileToExport, ITestInvocationListener listener) {
        if (fileToExport != null) {
            FileInputStreamSource stderrInputStream = new FileInputStreamSource(fileToExport);
            listener.testLog(fileToExport.getName(), LogDataType.TEXT, stderrInputStream);
            stderrInputStream.cancel();
            FileUtil.deleteFile(fileToExport);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Helper to add arguments required for code coverage collection.
     *
     * @param jacocoAgent the jacoco agent file to run the coverage.
     * @param args list of arguments that will be run in the subprocess.
     * @param destfile destination file where the report will be put.
     */
    private void addCoverageArgs(File jacocoAgent, List<String> args, File destfile) {
        String javaagent = String.format("-javaagent:%s=destfile=%s,"
                + "includes=com.android.tradefed*:com.google.android.tradefed*",
                jacocoAgent.getAbsolutePath(),
                destfile.getAbsolutePath());
        args.add(javaagent);
    }

    /**
     * Returns a {@link File} pointing to the jacoco agent jar file extracted from the resources.
     */
    private File extractJacocoAgent() throws IOException {
        String jacocoAgentRes = "/jacoco/jacocoagent.jar";
        InputStream jacocoAgentStream = getClass().getResourceAsStream(jacocoAgentRes);
        if (jacocoAgentStream == null) {
            throw new IOException("Could not find " + jacocoAgentRes);
        }
        File jacocoAgent = FileUtil.createTempFile("jacocoagent", ".jar");
        FileUtil.writeToFile(jacocoAgentStream, jacocoAgent);
        return jacocoAgent;
    }

    /**
     * Helper to process the execution data into user readable format (csv) that can easily be
     * parsed.
     *
     * @param executionData output files of the java agent jacoco.
     * @param rootDir base directory of downloaded TF
     * @return a {@link File} pointing to the human readable csv result file.
     */
    private File processExecData(File executionData, String rootDir) throws IOException {
        File csvReport = FileUtil.createTempFile("coverage_csv", ".csv");
        InputStream template = getClass().getResourceAsStream(mAntConfigResource);
        if (template == null) {
            throw new IOException("Could not find " + mAntConfigResource);
        }
        String jacocoAntRes = "/jacoco/jacocoant.jar";
        InputStream jacocoAntStream = getClass().getResourceAsStream(jacocoAntRes);
        if (jacocoAntStream == null) {
            throw new IOException("Could not find " + jacocoAntRes);
        }
        File antConfig = FileUtil.createTempFile("ant-merge_", ".xml");
        File jacocoAnt = FileUtil.createTempFile("jacocoant", ".jar");
        try {
            FileUtil.writeToFile(template, antConfig);
            FileUtil.writeToFile(jacocoAntStream, jacocoAnt);
            String[] cmd = {"ant", "-f", antConfig.getPath(),
                    "-Djacocoant.path=" + jacocoAnt.getAbsolutePath(),
                    "-Dexecution.files=" + executionData.getAbsolutePath(),
                    "-Droot.dir=" + rootDir,
                    "-Ddest.file=" + csvReport.getAbsolutePath()};
            CommandResult result = RunUtil.getDefault().runTimedCmd(COVERAGE_REPORT_TIMEOUT, cmd);
            CLog.d(result.getStdout());
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                throw new IOException(result.getStderr());
            }
            return csvReport;
        } finally {
            FileUtil.deleteFile(antConfig);
            FileUtil.deleteFile(jacocoAnt);
        }
    }

    /**
     * Extra test to ensure no files are created by the unit tests in the subprocess and not
     * cleaned.
     *
     * @param tmpDir the temporary dir of the subprocess.
     * @param listener the {@link ITestInvocationListener} where to report the test.
     */
    private void testTmpDirClean(File tmpDir, ITestInvocationListener listener) {
        TestIdentifier tid = new TestIdentifier("temporary-files", "testIfClean");
        listener.testStarted(tid);
        String[] listFiles = tmpDir.list();
        if (listFiles.length > 2) {
            String trace = String.format("Found '%d' temporary files: %s\n, only 2 are expected: "
                    + "inv_*, tradefed_global_log_*", listFiles.length,
                    Arrays.asList(listFiles));
            listener.testFailed(tid, trace);
        }
        listener.testEnded(tid, Collections.emptyMap());
        FileUtil.recursiveDelete(tmpDir);
    }
}
