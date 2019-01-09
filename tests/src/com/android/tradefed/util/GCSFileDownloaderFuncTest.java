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

package com.android.tradefed.util;

import com.android.tradefed.build.BuildRetrievalError;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.BlobListOption;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

/** {@link GCSFileDownloader} functional test. */
@RunWith(JUnit4.class)
public class GCSFileDownloaderFuncTest {

    private static final String BUCKET_NAME = "tradefed_function_test";
    private static final String FILE_NAME1 = "a_host_config.xml";
    private static final String FILE_NAME2 = "file2.txt";
    private static final String FILE_NAME3 = "file3.txt";
    private static final String FILE_NAME4 = "file4.txt";
    private static final String FOLDER_NAME1 = "folder1";
    private static final String FOLDER_NAME2 = "folder2";
    private static final String FILE_CONTENT = "Hello World!";

    private GCSFileDownloader mDownloader;
    private Bucket mBucket;
    private String mRemoteRoot;
    private File mLocalRoot;

    private static void createFile(String content, Bucket bucket, String... pathSegs) {
        String path = String.join("/", pathSegs);
        bucket.create(path, content.getBytes());
    }

    @Before
    public void setUp() throws IOException {
        File tempFile =
                FileUtil.createTempFile(GCSFileDownloaderFuncTest.class.getSimpleName(), "");
        mRemoteRoot = tempFile.getName();
        FileUtil.deleteFile(tempFile);
        mDownloader =
                new GCSFileDownloader() {

                    @Override
                    File createTempFile(String remoteFilePath, File rootDir)
                            throws BuildRetrievalError {
                        try {
                            File tmpFile =
                                    FileUtil.createTempFileForRemote(remoteFilePath, mLocalRoot);
                            tmpFile.delete();
                            return tmpFile;
                        } catch (IOException e) {
                            throw new BuildRetrievalError(e.getMessage(), e);
                        }
                    }
                };
        mBucket = mDownloader.getStorage().get(BUCKET_NAME);
        createFile(FILE_CONTENT, mBucket, mRemoteRoot, FILE_NAME1);
        createFile(FILE_NAME2, mBucket, mRemoteRoot, FOLDER_NAME1, FILE_NAME2);
        createFile(FILE_NAME3, mBucket, mRemoteRoot, FOLDER_NAME1, FILE_NAME3);
        createFile(FILE_NAME4, mBucket, mRemoteRoot, FOLDER_NAME1, FOLDER_NAME2, FILE_NAME4);
        mLocalRoot = FileUtil.createTempDir(GCSFileDownloaderFuncTest.class.getSimpleName());

    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mLocalRoot);
        for (Blob blob : mBucket.list(BlobListOption.prefix(mRemoteRoot)).iterateAll()) {
            blob.delete();
        }
    }

    @Test
    public void testDownloadFile_streamOutput() throws Exception {
        InputStream inputStream =
                mDownloader.downloadFile(BUCKET_NAME, mRemoteRoot + "/" + FILE_NAME1);
        String content = StreamUtil.getStringFromStream(inputStream);
        Assert.assertEquals(FILE_CONTENT, content);
    }

    @Test
    public void testDownloadFile_streamOutput_notExist() throws Exception {
        try {
            mDownloader.downloadFile(BUCKET_NAME, mRemoteRoot + "/" + "non_exist_file");
            Assert.fail("Should throw IOExcepiton.");
        } catch (IOException e) {
            // Expect IOException
        }
    }

    @Test
    public void testDownloadFile() throws Exception {
        File localFile =
                mDownloader.downloadFile(
                        String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, FILE_NAME1));
        String content = FileUtil.readStringFromFile(localFile);
        Assert.assertEquals(FILE_CONTENT, content);
    }

    @Test
    public void testDownloadFile_nonExist() throws Exception {
        try {
            mDownloader.downloadFile(
                    String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, "non_exist_file"));
            Assert.fail("Should throw BuildRetrievalError.");
        } catch (BuildRetrievalError e) {
            // Expect BuildRetrievalError
        }
    }

    @Test
    public void testDownloadFile_folder() throws Exception {
        File localFile =
                mDownloader.downloadFile(
                        String.format("gs://%s/%s/%s/", BUCKET_NAME, mRemoteRoot, FOLDER_NAME1));
        checkDownloadedFolder(localFile);
    }

    @Test
    public void testDownloadFile_folderNotsanitize() throws Exception {
        File localFile =
                mDownloader.downloadFile(
                        String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, FOLDER_NAME1));
        checkDownloadedFolder(localFile);
    }

    private void checkDownloadedFolder(File localFile) throws Exception {
        Assert.assertTrue(localFile.isDirectory());
        Assert.assertEquals(3, localFile.list().length);
        for (String filename : localFile.list()) {
            if (filename.equals(FILE_NAME2)) {
                Assert.assertEquals(
                        FILE_NAME2,
                        FileUtil.readStringFromFile(
                                new File(localFile.getAbsolutePath(), filename)));
            } else if (filename.equals(FILE_NAME3)) {
                Assert.assertEquals(
                        FILE_NAME3,
                        FileUtil.readStringFromFile(
                                new File(localFile.getAbsolutePath(), filename)));
            } else if (filename.equals(FOLDER_NAME2)) {
                File subFolder = new File(localFile.getAbsolutePath(), filename);
                Assert.assertTrue(subFolder.isDirectory());
                Assert.assertEquals(1, subFolder.list().length);
                Assert.assertEquals(
                        FILE_NAME4,
                        FileUtil.readStringFromFile(
                                new File(subFolder.getAbsolutePath(), subFolder.list()[0])));
            } else {
                Assert.assertTrue(String.format("Unknonwn file %s", filename), false);
            }
        }
    }

    @Test
    public void testDownloadFile_folder_nonExist() throws Exception {
        try {
            mDownloader.downloadFile(
                    String.format("gs://%s/%s/%s/", BUCKET_NAME, "mRemoteRoot", "nonExistFolder"));
            Assert.fail("Should throw BuildRetrievalError.");
        } catch (BuildRetrievalError e) {
            // Expect BuildRetrievalError
        }
    }

    @Test
    public void testCheckFreshness() throws Exception {
        String remotePath = String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, FILE_NAME1);
        File localFile = mDownloader.downloadFile(remotePath);
        Assert.assertTrue(mDownloader.isFresh(localFile, remotePath));
    }

    @Test
    public void testCheckFreshness_notFresh() throws Exception {
        String remotePath = String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, FILE_NAME1);
        File localFile = mDownloader.downloadFile(remotePath);
        // Change the remote file.
        createFile("New content.", mBucket, mRemoteRoot, FILE_NAME1);
        Assert.assertFalse(mDownloader.isFresh(localFile, remotePath));
    }

    @Test
    public void testCheckFreshness_folder() throws Exception {
        String remotePath = String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, FOLDER_NAME1);
        File localFolder = mDownloader.downloadFile(remotePath);
        Assert.assertTrue(mDownloader.isFresh(localFolder, remotePath));
    }

    @Test
    public void testCheckFreshness_folder_addFile() throws Exception {
        String remotePath = String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, FOLDER_NAME1);
        File localFolder = mDownloader.downloadFile(remotePath);
        createFile("A new file", mBucket, mRemoteRoot, FOLDER_NAME1, FOLDER_NAME2, "new_file.txt");
        Assert.assertFalse(mDownloader.isFresh(localFolder, remotePath));
    }

    @Test
    public void testCheckFreshness_folder_removeFile() throws Exception {
        String remotePath = String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, FOLDER_NAME1);
        File localFolder = mDownloader.downloadFile(remotePath);
        mBucket.get(Paths.get(mRemoteRoot, FOLDER_NAME1, FILE_NAME3).toString()).delete();
        Assert.assertFalse(mDownloader.isFresh(localFolder, remotePath));
    }

    @Test
    public void testCheckFreshness_folder_changeFile() throws Exception {
        String remotePath = String.format("gs://%s/%s/%s", BUCKET_NAME, mRemoteRoot, FOLDER_NAME1);
        File localFolder = mDownloader.downloadFile(remotePath);
        createFile("New content", mBucket, mRemoteRoot, FOLDER_NAME1, FILE_NAME3);
        Assert.assertFalse(mDownloader.isFresh(localFolder, remotePath));
    }
}
