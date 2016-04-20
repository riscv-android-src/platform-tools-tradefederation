/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tradefed.util.keystore;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * A sample implementation where a local JSON file acts a key store. The JSON
 * text file should have key to value in string format.
 */
public class JSONFileKeyStoreClient implements IKeyStoreClient {
    // JSON file from where to read the key store.
    private File mJsonFile;
    // JSON key store read from the JSON file.
    protected JSONObject mJsonKeyStore = null;

    /**
     * Default Ctor; used for testing.
     */
    JSONFileKeyStoreClient() {
        mJsonFile = null;
    }

    public JSONFileKeyStoreClient(File jsonFile) {
        mJsonFile = jsonFile;
    }

    /**
     * Helper method to lazily load the JSON key store.
     *
     * @return the JSONObject of the key store.
     * @throws KeyStoreException
     */
    protected JSONObject getKeyStore() throws KeyStoreException {
        if (mJsonKeyStore == null) {
            if (mJsonFile == null) {
                throw new KeyStoreException("JSON key store file not set.");
            }
            if (!mJsonFile.canRead()) {
                throw new KeyStoreException(
                        String.format("Unable to read the JSON key store file %s",
                                mJsonFile.toString()));
            }
            try {
                String data = FileUtil.readStringFromFile(mJsonFile);
                mJsonKeyStore = new JSONObject(data);
            } catch (IOException e) {
                throw new KeyStoreException(String.format("Failed to read JSON key file %s: %s",
                        mJsonFile.toString(), e));
            } catch (JSONException e) {
                throw new KeyStoreException(
                        String.format("Failed to parse JSON data from file %s with exception: %s",
                                mJsonFile.toString(), e));
            }
        }
        return mJsonKeyStore;
    }

    @Override
    public boolean isAvailable() {
        if (mJsonFile == null) {
            CLog.w("Null file specified for key store.");
            return false;
        }
        return mJsonFile.canRead();
    }

    @Override
    public boolean containsKey(String key) {
        try {
            if (getKeyStore() == null) {
                CLog.w("Key Store is null");
                return false;
            }
            return getKeyStore().has(key);
        } catch (KeyStoreException e) {
            CLog.e("Key store error while trying to fetch key %s: %s", key, e);
            return false;
        }
    }

    @Override
    public String fetchKey(String key) {
        if (key == null) {
            CLog.w("null key passed");
            return null;
        }
        try {
            return getKeyStore().getString(key);
        } catch (JSONException | KeyStoreException e) {
            CLog.e("Failed to fetch key '%s' inside JSON key store: %s", key, e);
            return null;
        }
    }

    /**
     * Helper method used to set key store. Used for testing.
     *
     * @param keyStore {@link JSONObject} to use as key store.
     */
    public void setKeyStore(JSONObject keyStore) {
        mJsonKeyStore = keyStore;
    }

}
