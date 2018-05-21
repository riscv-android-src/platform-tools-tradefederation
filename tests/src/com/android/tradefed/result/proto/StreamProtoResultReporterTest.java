/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

/**
 * Unit tests for {@link StreamProtoResultReporter}.
 *
 * <p>TODO: Needs to be completed when the parser is implemented.
 */
@RunWith(JUnit4.class)
public class StreamProtoResultReporterTest {

    private StreamProtoResultReporter mReporter;
    private IInvocationContext mInvocationContext;
    private ITestInvocationListener mMockListener;

    @Before
    public void setUp() {
        mReporter = new StreamProtoResultReporter();
        mInvocationContext = new InvocationContext();
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
    }

    @Test
    public void testStream() throws Exception {
        StreamProtoReceiver receiver = new StreamProtoReceiver(mMockListener);
        OptionSetter setter = new OptionSetter(mReporter);
        try {
            setter.setOptionValue(
                    "proto-report-port", Integer.toString(receiver.getSocketServerPort()));
            EasyMock.replay(mMockListener);
            mReporter.invocationStarted(mInvocationContext);
            // Run modules
            mReporter.testModuleStarted(createModuleContext("arm64 module1"));
            mReporter.testRunStarted("run1", 2);

            TestDescription test1 = new TestDescription("class1", "test1");
            mReporter.testStarted(test1, 5L);
            mReporter.testEnded(test1, 10L, new HashMap<String, Metric>());

            TestDescription test2 = new TestDescription("class1", "test2");
            mReporter.testStarted(test2, 11L);
            mReporter.testFailed(test2, "I failed");
            HashMap<String, Metric> metrics = new HashMap<String, Metric>();
            metrics.put("metric1", TfMetricProtoUtil.stringToMetric("value1"));
            // test log
            mReporter.logAssociation(
                    "log1", new LogFile("path", "url", false, LogDataType.TEXT, 5));

            mReporter.testEnded(test2, 60L, metrics);
            // run log
            mReporter.logAssociation(
                    "run_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
            mReporter.testRunEnded(50L, new HashMap<String, Metric>());

            mReporter.testModuleEnded();
            mReporter.testModuleStarted(createModuleContext("arm32 module1"));
            mReporter.testModuleEnded();
            // Invocation ends
            mReporter.invocationEnded(500L);

            EasyMock.verify(mMockListener);
        } finally {
            receiver.joinReceiver(5000);
            receiver.close();
        }
    }

    /** Helper to create a module context. */
    private IInvocationContext createModuleContext(String moduleId) {
        IInvocationContext context = new InvocationContext();
        context.addInvocationAttribute(ModuleDefinition.MODULE_ID, moduleId);
        return context;
    }
}
