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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

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

    /**
     * test {@link FileUtil#createTempFile(String, String)} with a very long file name. FileSystem
     * should not throw any exception.
     */
    public void testCreateTempFile_filenameTooLong() throws IOException {
        File testFile = null;
        try {
            final String prefix = "logcat-android.support.v7.widget.GridLayoutManagerWrapContent"
                    + "WithAspectRatioTest_testAllChildrenWrapContentInOtherDirection_"
                    + "WrapContentConfig_unlimitedWidth=true_ unlimitedHeight=true_padding="
                    + "Rect(0_0-0_0)_Config_mSpanCount=3_ mOrientation=v_mItemCount=1000_"
                    + "mReverseLayout=false_ 8__";
            testFile = FileUtil.createTempFile(prefix, ".gz");
            assertTrue(testFile.getName().length() <= FileUtil.FILESYSTEM_FILENAME_MAX_LENGTH);
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * test {@link FileUtil#createTempFile(String, String)} with a very long file name. FileSystem
     * should not throw any exception.
     * If both suffix is smaller than overflow length, it will be completely truncated, and prefix
     * will truncate the remaining.
     */
    public void testCreateTempFile_filenameTooLongEdge() throws IOException {
        File testFile = null;
        try {
            final String prefix = "logcat-android.support.v7.widget.GridLayoutManagerWrapContent"
                    + "WithAspectRatioTest_testAllChildrenWrapContentInOtherDirection_"
                    + "WrapContentConfig_unlimitedWidth=true_ unlimitedHeight=true_padding="
                    + "Rect(0_0-0_0)_Config_mSpanCount=3_ mOrientation";
            final String suffix = "logcat-android.support.v7.widget.GridLayoutManagerWrapContent"
                    + "WithAspectRatioTest_testAllChildrenWrapContentInOtherDirection_"
                    + "WrapContentConfig_unlimitedWidth=true_ unlimitedHeight=true_padding="
                    + "Rect(0_0-0_0)_Config_mSpanCount=3_ mOrientat";
            testFile = FileUtil.createTempFile(prefix, suffix);
            assertTrue(testFile.getName().length() <= FileUtil.FILESYSTEM_FILENAME_MAX_LENGTH);
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#writeToFile(InputStream, File, boolean)} succeeds overwriting an
     * existent file.
     */
    public void testWriteToFile_overwrites_exists() throws IOException {
        File testFile = null;
        try {
            testFile = File.createTempFile("doesnotmatter", ".txt");
            FileUtil.writeToFile(new ByteArrayInputStream("write1".getBytes()), testFile, false);
            assertEquals(FileUtil.readStringFromFile(testFile), "write1");
            FileUtil.writeToFile(new ByteArrayInputStream("write2".getBytes()), testFile, false);
            assertEquals(FileUtil.readStringFromFile(testFile), "write2");
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#writeToFile(InputStream, File, boolean)} succeeds appending to an
     * existent file.
     */
    public void testWriteToFile_appends_exists() throws IOException {
        File testFile = null;
        try {
            testFile = File.createTempFile("doesnotmatter", ".txt");
            FileUtil.writeToFile(new ByteArrayInputStream("write1".getBytes()), testFile, true);
            FileUtil.writeToFile(new ByteArrayInputStream("write2".getBytes()), testFile, true);
            assertEquals(FileUtil.readStringFromFile(testFile), "write1write2");
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#writeToFile(InputStream, File, boolean)} succeeds writing to an
     * uncreated file.
     */
    public void testWriteToFile_overwrites_doesNotExist() throws IOException {
        File testFile = null;
        try {
            testFile = new File("nonexistant");
            FileUtil.writeToFile(new ByteArrayInputStream("write1".getBytes()), testFile, false);
            assertEquals(FileUtil.readStringFromFile(testFile), "write1");
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#writeToFile(InputStream, File, boolean)} succeeds appending to an
     * uncreated file.
     */
    public void testWriteToFile_appends_doesNotExist() throws IOException {
        File testFile = null;
        try {
            testFile = new File("nonexistant");
            FileUtil.writeToFile(new ByteArrayInputStream("write1".getBytes()), testFile, true);
            assertEquals(FileUtil.readStringFromFile(testFile), "write1");
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#writeToFile(InputStream, File)} succeeds overwriting to a file.
     */
    public void testWriteToFile_stream_overwrites() throws IOException {
        File testFile = null;
        try {
            testFile = File.createTempFile("doesnotmatter", ".txt");
            FileUtil.writeToFile(new ByteArrayInputStream("write1".getBytes()), testFile);
            assertEquals(FileUtil.readStringFromFile(testFile), "write1");
            FileUtil.writeToFile(new ByteArrayInputStream("write2".getBytes()), testFile);
            assertEquals(FileUtil.readStringFromFile(testFile), "write2");
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#writeToFile(String, File, boolean)} succeeds overwriting to a file.
     */
    public void testWriteToFile_string_overwrites() throws IOException {
        File testFile = null;
        try {
            testFile = File.createTempFile("doesnotmatter", ".txt");
            FileUtil.writeToFile("write1", testFile, false);
            assertEquals(FileUtil.readStringFromFile(testFile), "write1");
            FileUtil.writeToFile("write2", testFile, false);
            assertEquals(FileUtil.readStringFromFile(testFile), "write2");
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#writeToFile(String, File, boolean)} succeeds appending to a file.
     */
    public void testWriteToFile_string_appends() throws IOException {
        File testFile = null;
        try {
            testFile = File.createTempFile("doesnotmatter", ".txt");
            FileUtil.writeToFile("write1", testFile, true);
            FileUtil.writeToFile("write2", testFile, true);
            assertEquals(FileUtil.readStringFromFile(testFile), "write1write2");
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#writeToFile(String, File)} succeeds overwriting to a file.
     */
    public void testWriteToFile_string_defaultOverwrites() throws IOException {
        File testFile = null;
        try {
            testFile = File.createTempFile("doesnotmatter", ".txt");
            FileUtil.writeToFile("write1", testFile);
            assertEquals(FileUtil.readStringFromFile(testFile), "write1");
            FileUtil.writeToFile("write2", testFile);
            assertEquals(FileUtil.readStringFromFile(testFile), "write2");
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    /**
     * Test {@link FileUtil#unixModeToPosix(int)} returns expected results;
     */
    public void testUnixModeToPosix() {
        Set<PosixFilePermission> perms = null;
        // can't test all 8 * 8 * 8, so just a select few
        perms = FileUtil.unixModeToPosix(0777);
        assertTrue("failed unix mode conversion: 0777",
                perms.remove(PosixFilePermission.OWNER_READ) &&
                perms.remove(PosixFilePermission.OWNER_WRITE) &&
                perms.remove(PosixFilePermission.OWNER_EXECUTE) &&
                perms.remove(PosixFilePermission.GROUP_READ) &&
                perms.remove(PosixFilePermission.GROUP_WRITE) &&
                perms.remove(PosixFilePermission.GROUP_EXECUTE) &&
                perms.remove(PosixFilePermission.OTHERS_READ) &&
                perms.remove(PosixFilePermission.OTHERS_WRITE) &&
                perms.remove(PosixFilePermission.OTHERS_EXECUTE) &&
                perms.isEmpty());
        perms = FileUtil.unixModeToPosix(0644);
        assertTrue("failed unix mode conversion: 0644",
                perms.remove(PosixFilePermission.OWNER_READ) &&
                perms.remove(PosixFilePermission.OWNER_WRITE) &&
                perms.remove(PosixFilePermission.GROUP_READ) &&
                perms.remove(PosixFilePermission.OTHERS_READ) &&
                perms.isEmpty());
        perms = FileUtil.unixModeToPosix(0755);
        assertTrue("failed unix mode conversion: 0755",
                perms.remove(PosixFilePermission.OWNER_READ) &&
                perms.remove(PosixFilePermission.OWNER_WRITE) &&
                perms.remove(PosixFilePermission.OWNER_EXECUTE) &&
                perms.remove(PosixFilePermission.GROUP_READ) &&
                perms.remove(PosixFilePermission.GROUP_EXECUTE) &&
                perms.remove(PosixFilePermission.OTHERS_READ) &&
                perms.remove(PosixFilePermission.OTHERS_EXECUTE) &&
                perms.isEmpty());
    }
}
