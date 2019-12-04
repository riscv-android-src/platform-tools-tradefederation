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
package com.android.tradefed.invoker;

/**
 * Holder object that contains all the information and dependencies a test runner or test might need
 * to execute properly.
 */
public class TestInformation {

    private final IInvocationContext mContext;

    private TestInformation(Builder builder) {
        mContext = builder.mContext;
    }

    /** Create a builder for creating {@link TestInformation} instances. */
    public static Builder newBuilder() {
        return new Builder();
    }

    /** Returns the current invocation context, or the module context if this is a module. */
    public IInvocationContext getContext() {
        return mContext;
    }

    /** Builder to create a {@link TestInformation} instance. */
    public static class Builder {
        private IInvocationContext mContext;

        private Builder() {}

        public TestInformation build() {
            return new TestInformation(this);
        }

        public Builder setInvocationContext(IInvocationContext context) {
            this.mContext = context;
            return this;
        }
    }
}
