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
package com.android.tradefed.result;

import com.android.tradefed.result.proto.TestRecordProto;

import javax.annotation.Nullable;

/**
 * The class describing a failure information in Trade Federation. This class contains the debugging
 * information and context of the failure that helps understanding the issue.
 */
public class FailureDescription {
    // The error message generated from the failure
    private String mErrorMessage;
    // Optional: The category of the failure
    private @Nullable TestRecordProto.FailureStatus mFailureStatus =
            TestRecordProto.FailureStatus.UNSET;

    FailureDescription() {}

    /**
     * Set the {@link com.android.tradefed.result.proto.TestRecordProto.FailureStatus} associated
     * with the failure.
     */
    public FailureDescription setFailureStatus(TestRecordProto.FailureStatus status) {
        mFailureStatus = status;
        return this;
    }

    /** Returns the FailureStatus associated with the failure. Can be null. */
    public @Nullable TestRecordProto.FailureStatus getFailureStatus() {
        if (TestRecordProto.FailureStatus.UNSET.equals(mFailureStatus)) {
            return null;
        }
        return mFailureStatus;
    }

    /** Returns the error message associated with the failure. */
    public String getErrorMessage() {
        return mErrorMessage;
    }

    @Override
    public String toString() {
        // For backward compatibility of result interface, toString falls back to the simple message
        return mErrorMessage;
    }

    /**
     * Create a {@link FailureDescription} based on the error message generated from the failure.
     *
     * @param errorMessage The error message from the failure.
     * @return the created {@link FailureDescription}
     */
    public static FailureDescription create(String errorMessage) {
        return create(errorMessage, null);
    }

    /**
     * Create a {@link FailureDescription} based on the error message generated from the failure.
     *
     * @param errorMessage The error message from the failure.
     * @param status The status associated with the failure.
     * @return the created {@link FailureDescription}
     */
    public static FailureDescription create(
            String errorMessage, @Nullable TestRecordProto.FailureStatus status) {
        FailureDescription info = new FailureDescription();
        info.mErrorMessage = errorMessage;
        info.mFailureStatus = status;
        return info;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mErrorMessage == null) ? 0 : mErrorMessage.hashCode());
        result = prime * result + ((mFailureStatus == null) ? 0 : mFailureStatus.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        FailureDescription other = (FailureDescription) obj;
        if (mErrorMessage == null) {
            if (other.mErrorMessage != null) return false;
        } else if (!mErrorMessage.equals(other.mErrorMessage)) return false;
        if (mFailureStatus != other.mFailureStatus) return false;
        return true;
    }
}
