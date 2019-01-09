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

import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File downloader to download file from google cloud storage (GCS). */
public class GCSFileDownloader implements IFileDownloader {
    public static final String GCS_PREFIX = "gs://";
    public static final String GCS_APPROX_PREFIX = "gs:/";

    private static final Pattern GCS_PATH_PATTERN = Pattern.compile("gs://([^/]*)/(.*)");
    private static final String PATH_SEP = "/";

    private File mJsonKeyFile = null;
    private Storage mStorage;

    public GCSFileDownloader(File jsonKeyFile) {
        mJsonKeyFile = jsonKeyFile;
    }

    public GCSFileDownloader() {}

    /**
     * Download a file from a GCS bucket file.
     *
     * @param bucketName GCS bucket name
     * @param filename the filename
     * @return {@link InputStream} with the file content.
     */
    public InputStream downloadFile(String bucketName, String filename) throws IOException {
        try {
            Blob blob = getBucket(bucketName).get(filename);
            if (blob == null) {
                throw new IOException(
                        String.format("gs://%s/%s doesn't exist.", bucketName, filename));
            }
            return Channels.newInputStream(blob.reader());
        } catch (StorageException e) {
            throw new IOException(e);
        }
    }

    Storage getStorage() throws IOException {
        if (mStorage == null) {
            Credentials credential = null;
            if (mJsonKeyFile != null && mJsonKeyFile.exists()) {
                CLog.d("Using json key file %s.", mJsonKeyFile);
                credential =
                        ServiceAccountCredentials.fromStream(new FileInputStream(mJsonKeyFile));
            } else {
                CLog.d("Using local authentication.");
                try {
                    credential = UserCredentials.getApplicationDefault();
                } catch (IOException e) {
                    CLog.e(e.getMessage());
                    CLog.e("Try 'gcloud auth application-default login' to login.");
                    throw e;
                }
            }
            mStorage = StorageOptions.newBuilder().setCredentials(credential).build().getService();
        }
        return mStorage;
    }

    Bucket getBucket(String bucketName) throws IOException, StorageException {
        Bucket bucket = getStorage().get(bucketName);
        if (bucket == null) {
            throw new IOException(String.format("Bucket %s doesn't exist.", bucketName));
        }
        return bucket;
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
        String[] pathParts = parseGcsPath(remotePath);
        downloadFile(pathParts[0], pathParts[1], destFile);
    }


    private boolean isFileFresh(File localFile, Blob remoteFile) throws IOException {
        if (localFile == null && remoteFile == null) {
            return true;
        }
        if (localFile == null || remoteFile == null) {
            return false;
        }
        return remoteFile.getMd5().equals(FileUtil.calculateBase64Md5(localFile));
    }

    @Override
    public boolean isFresh(File localFile, String remotePath) throws BuildRetrievalError {
        String[] pathParts = parseGcsPath(remotePath);
        try {
            return recursiveCheckFolderFreshness(getBucket(pathParts[0]), pathParts[1], localFile);
        } catch (IOException | StorageException e) {
            throw new BuildRetrievalError(e.getMessage(), e);
        }
    }

    /**
     * For GCS, if it's a file, we use file content's md5 hash to check if the local file is the
     * same as the remote file. If it's a folder, we will check all the files in the folder are the
     * same and all the sub-folders also have the same files.
     *
     * @param bucket is the gcs bucket.
     * @param remoteFilename is the relative path to the bucket.
     * @param localFile is the local file
     * @return true if local file is the same as remote file, otherwise false.
     * @throws IOException
     * @throws StorageException
     */
    private boolean recursiveCheckFolderFreshness(
            Bucket bucket, String remoteFilename, File localFile)
            throws IOException, StorageException {
        if (!localFile.exists()) {
            return false;
        }
        if (localFile.isFile()) {
            return isFileFresh(localFile, bucket.get(remoteFilename));
        }
        // localFile is a folder.
        Set<String> subFilenames = new HashSet<>(Arrays.asList(localFile.list()));
        remoteFilename = sanitizeDirectoryName(remoteFilename);

        for (Blob subRemoteFile : listRemoteFilesUnderFolder(bucket, remoteFilename)) {
            if (subRemoteFile.getName().equals(remoteFilename)) {
                // Skip the current folder.
                continue;
            }
            String subFilename = Paths.get(subRemoteFile.getName()).getFileName().toString();
            if (!recursiveCheckFolderFreshness(
                    bucket, subRemoteFile.getName(), new File(localFile, subFilename))) {
                return false;
            }
            subFilenames.remove(subFilename);
        }
        return subFilenames.isEmpty();
    }

    Iterable<Blob> listRemoteFilesUnderFolder(Bucket bucket, String folder) {
        return bucket.list(
                        BlobListOption.prefix(sanitizeDirectoryName(folder)),
                        BlobListOption.currentDirectory())
                .iterateAll();
    }

    String[] parseGcsPath(String remotePath) throws BuildRetrievalError {
        if (remotePath.startsWith(GCS_APPROX_PREFIX) && !remotePath.startsWith(GCS_PREFIX)) {
            // File object remove double // so we have to rebuild it in some cases
            remotePath = remotePath.replaceAll(GCS_APPROX_PREFIX, GCS_PREFIX);
        }
        Matcher m = GCS_PATH_PATTERN.matcher(remotePath);
        if (!m.find()) {
            throw new BuildRetrievalError(
                    String.format("Only GCS path is supported, %s is not supported", remotePath));
        }
        return new String[] {m.group(1), m.group(2)};
    }

    String sanitizeDirectoryName(String name) {
        /** Folder name should end with "/" */
        if (!name.endsWith(PATH_SEP)) {
            name += PATH_SEP;
        }
        return name;
    }

    /** check given filename is a folder or not. */
    private boolean isFolder(Bucket bucket, String filename) throws StorageException {
        filename = sanitizeDirectoryName(filename);
        return bucket.list(BlobListOption.prefix(filename), BlobListOption.currentDirectory())
                .iterateAll()
                .iterator()
                .hasNext();
    }

    @VisibleForTesting
    void downloadFile(String bucketName, String filename, File localFile)
            throws BuildRetrievalError {
        try {
            recursiveDownload(getStorage().get(bucketName), filename, localFile);
        } catch (IOException | StorageException e) {
            CLog.e("Failed to download gs://%s/%s, clean up.", bucketName, filename);
            throw new BuildRetrievalError(e.getMessage(), e);
        }
    }

    private void recursiveDownload(Bucket bucket, String filepath, File localFile)
            throws StorageException, IOException {
        CLog.d(
                "Downloading gs://%s/%s to %s",
                bucket.getName(), filepath, localFile.getAbsolutePath());
        if (!isFolder(bucket, filepath)) {
            Blob blob = bucket.get(filepath);
            if (blob == null) {
                throw new IOException(
                        String.format("gs://%s/%s doesn't exist.", bucket.getName(), filepath));
            }
            blob.downloadTo(localFile.toPath());
            return;
        }
        // Remote file is a folder.
        filepath = sanitizeDirectoryName(filepath);
        if (!localFile.exists()) {
            FileUtil.mkdirsRWX(localFile);
        }
        Set<String> subFilenames = new HashSet<>(Arrays.asList(localFile.list()));
        for (Blob subRemoteFile : listRemoteFilesUnderFolder(bucket, filepath)) {
            if (subRemoteFile.getName().equals(filepath)) {
                // Skip the current folder.
                continue;
            }
            String subFilename = Paths.get(subRemoteFile.getName()).getFileName().toString();
            recursiveDownload(bucket, subRemoteFile.getName(), new File(localFile, subFilename));
            subFilenames.remove(subFilename);
        }
        for (String subFilename : subFilenames) {
            FileUtil.recursiveDelete(new File(localFile, subFilename));
        }
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
