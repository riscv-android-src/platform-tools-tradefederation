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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

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
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        doReturn(99).when(mMockDevice).getCurrentUser();
        doReturn(result)
                .when(mMockDevice)
                .executeShellV2Command(
                        eq(
                                "content delete --user 99 --uri "
                                        + ContentProviderHandler.CONTENT_PROVIDER_URI
                                        + "/"
                                        + devicePath));
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
                                        + ContentProviderHandler.CONTENT_PROVIDER_URI
                                        + "/"
                                        + devicePath));
        assertFalse(mProvider.deleteFile(devicePath));
    }
}
