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
package com.android.tradefed.error;

import com.android.tradefed.result.error.ErrorIdentifier;

import java.lang.StackWalker.Option;

/**
 * Base exception class for exception thrown within the harness. This class help carry {@link
 * ErrorIdentifier} to be report the failure details.
 */
public class HarnessException extends Exception {
    static final long serialVersionUID = 100L;

    private final String mOrigin;
    private ErrorIdentifier mErrorId;

    public HarnessException(ErrorIdentifier errorId) {
        super();
        mErrorId = errorId;
        mOrigin =
                StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
                        .getCallerClass()
                        .getCanonicalName();
    }

    public HarnessException(String message, ErrorIdentifier errorId) {
        super(message);
        mErrorId = errorId;
        mOrigin =
                StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
                        .getCallerClass()
                        .getCanonicalName();
    }

    public HarnessException(Throwable cause, ErrorIdentifier errorId) {
        super(cause);
        mErrorId = errorId;
        mOrigin =
                StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
                        .getCallerClass()
                        .getCanonicalName();
    }

    public HarnessException(String message, Throwable cause, ErrorIdentifier errorId) {
        super(message, cause);
        mErrorId = errorId;
        mOrigin =
                StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
                        .getCallerClass()
                        .getCanonicalName();
    }

    /** Returns the {@link ErrorIdentifier} associated with the exception. Can be null. */
    public ErrorIdentifier getErrorId() {
        return mErrorId;
    }

    /** Returns the origin of the exception. */
    public String getOrigin() {
        return mOrigin;
    }
}