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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.remote.IRemoteFileResolver;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;

/** Unit tests for {@link IsolatedHostTest}. */
public class IsolatedHostTestTest {

    private static final File FAKE_REMOTE_FILE_PATH = new File("gs://bucket/path/file");

    private IsolatedHostTest mHostTest;
    private ITestInvocationListener mListener;
    private IBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;
    private ServerSocket mMockServer;
    private File mMockTestDir;

    private IRemoteFileResolver mMockResolver;

    public static class TestableIsolatedHostTest extends IsolatedHostTest {
        public TestableIsolatedHostTest() {}
    }

    /**
     * (copied and altered from JarHostTestTest) Helper to read a file from the res/testtype
     * directory and return it.
     *
     * @param filename the name of the file in the res/testtype directory
     * @param parentDir dir where to put the jar. Null if in default tmp directory.
     * @param name name to use in the target directory for the jar.
     * @return the extracted jar file.
     */
    protected File getJarResource(String filename, File parentDir, String name) throws IOException {
        InputStream jarFileStream = getClass().getResourceAsStream(filename);
        File jarFile = new File(parentDir, name);
        jarFile.createNewFile();
        FileUtil.writeToFile(jarFileStream, jarFile);
        return jarFile;
    }

    /** {@inheritDoc} */
    @Before
    public void setUp() throws Exception {
        mHostTest = new TestableIsolatedHostTest();
        mListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockBuildInfo = Mockito.mock(IBuildInfo.class);
        mMockServer = Mockito.mock(ServerSocket.class);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mHostTest.setBuild(mMockBuildInfo);
        mHostTest.setServer(mMockServer);
        OptionSetter setter = new OptionSetter(mHostTest);
        mMockTestDir = FileUtil.createTempDir("isolatedhosttesttest");
        // Disable pretty logging for testing
        // setter.setOptionValue("enable-pretty-logs", "false");
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mMockTestDir);
    }

    @Test
    public void testRobolectricResourcesPositive() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "true");
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(36000).when(mMockServer).getLocalPort();
        doReturn(Inet4Address.getByName("localhost")).when(mMockServer).getInetAddress();

        List<String> commandArgs = mHostTest.compileCommandArgs("");
        assertTrue(commandArgs.contains("-Drobolectric.offline=true"));
        assertTrue(commandArgs.contains("-Drobolectric.logging=stdout"));
        assertTrue(commandArgs.contains("-Drobolectric.resourcesMode=binary"));
        assertTrue(commandArgs.stream().anyMatch(s -> s.contains("-Drobolectric.dependency.dir=")));
    }

    @Test
    public void testRobolectricResourcesNegative() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "false");
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(36000).when(mMockServer).getLocalPort();
        doReturn(Inet4Address.getByName("localhost")).when(mMockServer).getInetAddress();

        List<String> commandArgs = mHostTest.compileCommandArgs("");
        assertFalse(commandArgs.contains("-Drobolectric.offline=true"));
        assertFalse(commandArgs.contains("-Drobolectric.logging=stdout"));
        assertFalse(commandArgs.contains("-Drobolectric.resourcesMode=binary"));
        assertFalse(
                commandArgs.stream().anyMatch(s -> s.contains("-Drobolectric.dependency.dir=")));
    }

    /**
     * TODO(murj) need to figure out a strategy with jdesprez on how to test the classpath
     * determination functionality.
     *
     * @throws Exception
     */
    @Test
    public void testRobolectricResourcesClasspathPositive() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "true");
    }

    /**
     * TODO(murj) same as above
     *
     * @throws Exception
     */
    @Test
    public void testRobolectricResourcesClasspathNegative() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "false");
    }

    @Test
    public void testSimpleFailingTestLifecycle() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        getJarResource(
                "/referenceTests/SimpleFailingTest.jar", mMockTestDir, "SimpleFailingTest.jar");
        setter.setOptionValue("jar", "SimpleFailingTest.jar");
        setter.setOptionValue("exclude-paths", "org/junit");
        setter.setOptionValue("exclude-paths", "junit");
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.TESTDIR_IMAGE);
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test =
                new TestDescription(
                        "com.android.tradefed.testtype.isolation.SimpleFailingTest", "test2Plus2");

        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test), EasyMock.anyInt());
        mListener.testFailed(EasyMock.eq(test), (String) EasyMock.anyObject());
        mListener.testEnded(
                EasyMock.eq(test),
                EasyMock.anyInt(),
                (HashMap<String, Metric>) EasyMock.anyObject());
        mListener.testLog(
                (String) EasyMock.anyObject(), EasyMock.eq(LogDataType.TEXT), EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (HashMap<String, Metric>) EasyMock.anyObject());

        EasyMock.replay(mListener);
        mHostTest.run(testInfo, mListener);
        EasyMock.verify(mListener);
    }

    @Test
    public void testSimplePassingTestLifecycle() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        getJarResource(
                "/referenceTests/SimplePassingTest.jar", mMockTestDir, "SimplePassingTest.jar");
        setter.setOptionValue("jar", "SimplePassingTest.jar");
        setter.setOptionValue("exclude-paths", "org/junit");
        setter.setOptionValue("exclude-paths", "junit");
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.HOST_LINKED_DIR);
        doReturn(mMockTestDir).when(mMockBuildInfo).getFile(BuildInfoFileKey.TESTDIR_IMAGE);
        TestInformation testInfo = TestInformation.newBuilder().build();
        TestDescription test =
                new TestDescription(
                        "com.android.tradefed.testtype.isolation.SimplePassingTest", "test2Plus2");

        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test), EasyMock.anyInt());
        mListener.testEnded(
                EasyMock.eq(test),
                EasyMock.anyInt(),
                (HashMap<String, Metric>) EasyMock.anyObject());
        mListener.testLog(
                (String) EasyMock.anyObject(), EasyMock.eq(LogDataType.TEXT), EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (HashMap<String, Metric>) EasyMock.anyObject());

        EasyMock.replay(mListener);
        mHostTest.run(testInfo, mListener);
        EasyMock.verify(mListener);
    }
}
