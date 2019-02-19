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
package com.android.tradefed.log;

import com.android.ddmlib.Log.LogLevel;

import java.util.concurrent.atomic.AtomicLong;

/** A log entry store's information for one log. */
public class LogEntry implements Comparable<LogEntry> {

    private static final AtomicLong sLogIndex = new AtomicLong(0);
    private final long mTimestamp; // time in milliseconds.
    private final LogLevel mLogLevel;
    private final String mTag;
    private final String mMessage;
    private final long mLogIndex;

    /**
     * Constructor for LogEntry.
     *
     * @param timestamp the currentTimeMillis when the log happen.
     * @param logLevel log level of the log.
     * @param tag log's tag.
     * @param message the log's actual message.
     */
    public LogEntry(long timestamp, LogLevel logLevel, String tag, String message) {
        mTimestamp = timestamp;
        mLogLevel = logLevel;
        mTag = tag;
        mMessage = message;
        mLogIndex = sLogIndex.incrementAndGet();
    }

    public LogEntry(LogLevel logLevel, String tag, String message) {
        this(System.currentTimeMillis(), logLevel, tag, message);
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public LogLevel getLogLevel() {
        return mLogLevel;
    }

    public String getTag() {
        return mTag;
    }

    public String getMessage() {
        return mMessage;
    }

    @Override
    public int compareTo(LogEntry o) {
        if (this.mLogIndex > o.mLogIndex) {
            return 1;
        }
        if (this.mLogIndex == o.mLogIndex) {
            return 0;
        }
        return -1;
    }
}
