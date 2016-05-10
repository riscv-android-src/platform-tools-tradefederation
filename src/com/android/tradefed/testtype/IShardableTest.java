/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.testtype;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Collection;

/**
 * A {@link IRemoteTest} that can be split into separately executable sub-tests.
 */
public interface IShardableTest extends IRemoteTest {
    // TODO: Remove split() once all child classes migrate to {@link #split(int)}.

    /**
     * Shard the test into separately runnable chunks.
     * <p/>
     * This will be called before test execution, so injected dependencies (such as the
     * {@link ITestDevice} for {@link IDeviceTest}s) may be null.
     * <p/>
     * This method is deprecated. Classes should not implement this method.
     *
     * @return a collection of subtests to be executed separately or <code>null</code> if test is
     *         not currently shardable
     */
    @Deprecated
    default public Collection<IRemoteTest> split() {
        throw new UnsupportedOperationException();
    }

    /**
     * Shard the test into the given number of separately runnable chunks.
     * <p/>
     * This will be called before test execution, so injected dependencies (such as the
     * {@link ITestDevice} for {@link IDeviceTest}s) may be null.
     * The sharding logic must be constant and return an identical list of tests for a same build
     * and a same shard count.
     *
     * @param shardCount a number of shards.
     * @return a collection of subtests to be executed separately or <code>null</code> if test is
     *         not currently shardable
     */
    default public Collection<IRemoteTest> split(int shardCount) {
        CLog.w("This test does not support splitting with a shard count; falling back to legacy" +
                "splitting.");
        return split();
    }
}
