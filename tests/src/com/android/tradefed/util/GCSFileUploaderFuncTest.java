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
 * limitations under the License
 */

package com.android.tradefed.util;

import com.android.tradefed.config.ConfigurationException;

import com.google.api.services.storage.Storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link GCSFileUploader} functional test. */
@RunWith(JUnit4.class)
public class GCSFileUploaderFuncTest {
    private static final String BUCKET_NAME = "tradefed_function_test";
    private static final String FILE_NAME = "test_file.txt";
    private static final String FILE_DATA = "Simple test string to write to file.";
    private static final String FILE_MIME_TYPE = "text/plain";
    private static final boolean ENABLE_OVERWRITE = true;
    private static final boolean DISABLE_OVERWRITE = false;

    private GCSFileUploader mUploader;
    private GCSFileDownloader mDownloader;

    private static byte[] toByteArray(InputStream in) throws IOException {

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len;

        // read bytes from the input stream and store them in buffer
        while ((len = in.read(buffer)) != -1) {
            // write bytes from the buffer into output stream
            os.write(buffer, 0, len);
        }

        return os.toByteArray();
    }

    private InputStream pullFileFromGcs(String gcsFilePath) throws ConfigurationException {
        try {
            return mDownloader.downloadFile(BUCKET_NAME, gcsFilePath);
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e.getCause());
        }
    }

    @Before
    public void setUp() throws IOException {
        mUploader = new GCSFileUploader();
        mDownloader = new GCSFileDownloader();
    }

    @After
    public void tearDown() throws IOException {
        Storage storage =
                mUploader.getStorage(
                        Collections.singleton(
                                "https://www.googleapis.com/auth/devstorage.read_write"));
        storage.objects().delete(BUCKET_NAME, FILE_NAME).execute();
    }

    @Test
    public void testUploadFile_roundTrip() throws Exception {
        InputStream uploadFileStream = new ByteArrayInputStream(FILE_DATA.getBytes());
        mUploader.uploadFile(
                BUCKET_NAME, FILE_NAME, uploadFileStream, FILE_MIME_TYPE, ENABLE_OVERWRITE);
        String readBack = new String(toByteArray(pullFileFromGcs(FILE_NAME)));
        Assert.assertEquals(FILE_DATA, readBack);
    }

    @Test
    public void testUploadFile_overwrite() throws Exception {
        InputStream uploadFileStream = new ByteArrayInputStream(FILE_DATA.getBytes());
        mUploader.uploadFile(
                BUCKET_NAME, FILE_NAME, uploadFileStream, FILE_MIME_TYPE, DISABLE_OVERWRITE);

        try {
            mUploader.uploadFile(
                    BUCKET_NAME, FILE_NAME, uploadFileStream, FILE_MIME_TYPE, DISABLE_OVERWRITE);
            Assert.fail("Should throw IOException.");
        } catch (IOException e) {
            // Expect IOException
        }

        mUploader.uploadFile(
                BUCKET_NAME, FILE_NAME, uploadFileStream, FILE_MIME_TYPE, ENABLE_OVERWRITE);
    }
}
