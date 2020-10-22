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

import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;

import javax.annotation.Nonnull;

/** Error identifier from tests and tests runners. */
public enum TestErrorIdentifier implements ErrorIdentifier {
    MODULE_DID_NOT_EXECUTE(530_001, FailureStatus.NOT_EXECUTED),
    INSTRUMENTATION_NULL_METHOD(530_002, FailureStatus.TEST_FAILURE);

    private final long code;
    private final @Nonnull FailureStatus status;

    TestErrorIdentifier(int code, FailureStatus status) {
        this.code = code;
        this.status = (status == null ? FailureStatus.UNSET : status);
    }

    @Override
    public long code() {
        return code;
    }

    @Override
    public @Nonnull FailureStatus status() {
        return status;
    }
}
