/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@link IRemoteTest} for running unit or functional tests against a separate TF installation.
 * <p/>
 * Launches an external java process to run the tests. Used for running the TF unit or
 * functional tests continuously.
 */
public class TfTestLauncher extends SubprocessTfLauncher {

    private static final long COVERAGE_REPORT_TIMEOUT_MS = 2 * 60 * 1000;

    @Option(name = "jacoco-code-coverage", description = "Enable jacoco code coverage on the java "
            + "sub process. Run will be slightly slower because of the overhead.")
    private boolean mEnableCoverage = false;

    @Option(name = "ant-config-res", description = "The name of the ant resource configuration to "
            + "transform the results in readable format.")
    private String mAntConfigResource = "/jacoco/ant-tf-coverage.xml";

    @Option(name = "sub-branch", description = "The branch to be provided to the sub invocation, "
            + "if null, the branch in build info will be used.")
    private String mSubBranch = null;

    @Option(name = "sub-build-flavor", description = "The build flavor to be provided to the "
            + "sub invocation, if null, the build flavor in build info will be used.")
    private String mSubBuildFlavor = null;

    @Option(name = "sub-build-id", description = "The build id that the sub invocation will try "
            + "to use in case where it needs its own device.")
    private String mSubBuildId = null;

    @Option(name = "use-virtual-device", description =
            "Flag if the subprocess is going to need to instantiate a virtual device to run.")
    private boolean mUseVirtualDevice = false;

    @Option(name = "sub-apk-path", description = "The name of all the Apks that needs to be "
            + "installed by the subprocess invocation. Apk need to be inside the downloaded zip. "
            + "Can be repeated.")
    private List<String> mSubApkPath = new ArrayList<String>();

    // The regex pattern of temp files to be found in the temporary dir of the subprocess.
    // Any file not matching the patterns, or multiple files in the temporary dir match the same
    // pattern, is considered as test failure.
    private static final String[] EXPECTED_TMP_FILE_PATTERNS = {
        "inv_.*", "tradefed_global_log_.*", "lc_cache", "stage-android-build-api",
    };

    // A destination file where the report will be put.
    private File mDestCoverageFile = null;
    // A {@link File} pointing to the jacoco args jar file extracted from the resources
    private File mAgent = null;
    // we track the elapsed time of the invocation to report it.
    private long mStartTime = 0l;

    /** {@inheritDoc} */
    @Override
    protected void addJavaArguments(List<String> args) {
        super.addJavaArguments(args);
        if (mEnableCoverage) {
            try {
                mDestCoverageFile = FileUtil.createTempFile("coverage", ".exec");
                mAgent = extractJacocoAgent();
                addCoverageArgs(mAgent, args, mDestCoverageFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void preRun() {
        super.preRun();

        if (!mUseVirtualDevice) {
            mCmdArgs.add("-n");
        } else {
            // if it needs a device we also enable more logs
            mCmdArgs.add("--log-level");
            mCmdArgs.add("VERBOSE");
            mCmdArgs.add("--log-level-display");
            mCmdArgs.add("VERBOSE");
        }
        mCmdArgs.add("--test-tag");
        mCmdArgs.add(mBuildInfo.getTestTag());
        mCmdArgs.add("--build-id");
        if (mSubBuildId != null) {
            mCmdArgs.add(mSubBuildId);
        } else {
            mCmdArgs.add(mBuildInfo.getBuildId());
        }
        mCmdArgs.add("--branch");
        if (mSubBranch != null) {
            mCmdArgs.add(mSubBranch);
        } else if (mBuildInfo.getBuildBranch() != null) {
            mCmdArgs.add(mBuildInfo.getBuildBranch());
        } else {
            throw new RuntimeException("Branch option is required for the sub invocation.");
        }
        mCmdArgs.add("--build-flavor");
        if (mSubBuildFlavor != null) {
            mCmdArgs.add(mSubBuildFlavor);
        } else if (mBuildInfo.getBuildFlavor() != null) {
            mCmdArgs.add(mBuildInfo.getBuildFlavor());
        } else {
            throw new RuntimeException("Build flavor option is required for the sub invocation.");
        }

        for (String apk : mSubApkPath) {
            mCmdArgs.add("--apk-path");
            String apkPath =
                    String.format(
                            "%s%s%s",
                            ((IFolderBuildInfo) mBuildInfo).getRootDir().getAbsolutePath(),
                            File.separator,
                            apk);
            mCmdArgs.add(apkPath);
        }

        mStartTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void postRun(ITestInvocationListener listener, boolean exception) {
        super.postRun(listener, exception);
        reportMetrics(System.currentTimeMillis() - mStartTime, listener);
        FileUtil.deleteFile(mAgent);

        // Evaluate coverage from the subprocess
        if (mEnableCoverage) {
            InputStreamSource coverage = null;
            File xmlResult = null;
            try {
                xmlResult = processExecData(mDestCoverageFile, mRootDir);
                coverage = new FileInputStreamSource(xmlResult);
                listener.testLog("coverage_xml", LogDataType.JACOCO_XML, coverage);
            } catch (IOException e) {
                if (exception) {
                    // If exception was thrown above, we only log this one since it's most
                    // likely related to it.
                    CLog.e(e);
                } else {
                    throw new RuntimeException(e);
                }
            } finally {
                FileUtil.deleteFile(mDestCoverageFile);
                StreamUtil.cancel(coverage);
                FileUtil.deleteFile(xmlResult);
            }
        }

        if (mTmpDir != null) {
            testTmpDirClean(mTmpDir, listener);
        }
        cleanTmpFile();
    }

    @VisibleForTesting
    void cleanTmpFile() {
        FileUtil.deleteFile(mDestCoverageFile);
        FileUtil.deleteFile(mAgent);
    }

    /**
     * Helper to add arguments required for code coverage collection.
     *
     * @param jacocoAgent the jacoco args file to run the coverage.
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
     * Returns a {@link File} pointing to the jacoco args jar file extracted from the resources.
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
     * Helper to process the execution data into user readable format (xml) that can easily be
     * parsed.
     *
     * @param executionData output files of the java args jacoco.
     * @param rootDir base directory of downloaded TF
     * @return a {@link File} pointing to the human readable xml result file.
     */
    private File processExecData(File executionData, String rootDir) throws IOException {
        File xmlReport = FileUtil.createTempFile("coverage_xml", ".xml");
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
                    "-Ddest.file=" + xmlReport.getAbsolutePath()};
            CommandResult result = RunUtil.getDefault().runTimedCmd(COVERAGE_REPORT_TIMEOUT_MS,
                    cmd);
            CLog.d(result.getStdout());
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                throw new IOException(result.getStderr());
            }
            return xmlReport;
        } finally {
            FileUtil.deleteFile(antConfig);
            FileUtil.deleteFile(jacocoAnt);
        }
    }

    /**
     * Report an elapsed-time metric to keep track of it.
     *
     * @param elapsedTime time it took the subprocess to run.
     * @param listener the {@link ITestInvocationListener} where to report the metric.
     */
    private void reportMetrics(long elapsedTime, ITestInvocationListener listener) {
        listener.testRunStarted("elapsed-time", 0);
        Map<String, String> runMetrics = new HashMap<>();
        runMetrics.put("elapsed-time", Long.toString(elapsedTime));
        listener.testRunEnded(elapsedTime, runMetrics);
    }

    /**
     * Extra test to ensure no files are created by the unit tests in the subprocess and not
     * cleaned.
     *
     * @param tmpDir the temporary dir of the subprocess.
     * @param listener the {@link ITestInvocationListener} where to report the test.
     */
    @VisibleForTesting
    protected void testTmpDirClean(File tmpDir, ITestInvocationListener listener) {
        listener.testRunStarted("temporaryFiles", 1);
        TestIdentifier tid = new TestIdentifier("temporary-files", "testIfClean");
        listener.testStarted(tid);
        String[] listFiles = tmpDir.list();
        List<String> unmatchedFiles = new ArrayList<String>();
        List<String> patterns = new ArrayList<String>(Arrays.asList(EXPECTED_TMP_FILE_PATTERNS));
        for (String file : Arrays.asList(listFiles)) {
            Boolean matchFound = false;
            for (String pattern : patterns) {
                if (Pattern.matches(pattern, file)) {
                    patterns.remove(pattern);
                    matchFound = true;
                    break;
                }
            }
            if (!matchFound) {
                unmatchedFiles.add(file);
            }
        }
        if (unmatchedFiles.size() > 0) {
            String trace = String.format("Found '%d' unexpected temporary files: %s.\nOnly "
                    + "expected files are: %s. And each should appears only once.",
                    unmatchedFiles.size(), unmatchedFiles,
                    Arrays.asList(EXPECTED_TMP_FILE_PATTERNS));
            listener.testFailed(tid, trace);
        }
        listener.testEnded(tid, Collections.emptyMap());
        listener.testRunEnded(0, Collections.emptyMap());
    }
}
