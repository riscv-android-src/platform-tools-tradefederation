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
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.util.List;

/** Unit tests for {@link IsolatedHostTest}. */
public class IsolatedHostTestTest {

    private static final File FAKE_REMOTE_FILE_PATH = new File("gs://bucket/path/file");

    private IsolatedHostTest mHostTest;
    private ITestInvocationListener mListener;
    private IBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;
    private ServerSocket mMockServer;

    private IRemoteFileResolver mMockResolver;

    public static class TestableIsolatedHostTest extends IsolatedHostTest {
        public TestableIsolatedHostTest() {}
    }

    /** {@inheritDoc} */
    @Before
    public void setUp() throws Exception {
        mHostTest = new TestableIsolatedHostTest();
        mListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockServer = Mockito.mock(ServerSocket.class);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mHostTest.setBuild(mMockBuildInfo);
        mHostTest.setServer(mMockServer);
        OptionSetter setter = new OptionSetter(mHostTest);
        // Disable pretty logging for testing
        // setter.setOptionValue("enable-pretty-logs", "false");
    }

    @Test
    public void testRobolectricResourcesPositive() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("use-robolectric-resources", "true");
        EasyMock.expect(mMockBuildInfo.getFile(BuildInfoFileKey.HOST_LINKED_DIR))
                .andReturn(new File(System.getProperty("user.dir")));
        EasyMock.replay(mMockBuildInfo);
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
        EasyMock.expect(mMockBuildInfo.getFile(BuildInfoFileKey.HOST_LINKED_DIR))
                .andReturn(new File(System.getProperty("user.dir")));
        EasyMock.replay(mMockBuildInfo);
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
}
