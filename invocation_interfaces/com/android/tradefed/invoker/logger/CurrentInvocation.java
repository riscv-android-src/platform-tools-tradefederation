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
package com.android.tradefed.invoker.logger;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A class that tracks and provides the current invocation information useful anywhere inside the
 * invocation.
 */
public class CurrentInvocation {

    /** Some special named key that we will always populate for the invocation. */
    public enum InvocationInfo {
        WORK_FOLDER("work_folder");

        private final String mKeyName;

        private InvocationInfo(String key) {
            mKeyName = key;
        }

        @Override
        public String toString() {
            return mKeyName;
        }
    }

    private CurrentInvocation() {}

    /**
     * Track info per ThreadGroup as a proxy to invocation since an invocation run within one
     * threadgroup.
     */
    private static final Map<ThreadGroup, Map<InvocationInfo, File>> mPerGroupInfo =
            Collections.synchronizedMap(new HashMap<ThreadGroup, Map<InvocationInfo, File>>());

    /**
     * Add one key-value to be tracked at the invocation level.
     *
     * @param key The key under which the invocation info will be tracked.
     * @param value The value of the invocation metric.
     */
    public static void addInvocationInfo(InvocationInfo key, File value) {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        synchronized (mPerGroupInfo) {
            if (mPerGroupInfo.get(group) == null) {
                mPerGroupInfo.put(group, new HashMap<>());
            }
            mPerGroupInfo.get(group).put(key, value);
        }
    }

    /** Returns the Map of invocation metrics for the invocation in progress. */
    public static File getInfo(InvocationInfo key) {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        synchronized (mPerGroupInfo) {
            if (mPerGroupInfo.get(group) == null) {
                mPerGroupInfo.put(group, new HashMap<>());
            }
            return mPerGroupInfo.get(group).get(key);
        }
    }

    /** Clear the invocation info for an invocation. */
    public static void clearInvocationInfos() {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        mPerGroupInfo.remove(group);
    }
}
