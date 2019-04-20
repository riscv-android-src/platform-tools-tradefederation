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
 * limitations under the License.
 */
package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;

/**
 * Base class for Gcs operation like download and upload. {@link GCSFileDownloader} and {@link
 * GCSFileUploader}.
 */
public abstract class GCSCommon {

    protected static final int DEFAULT_TIMEOUT = 10 * 60 * 1000; // 10minutes

    private File mJsonKeyFile = null;
    private Storage mStorage;

    public GCSCommon(File jsonKeyFile) {
        mJsonKeyFile = jsonKeyFile;
    }

    public GCSCommon() {}

    protected Storage getStorage(Collection<String> scopes) throws IOException {
        GoogleCredential credential = null;
        try {
            if (mStorage == null) {
                if (mJsonKeyFile != null && mJsonKeyFile.exists()) {
                    CLog.d("Using json key file %s.", mJsonKeyFile);
                    credential =
                            GoogleApiClientUtil.createCredentialFromJsonKeyFile(
                                    mJsonKeyFile, scopes);
                } else {
                    CLog.d("Using local authentication.");
                    try {
                        credential = GoogleCredential.getApplicationDefault().createScoped(scopes);
                    } catch (IOException e) {
                        CLog.e(e.getMessage());
                        CLog.e(
                                "Try 'gcloud auth application-default login' to login for "
                                        + "personal account; Or 'export "
                                        + "GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json' "
                                        + "for service account.");
                        throw e;
                    }
                }
                mStorage =
                        new Storage.Builder(
                                        GoogleNetHttpTransport.newTrustedTransport(),
                                        JacksonFactory.getDefaultInstance(),
                                        GoogleApiClientUtil.configureRetryStrategy(
                                                GoogleApiClientUtil.setHttpTimeout(
                                                        credential,
                                                        DEFAULT_TIMEOUT,
                                                        DEFAULT_TIMEOUT)))
                                .setApplicationName(GoogleApiClientUtil.APP_NAME)
                                .build();
            }
            return mStorage;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }
}
