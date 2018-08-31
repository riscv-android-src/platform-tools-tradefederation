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
package com.android.tradefed.build;

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.util.GCSFileDownloader;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Download test resource from GCS. */
public class GCSTestResourceProvider implements IBuildProvider {

    @Option(
            name = "test-resource",
            description =
                    "GCS files as test resources that are required for the test."
                            + "Key is the identity of the test resource."
                            + "Value is a gs://bucket/path/to/file format GCS path.")
    private Map<String, String> mTestResources = new HashMap<>();

    private IBuildInfo mBuildInfo;
    private IFileDownloader mFileDownloader = null;

    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        mBuildInfo = new BuildInfo();
        mBuildInfo.setTestResourceBuild(true);
        fetchTestResources();
        return mBuildInfo;
    }

    private void fetchTestResources() throws BuildRetrievalError {
        for (Map.Entry<String, String> entry : mTestResources.entrySet()) {
            fetchTestResource(entry.getKey(), entry.getValue());
        }
    }

    private void fetchTestResource(String key, String value) throws BuildRetrievalError {
        File localFile = getGCSFileDownloader().downloadFile(value);
        mBuildInfo.setFile(key, localFile, "");
    }

    @Override
    public void buildNotTested(IBuildInfo info) {
        // Ignored.
    }

    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }

    @VisibleForTesting
    IFileDownloader getGCSFileDownloader() {
        if (mFileDownloader == null) {
            mFileDownloader =
                    new FileDownloadCacheWrapper(
                            getHostOptions().getDownloadCacheDir(), new GCSFileDownloader());
        }
        return mFileDownloader;
    }

    /**
     * Gets the {@link IHostOptions} instance to use.
     *
     * <p>Exposed for unit testing
     */
    @VisibleForTesting
    IHostOptions getHostOptions() {
        return GlobalConfiguration.getInstance().getHostOptions();
    }
}
