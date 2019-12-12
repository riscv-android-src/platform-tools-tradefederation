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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;

import java.util.List;

/**
 * Holder object that contains all the information and dependencies a test runner or test might need
 * to execute properly.
 */
public class TestInformation {
    /** The context of the invocation or module in progress */
    private final IInvocationContext mContext;
    /** Properties generated during execution. */
    private final ExecutionProperties mProperties;

    private TestInformation(Builder builder) {
        mContext = builder.mContext;
        mProperties = builder.mProperties;
    }

    private TestInformation(TestInformation invocationInfo, IInvocationContext moduleContext) {
        mContext = moduleContext;
        mProperties = invocationInfo.mProperties;
    }

    /** Create a builder for creating {@link TestInformation} instances. */
    public static Builder newBuilder() {
        return new Builder();
    }

    /** Create an {@link TestInformation} representing a module rather than an invocation. */
    public static TestInformation createModuleTestInfo(
            TestInformation invocationInfo, IInvocationContext moduleContext) {
        return new TestInformation(invocationInfo, moduleContext);
    }

    /** Returns the current invocation context, or the module context if this is a module. */
    public IInvocationContext getContext() {
        return mContext;
    }

    /** Returns the primary device under tests. */
    public ITestDevice getDevice() {
        return mContext.getDevices().get(0);
    }

    /** Returns the list of devices part of the invocation. */
    public List<ITestDevice> getDevices() {
        return mContext.getDevices();
    }

    /** Returns the primary device build information. */
    public IBuildInfo getBuildInfo() {
        return mContext.getBuildInfos().get(0);
    }

    /**
     * Returns the properties generated during the invocation execution. Passing values and
     * information through the {@link ExecutionProperties} is the recommended way to exchange
     * information between target_preparers and tests.
     */
    public ExecutionProperties properties() {
        return mProperties;
    }

    /** Builder to create a {@link TestInformation} instance. */
    public static class Builder {
        private IInvocationContext mContext;
        private ExecutionProperties mProperties;

        private Builder() {
            mProperties = new ExecutionProperties();
        }

        public TestInformation build() {
            return new TestInformation(this);
        }

        public Builder setInvocationContext(IInvocationContext context) {
            this.mContext = context;
            return this;
        }
    }
}
