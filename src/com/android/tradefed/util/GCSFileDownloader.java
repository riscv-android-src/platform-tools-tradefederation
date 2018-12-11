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
import com.android.tradefed.util.GCSBucketUtil.GCSFileMetadata;

import com.google.common.annotations.VisibleForTesting;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File downloader to download file from google cloud storage (GCS). */
public class GCSFileDownloader implements IFileDownloader {
    public static final String GCS_PREFIX = "gs://";
    public static final String GCS_APPROX_PREFIX = "gs:/";

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
        if (remoteFilePath.startsWith(GCS_APPROX_PREFIX)
                && !remoteFilePath.startsWith(GCS_PREFIX)) {
            // File object remove double // so we have to rebuild it in some cases
            remoteFilePath = remoteFilePath.replaceAll(GCS_APPROX_PREFIX, GCS_PREFIX);
        }
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
        String[] pathParts = parseGcsPath(remotePath);
        downloadFile(pathParts[0], pathParts[1], destFile);
    }

    @Override
    public boolean isFresh(File localFile, String remotePath) throws BuildRetrievalError {
        String[] pathParts = parseGcsPath(remotePath);
        try {
            return recursiveCheckFreshness(localFile, pathParts[0], Paths.get(pathParts[1]));
        } catch (IOException e) {
            throw new BuildRetrievalError(e.getMessage(), e);
        }
    }

    String[] parseGcsPath(String remotePath) throws BuildRetrievalError {
        Matcher m = GCS_PATH_PATTERN.matcher(remotePath);
        if (!m.find()) {
            throw new BuildRetrievalError(
                    String.format("Only GCS path is supported, %s is not supported", remotePath));
        }
        return new String[] {m.group(1), m.group(2)};
    }

    /**
     * For GCS, if it's a file, we use file content's md5 hash to check if the local file is the
     * same as the remote file. If it's a folder, we will check all the files in the folder are the
     * same and all the sub-folders also have the same files.
     *
     * @param localFile is the local file
     * @param bucketName is the remote file's GCS bucket name
     * @param remotePath is the relative path to the bucket.
     * @return true if local file is the same as remote file, otherwise false.
     * @throws IOException
     */
    private boolean recursiveCheckFreshness(File localFile, String bucketName, Path remotePath)
            throws IOException {
        GCSBucketUtil bucketUtil = getGCSBucketUtil(bucketName);
        if (localFile.isFile()) {
            GCSFileMetadata fileInfo = bucketUtil.stat(remotePath);
            boolean isFileFresh = fileInfo.mMd5Hash.equals(bucketUtil.md5Hash(localFile));
            if (!isFileFresh) {
                CLog.d("Local file for %s is not fresh.", remotePath);
            }
            return isFileFresh;
        } else if (localFile.isDirectory()) {
            Set<String> remoteUriSets = new HashSet<String>(bucketUtil.ls(remotePath));
            String remoteUri = sanitizeDirectoryName(bucketUtil.getUriForGcsPath(remotePath));
            // If the folder has files inside it, "ls" will include the folder itself.
            // If the folder only has folders or has nothing inside it, "ls" will not include the
            // folder itself. That said depends on folder's content, "ls" may or may not list the
            // current folder. Since the current folder should always exists (otherwise the "ls"
            // already throws exception), we don't bother to check it is in the "ls" result or not.
            remoteUriSets.remove(remoteUri);

            for (File subFile : localFile.listFiles()) {
                Path remoteSubPath = remotePath.resolve(subFile.getName());
                String remoteSubUri = bucketUtil.getUriForGcsPath(remoteSubPath);
                if (subFile.isDirectory()) {
                    remoteSubUri = sanitizeDirectoryName(remoteSubUri);
                }
                if (!remoteUriSets.contains(remoteSubUri)) {
                    CLog.d("GCS doesn't have %s.", remoteSubUri);
                    return false;
                }
                remoteUriSets.remove(remoteSubUri);
            }
            if (!remoteUriSets.isEmpty()) {
                CLog.d("GCS has these files but local doesn't: %s", remoteUriSets);
                return false;
            }
            for (File subFile : localFile.listFiles()) {
                if (!recursiveCheckFreshness(
                        subFile, bucketName, remotePath.resolve(subFile.getName()))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** Folder name should end with "/" */
    String sanitizeDirectoryName(String name) {
        if (!name.endsWith(PATH_SEP)) {
            name += PATH_SEP;
        }
        return name;
    }

    @VisibleForTesting
    void downloadFile(String bucketName, String filename, File localFile)
            throws BuildRetrievalError {
        CLog.i("Downloading %s %s to %s", bucketName, filename, localFile.getAbsolutePath());

        GCSBucketUtil bucketUtil = getGCSBucketUtil(bucketName);
        try {
            if (!bucketUtil.isFile(filename)) {
                filename = sanitizeDirectoryName(filename);
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
