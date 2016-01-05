/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * Interface to dispatch host data
 */
public interface IHostMonitor {

    /**
     * A method that will be called after all of the Monitor's @Option fields have been set.
     */
    public void start();

    /**
     * A method that will be called to add a special event to be sent.
     */
    public void addHostEvent(String tag, DataPoint event);

    /**
     * A method that will be called to stop the Host Monitor.
     */
    public void terminate();

    /**
     * Generic class for data to be reported.
     */
    static class DataPoint {
        public String name;
        public int value;

        public DataPoint(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "dataPoint [name=" + name + ", value=" + value + "]";
        }
    }
}
