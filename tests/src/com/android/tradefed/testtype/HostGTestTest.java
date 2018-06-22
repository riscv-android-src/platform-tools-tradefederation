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
package com.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.BuildInfoKey;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Unit tests for {@link HostGTest}. */
@RunWith(JUnit4.class)
public class HostGTestTest {
    private File mTestsDir;
    private HostGTest mHostGTest;
    private ITestInvocationListener mMockInvocationListener;
    private IShellOutputReceiver mMockReceiver;
    private OptionSetter mSetter;

    /** Helper to initialize the object or folder for unittest need. */
    @Before
    public void setUp() throws Exception {
        mTestsDir = FileUtil.createTempDir("test_folder_for_unittest");
        mMockInvocationListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockReceiver = EasyMock.createMock(IShellOutputReceiver.class);
        mMockReceiver.addOutput(EasyMock.anyObject(), EasyMock.anyInt(), EasyMock.anyInt());
        mMockReceiver.flush();
        EasyMock.expectLastCall().anyTimes();
        mHostGTest =
                new HostGTest() {
                    @Override
                    IShellOutputReceiver createResultParser(
                            String runName, ITestInvocationListener listener) {
                        return mMockReceiver;
                    }

                    @Override
                    GTestXmlResultParser createXmlParser(
                            String testRunName, ITestInvocationListener listener) {
                        return new GTestXmlResultParser(testRunName, listener) {
                            @Override
                            public void parseResult(File f, CollectingOutputReceiver output) {
                                return;
                            }
                        };
                    }
                };
        mSetter = new OptionSetter(mHostGTest);
    }

    @After
    public void afterMethod() {
        FileUtil.recursiveDelete(mTestsDir);
    }

    /** Helper that replays all mocks. */
    private void replayMocks() {
        EasyMock.replay(mMockInvocationListener, mMockReceiver);
    }

    /** Helper that verifies all mocks. */
    private void verifyMocks() {
        EasyMock.verify(mMockInvocationListener, mMockReceiver);
    }

    /**
     * Helper to create a executable file.
     *
     * <p>This method will create a executable file for unittest. This executable file is shell
     * script file. It will echo all arguments to a file like "file.call" when it has be called.
     * Therefore unittest call get the information form the .call file to determine test success or
     * not.
     *
     * @param folderName The path where to create.
     * @param fileName The file name you want to create.
     * @return The file path of "file.call", it is used to check if the file has been called
     *     correctly or not.
     */
    private File createExecutableFile(String folderName, String fileName) throws IOException {
        String postfix = ".called";
        Path path = Paths.get(folderName, fileName);
        String calledPath = fileName + postfix;
        String script = String.format("echo \"$@\" > $(dirname \"$0\")/%s", calledPath);
        File destFile = path.toFile();
        FileUtil.writeToFile(script, destFile);
        destFile.setExecutable(true);
        return new File(destFile.getAbsolutePath() + postfix);
    }

    /**
     * Helper to create a sub folder in mTestsDir.
     *
     * @param folderName The path where to create.
     * @return Sub folder File.
     */
    private File createSubFolder(String folderName) throws IOException {
        return FileUtil.createTempDir(folderName, mTestsDir);
    }

    /** Test the exucuteShellCommand method. */
    @Test
    public void testExecuteHostCommand_success() {
        CommandResult lsResult = mHostGTest.executeHostCommand("ls");
        assertNotEquals("", lsResult.getStdout());
    }

    /** Test the exucuteShellCommand method. */
    @Test(expected = RuntimeException.class)
    public void testExecuteHostCommand_fail() {
        CommandResult cmdResult = mHostGTest.executeHostCommand("");
        assertNotEquals("", cmdResult.getStderr());
    }

    /** Test the loadFilter method. */
    @Test
    public void testLoadFilter() throws ConfigurationException, IOException {
        String moduleName = "hello_world_test";
        String testFilterKey = "presubmit";
        OptionSetter setter = new OptionSetter(mHostGTest);
        setter.setOptionValue("test-filter-key", testFilterKey);

        String filter = "LayerTransactionTest.*:LayerUpdateTest.*";
        String json_content =
                "{\n"
                        + "        \""
                        + testFilterKey
                        + "\": {\n"
                        + "            \"filter\": \""
                        + filter
                        + "\"\n"
                        + "        }\n"
                        + "}\n";
        Path path = Paths.get(mTestsDir.getAbsolutePath(), moduleName + GTestBase.FILTER_EXTENSION);
        File filterFile = path.toFile();
        filterFile.createNewFile();
        filterFile.setReadable(true);
        FileUtil.writeToFile(json_content, filterFile);
        assertEquals(
                mHostGTest.loadFilter(filterFile.getParent() + File.separator + moduleName),
                filter);
    }

    /** Test runTest method. */
    @Test
    public void testRunTest()
            throws ConfigurationException, IOException, DeviceNotAvailableException {
        String moduleName = "hello_world_test";
        String dirPath = mTestsDir.getAbsolutePath();
        File cmd1 = createExecutableFile(dirPath, "cmd1");
        File cmd2 = createExecutableFile(dirPath, "cmd2");
        File cmd3 = createExecutableFile(dirPath, "cmd3");
        File cmd4 = createExecutableFile(dirPath, "cmd4");

        OptionSetter setter = new OptionSetter(mHostGTest);
        setter.setOptionValue("before-test-cmd", dirPath + File.separator + "cmd1");
        setter.setOptionValue("before-test-cmd", dirPath + File.separator + "cmd2");
        setter.setOptionValue("after-test-cmd", dirPath + File.separator + "cmd3");
        setter.setOptionValue("after-test-cmd", dirPath + File.separator + "cmd4");
        setter.setOptionValue("module-name", moduleName);

        File hostLinkedFolder = createSubFolder("hosttestcases");
        createExecutableFile(hostLinkedFolder.getAbsolutePath(), moduleName);

        DeviceBuildInfo buildInfo = new DeviceBuildInfo();
        buildInfo.setFile(BuildInfoKey.BuildInfoFileKey.HOST_LINKED_DIR, hostLinkedFolder, "0.0");
        mHostGTest.setBuild(buildInfo);

        replayMocks();
        mHostGTest.run(mMockInvocationListener);

        assertTrue(cmd1.exists());
        assertTrue(cmd2.exists());
        assertTrue(cmd3.exists());
        assertTrue(cmd4.exists());
        verifyMocks();
    }

    /** Test the run method for host linked folder is set. */
    @Test
    public void testRun_priority_get_testcase_from_hostlinked_folder()
            throws IOException, ConfigurationException, DeviceNotAvailableException {
        String moduleName = "hello_world_test";
        String hostLinkedFolderName = "hosttestcases";
        File hostLinkedFolder = createSubFolder(hostLinkedFolderName);
        File hostTestcaseExecutedCheckFile =
                createExecutableFile(hostLinkedFolder.getAbsolutePath(), moduleName);

        String testFolderName = "testcases";
        File testcasesFolder = createSubFolder(testFolderName);
        File testfolderTestcaseCheckExecuted =
                createExecutableFile(testcasesFolder.getAbsolutePath(), moduleName);

        mSetter.setOptionValue("module-name", moduleName);
        DeviceBuildInfo buildInfo = new DeviceBuildInfo();
        buildInfo.setFile(BuildInfoKey.BuildInfoFileKey.HOST_LINKED_DIR, hostLinkedFolder, "0.0");
        buildInfo.setTestsDir(testcasesFolder, "0.0");
        mHostGTest.setBuild(buildInfo);

        replayMocks();
        mHostGTest.run(mMockInvocationListener);
        assertTrue(hostTestcaseExecutedCheckFile.exists());
        assertFalse(testfolderTestcaseCheckExecuted.exists());
        verifyMocks();
    }

    /** Test the run method for host linked folder is not set. */
    @Test
    public void testRun_get_testcase_from_testcases_folder_if_no_hostlinked_dir_set()
            throws IOException, ConfigurationException, DeviceNotAvailableException {
        String moduleName = "hello_world_test";
        String hostLinkedFolderName = "hosttestcases";
        File hostLinkedFolder = createSubFolder(hostLinkedFolderName);
        File hostTestcaseExecutedCheckFile =
                createExecutableFile(hostLinkedFolder.getAbsolutePath(), moduleName);

        String testFolderName = "testcases";
        File testcasesFolder = createSubFolder(testFolderName);
        File testfolderTestcaseCheckExecuted =
                createExecutableFile(testcasesFolder.getAbsolutePath(), moduleName);

        mSetter.setOptionValue("module-name", moduleName);
        DeviceBuildInfo buildInfo = new DeviceBuildInfo();
        buildInfo.setTestsDir(testcasesFolder, "0.0");
        mHostGTest.setBuild(buildInfo);

        replayMocks();
        mHostGTest.run(mMockInvocationListener);
        assertFalse(hostTestcaseExecutedCheckFile.exists());
        assertTrue(testfolderTestcaseCheckExecuted.exists());
        verifyMocks();
    }

    /** Test can't find testcase. */
    @Test(expected = RuntimeException.class)
    public void testRun_can_not_find_testcase()
            throws ConfigurationException, DeviceNotAvailableException {
        String moduleName = "hello_world_test";
        mSetter.setOptionValue("module-name", moduleName);
        DeviceBuildInfo buildInfo = new DeviceBuildInfo();
        mHostGTest.setBuild(buildInfo);

        replayMocks();
        mHostGTest.run(mMockInvocationListener);
        verifyMocks();
    }
}
