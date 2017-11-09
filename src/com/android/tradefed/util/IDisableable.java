/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.util;

/**
 * Interface that describes a Tradefed object that can be disabled. A disabled object will be
 * skipped and not called at all.
 */
public interface IDisableable {

    /** Returns True if the object should be disabled and skipped. False otherwise. */
    public default boolean isDisabled() {
        return false;
    }

    /**
     * Sets whether or not the object should be disabled or not. Can be use to make an object
     * disabled by default in the default constructor.
     *
     * @param isDisabled the state the object should be put in.
     */
    public default void setDisable(boolean isDisabled) {
        // TODO: Remove the default empty implementation
    }
}
