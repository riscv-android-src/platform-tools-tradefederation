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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.proto.SummaryRecordProto.SummaryRecord;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.HashMap;

/** Unit tests for {@link SummaryProtoResultReporter}. */
@RunWith(JUnit4.class)
public class SummaryProtoResultReporterTest {

    private SummaryProtoResultReporter mReporter;
    private File mOutputFile;
    private IInvocationContext mContext;

    @Before
    public void setUp() throws Exception {
        mOutputFile = FileUtil.createTempFile("test-summary-proto", ".pb");
        mReporter = new SummaryProtoResultReporter();
        mReporter.setFileOutput(mOutputFile);

        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, new BuildInfo());
        mContext.setConfigurationDescriptor(new ConfigurationDescriptor());
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mOutputFile);
    }

    @Test
    public void testReportSummary() throws Exception {
        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted("run1", 5);
        mReporter.testRunEnded(5L, new HashMap<String, Metric>());
        mReporter.invocationEnded(500L);

        assertTrue(FileUtil.readStringFromFile(mOutputFile).length() > 50);

        SummaryRecord summary = SummaryProtoParser.readFromFile(mOutputFile);
        assertEquals(5, summary.getNumExpectedTests());
        assertEquals(0, summary.getNumTotalTests());
    }
}
