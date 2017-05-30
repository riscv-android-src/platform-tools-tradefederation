/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.build;

import static org.junit.Assert.*;

import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.Collection;

/** Unit tests for {@link BuildInfo}. */
@RunWith(JUnit4.class)
public class DeviceFolderBuildInfoTest {
    private DeviceFolderBuildInfo mDeviceFolderBuildInfo;
    private File mFile;

    @Before
    public void setUp() throws Exception {
        mDeviceFolderBuildInfo = new DeviceFolderBuildInfo("1", "target");
        mDeviceFolderBuildInfo.setFolderBuild(new FolderBuildInfo("1", "target"));
        mDeviceFolderBuildInfo.setDeviceBuild(new DeviceBuildInfo());
        mFile = FileUtil.createTempFile("image", "tmp");
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mFile);
    }

    /** Return bool collection contains VersionedFile with specified version." */
    boolean hasFile(Collection<VersionedFile> files, String version) {
        for (VersionedFile candidate : files) {
            if (candidate.getVersion().equals(version)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test {@link DeviceFolderBuildInfo#getFiles()}. Verify files added to root and nested
     * DeviceBuildInfo are in result.
     */
    @Test
    public void testGetFiles_both() {
        mDeviceFolderBuildInfo.setFile("foo", mFile, "foo-version");
        mDeviceFolderBuildInfo.setDeviceImageFile(mFile, "img-version");
        Collection<VersionedFile> files = mDeviceFolderBuildInfo.getFiles();

        assertEquals(2, files.size());
        assertTrue(hasFile(files, "foo-version"));
        assertTrue(hasFile(files, "img-version"));
    }

    /**
     * Test {@link DeviceFolderBuildInfo#getFiles()}. Verify empty result when no files are present.
     */
    @Test
    public void testGetFiles_none() {
        Collection<VersionedFile> files = mDeviceFolderBuildInfo.getFiles();
        assertEquals(0, files.size());
    }

    /** Test {@link DeviceFolderBuildInfo#getFiles()}. Verify device image in result. */
    @Test
    public void testGetFiles_deviceImages() {
        mDeviceFolderBuildInfo.setDeviceImageFile(mFile, "img-version");
        Collection<VersionedFile> files = mDeviceFolderBuildInfo.getFiles();

        assertEquals(1, files.size());
        assertTrue(hasFile(files, "img-version"));
    }
}
