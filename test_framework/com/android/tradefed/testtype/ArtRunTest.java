/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.ExecutionFiles.FilesKey;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import difflib.DiffUtils;
import difflib.Patch;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A test runner to run ART run-tests. */
public class ArtRunTest implements IRemoteTest, IAbiReceiver, ITestFilterReceiver {

    private static final String RUNTEST_TAG = "ArtRunTest";

    private static final String DALVIKVM_CMD =
            "dalvikvm|#BITNESS#| -classpath |#CLASSPATH#| |#MAINCLASS#|";

    @Option(
            name = "test-timeout",
            description =
                    "The max time in ms for an art run-test to "
                            + "run. Test run will be aborted if any test takes longer.",
            isTimeVal = true)
    private long mMaxTestTimeMs = 1 * 60 * 1000;

    @Option(name = "run-test-name", description = "The name to use when reporting results.")
    private String mRunTestName;

    @Option(name = "classpath", description = "Holds the paths to search when loading tests.")
    private List<String> mClasspath = new ArrayList<>();

    private ITestDevice mDevice = null;
    private IAbi mAbi = null;
    private final Set<String> mIncludeFilters = new LinkedHashSet<>();
    private final Set<String> mExcludeFilters = new LinkedHashSet<>();

    /** {@inheritDoc} */
    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /** {@inheritDoc} */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }

    /** {@inheritDoc} */
    @Override
    public void clearIncludeFilters() {
        mIncludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void clearExcludeFilters() {
        mExcludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        mDevice = testInfo.getDevice();
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set.");
        }
        if (mAbi == null) {
            throw new IllegalArgumentException("ABI has not been set.");
        }
        if (mRunTestName == null) {
            throw new IllegalArgumentException("Run-test name has not been set.");
        }
        if (mClasspath.isEmpty()) {
            throw new IllegalArgumentException("Classpath is empty.");
        }

        runArtTest(testInfo, listener);
    }

    /**
     * Run a single ART run-test (on device).
     *
     * @param listener {@link ITestInvocationListener} listener for test
     * @throws DeviceNotAvailableException If there was a problem communicating with the device.
     */
    void runArtTest(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        String abi = mAbi.getName();
        String runName = String.format("%s_%s", RUNTEST_TAG, abi);
        TestDescription testId = new TestDescription(runName, mRunTestName);
        if (shouldSkipCurrentTest(testId)) {
            return;
        }

        String deviceSerialNumber = mDevice.getSerialNumber();
        CLog.i("Running ArtRunTest %s on %s", mRunTestName, deviceSerialNumber);

        String testCmd = DALVIKVM_CMD;
        testCmd = testCmd.replace("|#BITNESS#|", AbiUtils.getBitness(abi));
        testCmd = testCmd.replace("|#CLASSPATH#|", ArrayUtil.join(File.pathSeparator, mClasspath));
        // TODO: Turn this into an an option of the `ArtRunTest` class?
        testCmd = testCmd.replace("|#MAINCLASS#|", "Main");

        CLog.d("About to run run-test command: %s", testCmd);
        // Note: We only run one test at the moment.
        int testCount = 1;
        listener.testRunStarted(runName, testCount);
        listener.testStarted(testId);

        try {
            // Execute the test on device.
            CommandResult testResult =
                    mDevice.executeShellV2Command(
                            testCmd, mMaxTestTimeMs, TimeUnit.MILLISECONDS, /* retryAttempts */ 0);
            if (testResult.getStatus() != CommandStatus.SUCCESS) {
                String message =
                        String.format(
                                "Test command execution failed with status %s: %s",
                                testResult.getStatus(), testResult);
                CLog.e(message);
                listener.testFailed(testId, message);
                return;
            }
            String actualStdoutText = testResult.getStdout();
            CLog.v("%s on %s returned stdout: %s", testCmd, deviceSerialNumber, actualStdoutText);
            String actualStderrText = testResult.getStderr();
            CLog.v("%s on %s returned stderr: %s", testCmd, deviceSerialNumber, actualStderrText);

            // TODO: The "check" step should be configurable, as is the case in current ART
            // `run-test` scripts).

            // Check the test's standard output.
            if (actualStdoutText == null) {
                listener.testFailed(testId, "No standard output received to compare to.");
                return;
            }
            try {
                String expectedStdoutFileName =
                        String.format("%s-expected-stdout.txt", mRunTestName);
                File expectedStdoutFile =
                        testInfo.getDependencyFile(expectedStdoutFileName, /* targetFirst */ true);
                CLog.i(
                        "Found expected standard output for run-test %s: %s",
                        mRunTestName, expectedStdoutFile);
                String expectedStdoutText = FileUtil.readStringFromFile(expectedStdoutFile);

                if (!actualStdoutText.equals(expectedStdoutText)) {
                    // Produce a unified diff output for the error message.
                    String diff =
                            computeDiff(
                                    expectedStdoutText,
                                    actualStdoutText,
                                    "expected-stdout.txt",
                                    "stdout");
                    String errorMessage =
                            "The test's standard output does not match the expected standard "
                                    + "output:\n"
                                    + diff;
                    CLog.i("%s FAILED: %s", mRunTestName, errorMessage);
                    listener.testFailed(testId, errorMessage);
                    return;
                }
            } catch (IOException ioe) {
                CLog.e(
                        "I/O error while accessing expected standard output file for test %s: %s",
                        mRunTestName, ioe);
                listener.testFailed(
                        testId, "I/O error while accessing expected standard output file.");
                return;
            }

            // Check the test's standard error.
            if (actualStderrText == null) {
                listener.testFailed(testId, "No standard error received to compare to.");
                return;
            }
            try {
                String expectedStderrFileName =
                        String.format("%s-expected-stderr.txt", mRunTestName);
                File expectedStderrFile =
                        testInfo.getDependencyFile(expectedStderrFileName, /* targetFirst */ true);
                CLog.i(
                        "Found expected standard error for run-test %s: %s",
                        mRunTestName, expectedStderrFile);
                String expectedStderrText = FileUtil.readStringFromFile(expectedStderrFile);

                if (!actualStderrText.equals(expectedStderrText)) {
                    // Produce a unified diff output for the error message.
                    String diff =
                            computeDiff(
                                    expectedStderrText,
                                    actualStderrText,
                                    "expected-stderr.txt",
                                    "stderr");
                    String errorMessage =
                            "The test's standard error does not match the expected standard "
                                    + "error:\n"
                                    + diff;
                    CLog.i("%s FAILED: %s", mRunTestName, errorMessage);
                    listener.testFailed(testId, errorMessage);
                    return;
                }
            } catch (IOException ioe) {
                CLog.e(
                        "I/O error while accessing expected standard error file for test %s: %s",
                        mRunTestName, ioe);
                listener.testFailed(
                        testId, "I/O error while accessing expected standard error file.");
                return;
            }

            if (mRunTestName.contains("-checker-")) {
                // not particularly reliable way of constructing a temporary dir
                String tmpCheckerDir =
                        String.format("/data/local/tmp/%s", mRunTestName.replaceAll("/", "-"));
                String mkdirCmd = String.format("mkdir -p \"%s\"", tmpCheckerDir);
                CommandResult mkdirResult = mDevice.executeShellV2Command(mkdirCmd);
                if (mkdirResult.getStatus() != CommandStatus.SUCCESS) {
                    String message =
                            String.format(
                                    "Cannot create a directory on the device: %s",
                                    mkdirResult.getStderr());
                    CLog.e(message);
                    listener.testFailed(testId, message);
                    return;
                }

                String cfgPath = tmpCheckerDir + "/graph.cfg";
                String oatPath = tmpCheckerDir + "/output.oat";
                String dex2oatCmd =
                        String.format(
                                "dex2oat --dex-file=%s --oat-file=%s --dump-cfg=%s -j1",
                                mClasspath.get(0), oatPath, cfgPath);
                CommandResult dex2oatResult = mDevice.executeShellV2Command(dex2oatCmd);
                if (dex2oatResult.getStatus() != CommandStatus.SUCCESS) {
                    String message =
                            String.format(
                                    "Error while running dex2oat: %s", dex2oatResult.getStderr());
                    CLog.e(message);
                    listener.testFailed(testId, message);
                    return;
                }

                File runTestDir;
                try {
                    runTestDir =
                            Files.createTempDirectory(
                                    testInfo.dependenciesFolder().toPath(), mRunTestName)
                                    .toFile();
                } catch (IOException e) {
                    CLog.e(e);
                    listener.testFailed(testId, "I/O error while creating test dir.");
                    return;
                }

                File localCfgPath = new File(runTestDir, "graph.cfg");
                if (localCfgPath.isFile()) {
                    localCfgPath.delete();
                }

                if (!mDevice.pullFile(cfgPath, localCfgPath)) {
                    listener.testFailed(testId, "Cannot pull cfg file from the device");
                    return;
                }

                File tempJar = new File(runTestDir, "temp.jar");
                if (!mDevice.pullFile(mClasspath.get(0), tempJar)) {
                    listener.testFailed(testId, "Cannot pull jar file from the device");
                    return;
                }

                try (ZipFile archive = new ZipFile(tempJar)) {
                    File srcFile = new File(runTestDir, "src");
                    if (srcFile.exists()) {
                        Files.walk(srcFile.toPath())
                                .map(Path::toFile)
                                .sorted(Comparator.reverseOrder())
                                .forEach(File::delete);
                    }

                    List<? extends ZipEntry> entries = archive.stream()
                            .sorted(Comparator.comparing(ZipEntry::getName))
                            .collect(Collectors.toList());

                    for (ZipEntry entry : entries) {
                        if (entry.getName().startsWith("src")) {
                            Path entryDest = runTestDir.toPath().resolve(entry.getName());
                            if (entry.isDirectory()) {
                                Files.createDirectory(entryDest);
                            } else {
                                Files.copy(archive.getInputStream(entry), entryDest);
                            }
                        }
                    }

                    // TODO(b/162408889): Clean up files on device (i.e. remove `tmpCheckerDir`)
                    // after running the Checker test.
                } catch (IOException e) {
                    listener.testFailed(testId, "Error unpacking test jar");
                    CLog.e("Jar unpacking failed with exception %s", e);
                    CLog.e(e);
                    return;
                }

                String checkerArch = AbiUtils.getArchForAbi(abi).toUpperCase();

                // Checker path for testsuites
                File checkerBinary = new File(
                        testInfo.executionFiles().get(FilesKey.TESTS_DIRECTORY),
                        "art-run-test-checker");

                if (!checkerBinary.isFile()) {
                    // Checker path for single atest runs
                    checkerBinary = new File(
                            testInfo.executionFiles().get(FilesKey.HOST_TESTS_DIRECTORY),
                            "art-run-test-checker/art-run-test-checker");
                    if (!checkerBinary.isFile()) {
                        listener.testFailed(testId, "Checker binary not found");
                        return;
                    }
                }

                ProcessBuilder processBuilder =
                        new ProcessBuilder(
                                checkerBinary.getAbsolutePath(),
                                "--no-print-cfg",
                                "-q",
                                "--arch=" + checkerArch,
                                localCfgPath.getAbsolutePath(),
                                runTestDir.getAbsolutePath());

                try {
                    Process process = processBuilder.start();
                    if (process.waitFor() != 0) {
                        String checkerOutput = new BufferedReader(
                                new InputStreamReader(process.getErrorStream())).lines().collect(
                                Collectors.joining("\n"));
                        listener.testFailed(testId, "Checker failed\n" + checkerOutput);
                        listener.testLog("graph.cfg", LogDataType.CFG,
                                new FileInputStreamSource(localCfgPath));
                    }
                } catch (IOException | InterruptedException e) {
                    listener.testFailed(testId, "I/O error while starting Checker process");
                }
            }

            // Check the test's exit code.
            if (testResult.getExitCode() != 0) {
                listener.testFailed(
                        testId,
                        String.format("Test exited with code %s", testResult.getExitCode()));
                return;
            }
        } finally {
            HashMap<String, Metric> emptyTestMetrics = new HashMap<>();
            listener.testEnded(testId, emptyTestMetrics);
            HashMap<String, Metric> emptyTestRunMetrics = new HashMap<>();
            // TODO: Pass an actual value as `elapsedTimeMillis` argument.
            listener.testRunEnded(/* elapsedTimeMillis*/ 0, emptyTestRunMetrics);
        }
    }

    /**
     * Check if current test should be skipped.
     *
     * @param description The test in progress.
     * @return true if the test should be skipped.
     */
    private boolean shouldSkipCurrentTest(TestDescription description) {
        // Force to skip any test not listed in include filters, or listed in exclude filters.
        // exclude filters have highest priority.
        String testName = description.getTestName();
        String descString = description.toString();
        if (mExcludeFilters.contains(testName) || mExcludeFilters.contains(descString)) {
            return true;
        }
        if (!mIncludeFilters.isEmpty()) {
            return !mIncludeFilters.contains(testName) && !mIncludeFilters.contains(descString);
        }
        return false;
    }

    /**
     * Compute the difference between expected and actual outputs as a unified diff.
     *
     * @param expected The expected output
     * @param actual The actual output
     * @param expectedFileName The name of the expected output file name (used in diff header)
     * @param actualFileName The name of the actual output file name (used in diff header)
     * @return The unified diff between the expected and actual outputs
     */
    private String computeDiff(
            String expected, String actual, String expectedFileName, String actualFileName) {
        List<String> expectedLines = Arrays.asList(expected.split("\\r?\\n"));
        List<String> actualLines = Arrays.asList(actual.split("\\r?\\n"));
        Patch<String> diff = DiffUtils.diff(expectedLines, actualLines);
        List<String> unifiedDiff =
                DiffUtils.generateUnifiedDiff(
                        expectedFileName, actualFileName, expectedLines, diff, 3);
        StringBuilder diffOutput = new StringBuilder();
        for (String delta : unifiedDiff) {
            diffOutput.append(delta).append('\n');
        }
        return diffOutput.toString();
    }
}
