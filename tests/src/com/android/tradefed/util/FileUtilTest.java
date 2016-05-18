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
package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * Unit tests for {@link FileUtil}
 */
public class FileUtilTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FileUtil.setChmodBinary("chmod");
    }

    /**
     * test {@link FileUtil#getExtension(String)}
     */
    public void testGetExtension() {
        assertEquals("", FileUtil.getExtension("filewithoutext"));
        assertEquals(".txt", FileUtil.getExtension("file.txt"));
        assertEquals(".txt", FileUtil.getExtension("foo.file.txt"));
    }

    /**
     * test {@link FileUtil#chmodGroupRW(File)} on a system that supports 'chmod'
     */
    public void testChmodGroupRW() throws IOException {
        File testFile = null;
        try {
            if (!FileUtil.chmodExists()) {
                CLog.d("Chmod not available, skipping the test");
                return;
            }
            testFile = FileUtil.createTempFile("testChmodRW", ".txt");
            assertTrue(FileUtil.chmodGroupRW(testFile));
            assertTrue(testFile.canRead());
            assertTrue(testFile.canWrite());
            assertFalse(testFile.canExecute());
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * test {@link FileUtil#chmodGroupRW(File)} on a system that does not supports 'chmod'.
     * File permission should still be set with the fallback.
     */
    public void testChmodGroupRW_noChmod() throws IOException {
        File testFile = null;
        FileUtil.setChmodBinary("fake_not_existing_chmod");
        try {
            testFile = FileUtil.createTempFile("testChmodRW", ".txt");
            assertTrue(FileUtil.chmodGroupRW(testFile));
            assertTrue(testFile.canRead());
            assertTrue(testFile.canWrite());
            assertFalse(testFile.canExecute());
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * test {@link FileUtil#chmodGroupRWX(File)} on a system that supports 'chmod'
     */
    public void testChmodGroupRWX() throws IOException {
        File testFile = null;
        try {
            if (!FileUtil.chmodExists()) {
                CLog.d("Chmod not available, skipping the test");
                return;
            }
            testFile = FileUtil.createTempFile("testChmodRWX", ".txt");
            assertTrue(FileUtil.chmodGroupRWX(testFile));
            assertTrue(testFile.canRead());
            assertTrue(testFile.canWrite());
            assertTrue(testFile.canExecute());
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * test {@link FileUtil#chmodGroupRWX(File)} on a system that does not supports 'chmod'.
     * File permission should still be set with the fallback.
     */
    public void testChmodGroupRWX_noChmod() throws IOException {
        File testFile = null;
        FileUtil.setChmodBinary("fake_not_existing_chmod");
        try {
            testFile = FileUtil.createTempFile("testChmodRWX", ".txt");
            assertTrue(FileUtil.chmodGroupRWX(testFile));
            assertTrue(testFile.canRead());
            assertTrue(testFile.canWrite());
            assertTrue(testFile.canExecute());
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }
}
