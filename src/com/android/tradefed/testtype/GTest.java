/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A Test that runs a native test package on given device.
 */
@OptionClass(alias = "gtest")
public class GTest implements IDeviceTest, IRemoteTest, ITestFilterReceiver, IRuntimeHintProvider,
    ITestCollector {

    private static final String LOG_TAG = "GTest";
    static final String DEFAULT_NATIVETEST_PATH = "/data/nativetest";

    private ITestDevice mDevice = null;
    private boolean mRunDisabledTests = false;

    @Option(name = "native-test-device-path",
            description="The path on the device where native tests are located.")
    private String mNativeTestDevicePath = DEFAULT_NATIVETEST_PATH;

    @Option(name = "file-exclusion-filter-regex",
            description = "Regex to exclude certain files from executing. Can be repeated")
    private List<String> mFileExclusionFilterRegex = new ArrayList<>();

    @Option(name = "module-name",
            description="The name of the native test module to run.")
    private String mTestModule = null;

    @Option(name = "positive-testname-filter",
            description="The GTest-based positive filter of the test name to run.")
    private String mTestNamePositiveFilter = null;
    @Option(name = "negative-testname-filter",
            description="The GTest-based negative filter of the test name to run.")
    private String mTestNameNegativeFilter = null;

    @Option(name = "include-filter",
            description="The GTest-based positive filter of the test names to run.")
    private Set<String> mIncludeFilters = new HashSet<>();
    @Option(name = "exclude-filter",
            description="The GTest-based negative filter of the test names to run.")
    private Set<String> mExcludeFilters = new HashSet<>();

    @Option(name = "native-test-timeout", description =
            "The max time in ms for a gtest to run. " +
            "Test run will be aborted if any test takes longer.")
    private int mMaxTestTimeMs = 1 * 60 * 1000;

    @Option(name = "send-coverage",
            description = "Send coverage target info to test listeners.")
    private boolean mSendCoverage = true;

    @Option(name ="prepend-filename",
            description = "Prepend filename as part of the classname for the tests.")
    private boolean mPrependFileName = false;

    @Option(name = "before-test-cmd",
            description = "adb shell command(s) to run before GTest.")
    private List<String> mBeforeTestCmd = new ArrayList<>();

    @Option(name = "after-test-cmd",
            description = "adb shell command(s) to run after GTest.")
    private List<String> mAfterTestCmd = new ArrayList<>();

    @Option(name = "ld-library-path",
            description = "LD_LIBRARY_PATH value to include in the GTest execution command.")
    private String mLdLibraryPath = null;

    @Option(name = "native-test-flag", description =
            "Additional flag values to pass to the native test's shell command. " +
            "Flags should be complete, including any necessary dashes: \"--flag=value\"")
    private List<String> mGTestFlags = new ArrayList<>();

    @Option(name = "runtime-hint",
            isTimeVal=true,
            description="The hint about the test's runtime.")
    private long mRuntimeHint = 60000;// 1 minute

    @Option(name = "xml-output",
            description="Use gtest xml output for test results, if test binaries crash, no output"
                    + "will be available.")
    private boolean mEnableXmlOutput = false;

    @Option(name = "collect-tests-only",
            description = "Only invoke the test binary to collect list of applicable test cases. "
                    + "All test run callbacks will be triggered, but test execution will "
                    + "not be actually carried out.")
    private boolean mCollectTestsOnly = false;

    /** coverage target value. Just report all gtests as 'native' for now */
    private static final String COVERAGE_TARGET = "Native";

    // GTest flags...
    private static final String GTEST_FLAG_PRINT_TIME = "--gtest_print_time";
    private static final String GTEST_FLAG_FILTER = "--gtest_filter";
    private static final String GTEST_FLAG_RUN_DISABLED_TESTS = "--gtest_also_run_disabled_tests";
    private static final String GTEST_FLAG_LIST_TESTS = "--gtest_list_tests";
    private static final String GTEST_XML_OUTPUT = "--gtest_output=xml:%s";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    public void setEnableXmlOutput(boolean b) {
        mEnableXmlOutput = b;
    }

    /**
     * Set the Android native test module to run.
     *
     * @param moduleName The name of the native test module to run
     */
    public void setModuleName(String moduleName) {
        mTestModule = moduleName;
    }

    /**
     * Get the Android native test module to run.
     *
     * @return the name of the native test module to run, or null if not set
     */
    public String getModuleName() {
        return mTestModule;
    }

    /**
     * Set whether GTest should run disabled tests.
     */
    public void setRunDisabled(boolean runDisabled) {
        mRunDisabledTests = runDisabled;
    }

    /**
     * Get whether GTest should run disabled tests.
     *
     * @return True if disabled tests should be run, false otherwise
     */
    public boolean getRunDisabledTests() {
        return mRunDisabledTests;
    }

    /**
     * Set the max time in ms for a gtest to run.
     * <p/>
     * Exposed for unit testing
     */
    void setMaxTestTimeMs(int timeout) {
        mMaxTestTimeMs = timeout;
    }

    /**
     * Adds an exclusion file filter regex.
     * <p/>
     * Exposed for unit testing
     *
     * @param regex to exclude file.
     */
    void addFileExclusionFilterRegex(String regex) {
        mFileExclusionFilterRegex.add(regex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRuntimeHint() {
        return mRuntimeHint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(List<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(List<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /**
     * Helper to get the adb gtest filter of test to run.
     *
     * Note that filters filter on the function name only (eg: Google Test "Test"); all Google Test
     * "Test Cases" will be considered.
     *
     * @return the full filter flag to pass to the Gtest, or an empty string if none have been
     * specified
     */
    private String getGTestFilters() {
        StringBuilder filter = new StringBuilder();
        if (mTestNamePositiveFilter != null) {
            mIncludeFilters.add(mTestNamePositiveFilter);
        }
        if (mTestNameNegativeFilter != null) {
            mExcludeFilters.add(mTestNameNegativeFilter);
        }
        if (!mIncludeFilters.isEmpty() || !mExcludeFilters.isEmpty()) {
            filter.append(GTEST_FLAG_FILTER);
            filter.append("=");
            if (!mIncludeFilters.isEmpty()) {
              filter.append(ArrayUtil.join(":", mIncludeFilters));
            }
            if (!mExcludeFilters.isEmpty()) {
              filter.append("-");
              filter.append(ArrayUtil.join(":", mExcludeFilters));
          }
        }
        return filter.toString();
    }

    /**
     * Helper to get all the GTest flags to pass into the adb shell command.
     *
     * @return the {@link String} of all the GTest flags that should be passed to the GTest
     */
    private String getAllGTestFlags() {
        String flags = String.format("%s %s", GTEST_FLAG_PRINT_TIME, getGTestFilters());

        if (mRunDisabledTests) {
            flags = String.format("%s %s", flags, GTEST_FLAG_RUN_DISABLED_TESTS);
        }

        if (mCollectTestsOnly) {
            flags = String.format("%s %s", flags, GTEST_FLAG_LIST_TESTS);
        }

        for (String gTestFlag : mGTestFlags) {
            flags = String.format("%s %s", flags, gTestFlag);
        }
        return flags;
    }

    /**
     * Gets the path where native tests live on the device.
     *
     * @return The path on the device where the native tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(mNativeTestDevicePath);
        if (mTestModule != null) {
            testPath.append(FileListingService.FILE_SEPARATOR);
            testPath.append(mTestModule);
        }
        return testPath.toString();
    }

    /**
     * Executes all native tests in a folder as well as in all subfolders recursively.
     * <p/>
     * Exposed for unit testing.
     *
     * @param root The root folder to begin searching for native tests
     * @param testDevice The device to run tests on
     * @param listener the {@link ITestRunListener}
     * @throws DeviceNotAvailableException
     */
    void doRunAllTestsInSubdirectory(String root, ITestDevice testDevice,
            ITestRunListener listener) throws DeviceNotAvailableException {
        if (isDirectory(testDevice, root)) {
            // recursively run tests in all subdirectories
            for (String child : getChildren(testDevice, root)) {
                doRunAllTestsInSubdirectory(root + "/" + child, testDevice, listener);
            }
        } else {
            // assume every file is a valid gtest binary.
            IShellOutputReceiver resultParser = createResultParser(getFileName(root), listener);
            if (shouldSkipFile(root)) {
                return;
            }
            String flags = getAllGTestFlags();
            Log.i(LOG_TAG, String.format("Running gtest %s %s on %s", root, flags,
                    testDevice.getSerialNumber()));
            // force file to be executable
            testDevice.executeShellCommand(String.format("chmod 755 %s", root));
            if (mEnableXmlOutput) {
                runTestXml(testDevice, root, flags, listener);
            } else {
                runTest(testDevice, resultParser, root, flags);
            }
        }
    }

    String getFileName(String fullPath) {
        int pos = fullPath.lastIndexOf('/');
        if (pos == -1) {
            return fullPath;
        }
        String fileName = fullPath.substring(pos + 1);
        if (fileName.isEmpty()) {
            throw new IllegalArgumentException("input should not end with \"/\"");
        }
        return fileName;
    }

    private boolean isDirectory(ITestDevice device, String path)
            throws DeviceNotAvailableException {
        return device.executeShellCommand(String.format("ls -ld %s", path)).charAt(0) == 'd';
    }

    private String[] getChildren(ITestDevice device, String path)
            throws DeviceNotAvailableException {
        String lsOutput = device.executeShellCommand(String.format("ls -A1 %s", path));
        if (lsOutput.trim().isEmpty()) {
            return new String[0];
        }
        return lsOutput.split("\r?\n");
    }

    /**
     * Helper method to determine if we should skip the execution of a given file.
     * @param fullPath the full path of the file in question
     * @return true if we should skip the said file.
     */
    protected boolean shouldSkipFile(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return true;
        }
        if (mFileExclusionFilterRegex == null || mFileExclusionFilterRegex.isEmpty()) {
            return false;
        }
        for (String regex : mFileExclusionFilterRegex) {
            if (fullPath.matches(regex)) {
                Log.i(LOG_TAG, String.format("File %s matches exclusion file regex %s, skipping",
                        fullPath, regex));
                return true;
            }
        }
        return false;
    }

    /**
     * Run the given gtest binary
     *
     * @param testDevice the {@link ITestDevice}
     * @param resultParser the test run output parser
     * @param fullPath absolute file system path to gtest binary on device
     * @param flags gtest execution flags
     * @throws DeviceNotAvailableException
     */
    private void runTest(final ITestDevice testDevice, final IShellOutputReceiver resultParser,
            final String fullPath, final String flags) throws DeviceNotAvailableException {
        // TODO: add individual test timeout support, and rerun support
        try {
            for (String cmd : mBeforeTestCmd) {
                testDevice.executeShellCommand(cmd);
            }
            String cmd = getGTestCmdLine(fullPath, flags);
            testDevice.executeShellCommand(cmd, resultParser,
                    mMaxTestTimeMs /* maxTimeToShellOutputResponse */,
                    TimeUnit.MILLISECONDS,
                    0 /* retryAttempts */);
        } catch (DeviceNotAvailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } finally {
            // TODO: consider moving the flush of parser data on exceptions to TestDevice or
            // AdbHelper
            resultParser.flush();
            for (String cmd : mAfterTestCmd) {
                testDevice.executeShellCommand(cmd);
            }
        }
    }

    /**
     * Run the given gtest binary and parse XML results
     * This methods typically requires the filter for .tff and .xml files, otherwise it will post
     * some unwanted results.
     *
     * @param testDevice the {@link ITestDevice}
     * @param fullPath absolute file system path to gtest binary on device
     * @param flags gtest execution flags
     * @param listener the {@link ITestRunListener}
     * @throws DeviceNotAvailableException
     */
    private void runTestXml(final ITestDevice testDevice, final String fullPath,
            final String flags, ITestRunListener listener) throws DeviceNotAvailableException {
        CollectingOutputReceiver outputCollector = new CollectingOutputReceiver();
        File tmpOutput = null;
        try {
            String testRunName = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            tmpOutput = FileUtil.createTempFile(testRunName, ".xml");
            String tmpResName = fullPath + "_res.xml";
            String extraFlag = String.format(GTEST_XML_OUTPUT, tmpResName);
            String fullFlagCmd =  String.format("%s %s", flags, extraFlag);

            // Run the tests with modified flags
            runTest(testDevice, outputCollector, fullPath, fullFlagCmd);
            // Pull the result file, may not exist if issue with the test.
            testDevice.pullFile(tmpResName, tmpOutput);
            // Clean the file on the device
            testDevice.executeShellCommand("rm " + tmpResName);
            GTestXmlResultParser parser = createXmlParser(testRunName, listener);
            // Attempt to parse the file, doesn't matter if the content is invalid.
            if (tmpOutput.exists()) {
                parser.parseResult(tmpOutput, outputCollector);
            }
        } catch (DeviceNotAvailableException | RuntimeException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            outputCollector.flush();
            for (String cmd : mAfterTestCmd) {
                testDevice.executeShellCommand(cmd);
            }
            if (tmpOutput != null && tmpOutput.exists()) {
                FileUtil.deleteFile(tmpOutput);
            }
        }
    }

    /**
     * Exposed for testing
     * @param testRunName
     * @param listener
     * @return a {@link GTestXmlResultParser}
     */
    GTestXmlResultParser createXmlParser(String testRunName, ITestRunListener listener) {
        return new GTestXmlResultParser(testRunName, listener);
    }

    /**
     * Helper method to build the gtest command to run.
     *
     * @param fullPath absolute file system path to gtest binary on device
     * @param flags gtest execution flags
     * @return the shell command line to run for the gtest
     */
    protected String getGTestCmdLine(String fullPath, String flags) {
        StringBuilder gTestCmdLine = new StringBuilder();
        if (mLdLibraryPath != null) {
            gTestCmdLine.append(String.format("LD_LIBRARY_PATH=%s ", mLdLibraryPath));
        }
        gTestCmdLine.append(String.format("%s %s", fullPath, flags));
        return gTestCmdLine.toString();
    }

    /**
     * Factory method for creating a {@link IShellOutputReceiver} that parses test output and
     * forwards results to the result listener.
     * <p/>
     * Exposed so unit tests can mock
     *
     * @param listener
     * @param runName
     * @return a {@link IShellOutputReceiver}
     */
    IShellOutputReceiver createResultParser(String runName, ITestRunListener listener) {
        IShellOutputReceiver receiver = null;
        if (mCollectTestsOnly) {
            GTestListTestParser resultParser = new GTestListTestParser(runName, listener);
            resultParser.setPrependFileName(mPrependFileName);
            receiver = resultParser;
        } else {
            GTestResultParser resultParser = new GTestResultParser(runName, listener);
            resultParser.setPrependFileName(mPrependFileName);
            // TODO: find a better solution for sending coverage info
            if (mSendCoverage) {
                resultParser.setCoverageTarget(COVERAGE_TARGET);
            }
            receiver = resultParser;
        }
        return receiver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // @TODO: add support for rerunning tests
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        String testPath = getTestPath();
        if (!mDevice.doesFileExist(testPath)) {
            Log.w(LOG_TAG, String.format("Could not find native test directory %s in %s!",
                    testPath, mDevice.getSerialNumber()));
            return;
        }
        doRunAllTestsInSubdirectory(testPath, mDevice, listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }
}
