/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.result.error;

/** Error Identifiers from Trade Federation infra, and dependent infra (like Build infra). */
public enum InfraErrorIdentifier implements ErrorIdentifier {

    // ********************************************************************************************
    // Infra: 10_001 ~ 20_000
    // ********************************************************************************************
    UNDETERMINED(20_000);

    private final long code;

    InfraErrorIdentifier(int code) {
        this.code = code;
    }

    @Override
    public long code() {
        return code;
    }
}
