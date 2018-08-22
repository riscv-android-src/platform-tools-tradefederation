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
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.annotations.VisibleForTesting;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File downloader to download file from google cloud storage (GCS). */
public class GCSFileDownloader implements IFileDownloader {
    private static final long TIMEOUT = 10 * 60 * 1000; // 10minutes
    private static final long RETRY_INTERVAL = 1000; // 1s
    private static final int ATTETMPTS = 3;
    private static final Pattern GCS_PATH_PATTERN = Pattern.compile("gs://([^/]*)(/.*)");
    private static final String PATH_SEP = "/";

    /**
     * Download a file from a GCS bucket file.
     *
     * @param bucketName GCS bucket name
     * @param filename the filename
     * @return {@link InputStream} with the file content.
     */
    public InputStream downloadFile(String bucketName, String filename) throws IOException {
        GCSBucketUtil bucket = getGCSBucketUtil(bucketName);
        Path path = Paths.get(filename);
        String contents = bucket.pullContents(path);
        return new ByteArrayInputStream(contents.getBytes());
    }

    /**
     * Download file from GCS.
     *
     * <p>Right now only support GCS path.
     *
     * @param remoteFilePath gs://bucket/file/path format GCS path.
     * @return local file
     * @throws BuildRetrievalError
     */
    @Override
    public File downloadFile(String remoteFilePath) throws BuildRetrievalError {
        File destFile = createTempFile(remoteFilePath, null);
        try {
            downloadFile(remoteFilePath, destFile);
            return destFile;
        } catch (BuildRetrievalError e) {
            FileUtil.recursiveDelete(destFile);
            throw e;
        }
    }

    @Override
    public void downloadFile(String remotePath, File destFile) throws BuildRetrievalError {
        Matcher m = GCS_PATH_PATTERN.matcher(remotePath);
        if (!m.find()) {
            throw new BuildRetrievalError(
                    String.format("Only GCS path is supported, %s is not supported", remotePath));
        }
        String bucket = m.group(1);
        String path = m.group(2);
        downloadFile(bucket, path, destFile);
    }

    @VisibleForTesting
    void downloadFile(String bucketName, String filename, File localFile)
            throws BuildRetrievalError {
        CLog.i("Downloading %s %s to %s", bucketName, filename, localFile.getAbsolutePath());

        GCSBucketUtil bucketUtil = getGCSBucketUtil(bucketName);
        try {
            if (!bucketUtil.isFile(filename)) {
                if (!filename.endsWith(PATH_SEP)) {
                    filename += PATH_SEP;
                }
                filename += "*";
                localFile.mkdirs();
                bucketUtil.setRecursive(true);
            }
            bucketUtil.pull(Paths.get(filename), localFile);
        } catch (IOException e) {
            CLog.e("Failed to download %s, clean up.", localFile.getAbsoluteFile());
            throw new BuildRetrievalError(e.getMessage(), e);
        }
    }

    private GCSBucketUtil getGCSBucketUtil(String bucketName) {
        GCSBucketUtil bucketUtil = new GCSBucketUtil(bucketName);
        bucketUtil.setTimeoutMs(TIMEOUT);
        bucketUtil.setRetryInterval(RETRY_INTERVAL);
        bucketUtil.setAttempts(ATTETMPTS);
        return bucketUtil;
    }

    /**
     * Creates a unique file on temporary disk to house downloaded file with given path.
     *
     * <p>Constructs the file name based on base file name from path
     *
     * @param remoteFilePath the remote path to construct the name from
     */
    @VisibleForTesting
    File createTempFile(String remoteFilePath, File rootDir) throws BuildRetrievalError {
        try {
            // create a unique file.
            File tmpFile = FileUtil.createTempFileForRemote(remoteFilePath, rootDir);
            // now delete it so name is available
            tmpFile.delete();
            return tmpFile;
        } catch (IOException e) {
            String msg = String.format("Failed to create tmp file for %s", remoteFilePath);
            throw new BuildRetrievalError(msg, e);
        }
    }
}
