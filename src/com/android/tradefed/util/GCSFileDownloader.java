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

import com.android.tradefed.log.LogUtil.CLog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** File downloader to download file from google cloud storage (GCS). */
public class GCSFileDownloader {

    // https://cloud.google.com/storage/docs/gsutil
    private static final String GSUTIL = "gsutil";
    private static final String GCS_FILENAME_FORMAT = "gs://%s/%s";
    private static final String CP = "cp";
    private static final long TIMEOUT = 10000; // 10s
    private static final long RETRY_INTERVAL = 1000; // 1s
    private static final int ATTETMPTS = 3;

    /**
     * Download a file from a gcs bucket file.
     *
     * @param bucketName gcs bucket name
     * @param filename the filename
     * @return {@link InputStream} with the file content.
     */
    public InputStream downloadFile(String bucketName, String filename) throws IOException {
        checkGSUtil();
        CLog.d("Downloading %s %s", bucketName, filename);
        // "gsutil cp url... -" will copy the file to stdout.
        CommandResult res =
                RunUtil.getDefault()
                        .runTimedCmdRetry(
                                TIMEOUT,
                                RETRY_INTERVAL,
                                ATTETMPTS,
                                GSUTIL,
                                CP,
                                String.format(GCS_FILENAME_FORMAT, bucketName, filename),
                                "-");
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            throw new IOException(
                    String.format(
                            "Failed to download %s %s with %s.\nstdout: %s\nstderr: %s",
                            bucketName,
                            filename,
                            res.getStatus(),
                            res.getStdout(),
                            res.getStderr()));
        }
        return new ByteArrayInputStream(res.getStdout().getBytes());
    }

    void checkGSUtil() throws IOException {
        CommandResult res =
                RunUtil.getDefault()
                        .runTimedCmdRetry(TIMEOUT, RETRY_INTERVAL, ATTETMPTS, GSUTIL, "-v");
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            throw new IOException(
                    "gsutil is not installed.\n"
                            + "https://cloud.google.com/storage/docs/gsutil for instructions.");
        }
    }
}
