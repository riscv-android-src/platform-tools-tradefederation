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
package com.android.tradefed.result.proto;

import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.proto.SummaryRecordProto.SummaryRecord;
import com.android.tradefed.util.StreamUtil;

import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Reporters that creates a summary proto from an invocation. The summary contains some minimal
 * informations about the invocation.
 */
public class SummaryProtoResultReporter extends CollectingTestListener {

    @Option(
        name = "summary-proto-output-file",
        description = "File where the proto output will be saved",
        mandatory = true
    )
    private File mOutputFile;

    private Throwable mInvocationFailure = null;
    private long mStartTimeMs;
    private long mEndTimeMs;

    @Override
    public void invocationStarted(IInvocationContext context) {
        mStartTimeMs = System.currentTimeMillis();
        super.invocationStarted(context);
    }

    @Override
    public void invocationFailed(Throwable cause) {
        super.invocationFailed(cause);
        mInvocationFailure = cause;
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        mEndTimeMs = System.currentTimeMillis();
        super.invocationEnded(elapsedTime);
        writeSummaryProto();
    }

    /** Sets the file where to output the result. */
    public void setFileOutput(File output) {
        mOutputFile = output;
    }

    private void writeSummaryProto() {
        if (mOutputFile == null) {
            return;
        }
        SummaryRecord.Builder builder = SummaryRecord.newBuilder();
        // Populate the proto
        builder.setNumExpectedTests(getExpectedTests());
        builder.setNumTotalTests(getNumTotalTests());
        builder.setNumFailedTests(getNumAllFailedTests());
        builder.setNumFailedRuns(getNumAllFailedTestRuns());
        if (mInvocationFailure != null) {
            builder.setErrorMessage(StreamUtil.getStackTrace(mInvocationFailure));
        }
        builder.setStartTime(createTimeStamp(mStartTimeMs));
        builder.setEndTime(createTimeStamp(mEndTimeMs));
        builder.setDescription(Any.pack(getInvocationContext().toProto()));
        // Build and write the proto
        SummaryRecord record = builder.build();
        try {
            record.writeDelimitedTo(new FileOutputStream(mOutputFile));
        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        }
    }

    /** Create and populate Timestamp as recommended in the javadoc of the Timestamp proto. */
    private Timestamp createTimeStamp(long currentTimeMs) {
        return Timestamp.newBuilder()
                .setSeconds(currentTimeMs / 1000)
                .setNanos((int) ((currentTimeMs % 1000) * 1000000))
                .build();
    }
}
