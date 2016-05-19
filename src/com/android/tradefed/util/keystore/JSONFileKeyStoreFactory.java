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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.Option.Importance;

import java.io.File;

/**
 * Implementation of a JSON KeyStore Factory, which provides a {@link JSONFileKeyStoreClient} for
 * accessing a JSON Key Store File.
 */
@OptionClass(alias = "json-keystore")
public class JSONFileKeyStoreFactory implements IKeyStoreFactory {
    @Option(name = "json-key-store-file",
            description = "The JSON file from where to read the key store",
            importance = Importance.IF_UNSET)
    private File mJsonFile = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public IKeyStoreClient createKeyStoreClient() throws KeyStoreException {
        return new JSONFileKeyStoreClient(mJsonFile);
    }

}
