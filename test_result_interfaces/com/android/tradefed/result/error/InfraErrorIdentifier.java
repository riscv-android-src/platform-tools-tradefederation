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
    // 10_001 - 10_500: General errors
    ARTIFACT_NOT_FOUND(10_001),
    FAIL_TO_CREATE_FILE(10_002),

    // 10_501 - 11_000: Build, Artifacts download related errors
    ARTIFACT_REMOTE_PATH_NULL(10_501),
    ARTIFACT_UNSUPPORTED_PATH(10_502),
    ARTIFACT_DOWNLOAD_ERROR(10_503),

    UNDETERMINED(20_000);

    private final int code;

    InfraErrorIdentifier(int code) {
        this.code = code;
    }

    @Override
    public int code() {
        return code;
    }
}
