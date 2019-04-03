/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.device.contentprovider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/** Run unit tests for {@link ContentProviderHandler}. */
@RunWith(JUnit4.class)
public class ContentProviderHandlerTest {

    private ContentProviderHandler mProvider;
    private ITestDevice mMockDevice;

    @Before
    public void setUp() {
        mMockDevice = Mockito.mock(ITestDevice.class);
        mProvider = new ContentProviderHandler(mMockDevice);
    }

    @After
    public void tearDown() throws Exception {
        mProvider.tearDown();
    }

    /** Test the install flow. */
    @Test
    public void testSetUp_install() throws Exception {
        Set<String> set = new HashSet<>();
        doReturn(set).when(mMockDevice).getInstalledPackageNames();
        doReturn(1).when(mMockDevice).getCurrentUser();
        doReturn(null).when(mMockDevice).installPackage(any(), eq(true), eq(true));
        assertTrue(mProvider.setUp());
    }

    @Test
    public void testSetUp_alreadyInstalled() throws Exception {
        Set<String> set = new HashSet<>();
        set.add(ContentProviderHandler.PACKAGE_NAME);
        doReturn(set).when(mMockDevice).getInstalledPackageNames();

        assertTrue(mProvider.setUp());
    }

    @Test
    public void testSetUp_installFail() throws Exception {
        Set<String> set = new HashSet<>();
        doReturn(set).when(mMockDevice).getInstalledPackageNames();
        doReturn(1).when(mMockDevice).getCurrentUser();
        doReturn("fail").when(mMockDevice).installPackage(any(), eq(true), eq(true));

        assertFalse(mProvider.setUp());
    }

    /** Test {@link ContentProviderHandler#deleteFile(String)}. */
    @Test
    public void testDeleteFile() throws Exception {
        String devicePath = "path/somewhere/file.txt";
        doReturn(99).when(mMockDevice).getCurrentUser();
        doReturn(mockSuccess())
                .when(mMockDevice)
                .executeShellV2Command(
                        eq(
                                "content delete --user 99 --uri "
                                        + ContentProviderHandler.createEscapedContentUri(
                                                devicePath)));
        assertTrue(mProvider.deleteFile(devicePath));
    }

    /** Test {@link ContentProviderHandler#deleteFile(String)}. */
    @Test
    public void testDeleteFile_fail() throws Exception {
        String devicePath = "path/somewhere/file.txt";
        CommandResult result = new CommandResult(CommandStatus.FAILED);
        result.setStderr("couldn't find the file");
        doReturn(99).when(mMockDevice).getCurrentUser();
        doReturn(result)
                .when(mMockDevice)
                .executeShellV2Command(
                        eq(
                                "content delete --user 99 --uri "
                                        + ContentProviderHandler.createEscapedContentUri(
                                                devicePath)));
        assertFalse(mProvider.deleteFile(devicePath));
    }

    /** Test {@link ContentProviderHandler#pushFile(File, String)}. */
    @Test
    public void testPushFile() throws Exception {
        File toPush = FileUtil.createTempFile("content-provider-test", ".txt");
        try {
            String devicePath = "path/somewhere/file.txt";
            doReturn(99).when(mMockDevice).getCurrentUser();
            doReturn(mockSuccess())
                    .when(mMockDevice)
                    .executeShellV2Command(
                            eq(
                                    "content write --user 99 --uri "
                                            + ContentProviderHandler.createEscapedContentUri(
                                                    devicePath)),
                            eq(toPush));
            assertTrue(mProvider.pushFile(toPush, devicePath));
        } finally {
            FileUtil.deleteFile(toPush);
        }
    }

    /** Test {@link ContentProviderHandler#pullFile(String, File)}. */
    @Test
    public void testPullFile_verifyShellCommand() throws Exception {
        File pullTo = FileUtil.createTempFile("content-provider-test", ".txt");
        String devicePath = "path/somewhere/file.txt";
        doReturn(99).when(mMockDevice).getCurrentUser();
        mockPullFileSuccess();

        try {
            mProvider.pullFile(devicePath, pullTo);

            // Capture the shell command used by pullFile.
            ArgumentCaptor<String> shellCommandCaptor = ArgumentCaptor.forClass(String.class);
            verify(mMockDevice)
                    .executeShellV2Command(shellCommandCaptor.capture(), any(OutputStream.class));

            // Verify the command.
            assertEquals(
                    shellCommandCaptor.getValue(),
                    "content read --user 99 --uri "
                            + ContentProviderHandler.createEscapedContentUri(devicePath));
        } finally {
            FileUtil.deleteFile(pullTo);
        }
    }

    /** Test {@link ContentProviderHandler#pullFile(String, File)}. */
    @Test
    public void testPullFile_createLocalFileIfNotExist() throws Exception {
        File pullTo = new File("content-provider-test.txt");
        String devicePath = "path/somewhere/file.txt";
        mockPullFileSuccess();

        try {
            assertFalse(pullTo.exists());
            mProvider.pullFile(devicePath, pullTo);
            assertTrue(pullTo.exists());
        } finally {
            FileUtil.deleteFile(pullTo);
        }
    }

    /** Test {@link ContentProviderHandler#pullFile(String, File)}. */
    @Test
    public void testPullFile_success() throws Exception {
        File pullTo = new File("content-provider-test.txt");
        String devicePath = "path/somewhere/file.txt";

        try {
            mockPullFileSuccess();
            assertTrue(mProvider.pullFile(devicePath, pullTo));
        } finally {
            FileUtil.deleteFile(pullTo);
        }
    }

    @Test
    public void testCreateUri() throws Exception {
        String espacedUrl =
                ContentProviderHandler.createEscapedContentUri("filepath/file name spaced (data)");
        // We expect the full url to be quoted to avoid space issues and the URL to be encoded.
        assertEquals(
                "\"content://android.tradefed.contentprovider/filepath%252Ffile%2520name"
                        + "%2520spaced%2520%28data%29\"",
                espacedUrl);
    }

    private CommandResult mockSuccess() {
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStderr("");
        return result;
    }

    private void mockPullFileSuccess() throws Exception {
        doReturn(mockSuccess())
                .when(mMockDevice)
                .executeShellV2Command(anyString(), any(OutputStream.class));
    }
}
