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

import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

/** Unit tests for {@link ProtoResultParser}. */
@RunWith(JUnit4.class)
public class ProtoResultParserTest {

    private ProtoResultParser mParser;
    private ITestInvocationListener mMockListener;
    private TestProtoParser mTestParser;
    private IInvocationContext mInvocationContext;

    private class TestProtoParser extends ProtoResultReporter {

        @Override
        public void processStartInvocation(TestRecord invocationStartRecord) {
            mParser.processNewProto(invocationStartRecord);
        }

        @Override
        public void processFinalProto(TestRecord finalRecord) {
            mParser.processNewProto(finalRecord);
        }

        @Override
        public void processTestModuleStarted(TestRecord moduleStartRecord) {
            mParser.processNewProto(moduleStartRecord);
        }

        @Override
        public void processTestModuleEnd(TestRecord moduleRecord) {
            mParser.processNewProto(moduleRecord);
        }

        @Override
        public void processTestRunStarted(TestRecord runStartedRecord) {
            mParser.processNewProto(runStartedRecord);
        }

        @Override
        public void processTestRunEnded(TestRecord runRecord) {
            mParser.processNewProto(runRecord);
        }

        @Override
        public void processTestCaseStarted(TestRecord testCaseStartedRecord) {
            mParser.processNewProto(testCaseStartedRecord);
        }

        @Override
        public void processTestCaseEnded(TestRecord testCaseRecord) {
            mParser.processNewProto(testCaseRecord);
        }
    }

    @Before
    public void setUp() {
        mMockListener = EasyMock.createStrictMock(ITestInvocationListener.class);
        mParser = new ProtoResultParser(mMockListener);
        mTestParser = new TestProtoParser();
        mInvocationContext = new InvocationContext();
        mInvocationContext.setConfigurationDescriptor(new ConfigurationDescriptor());
    }

    @Test
    public void testEvents() {
        TestDescription test1 = new TestDescription("class1", "test1");
        TestDescription test2 = new TestDescription("class1", "test2");
        HashMap<String, Metric> metrics = new HashMap<String, Metric>();
        metrics.put("metric1", TfMetricProtoUtil.stringToMetric("value1"));

        // Verify Mocks
        mMockListener.invocationStarted(EasyMock.anyObject());

        mMockListener.testModuleStarted(EasyMock.anyObject());
        mMockListener.testRunStarted("run1", 2);
        mMockListener.testStarted(test1, 5L);
        mMockListener.testEnded(test1, 10L, new HashMap<String, Metric>());

        mMockListener.testStarted(test2, 11L);
        mMockListener.testFailed(test2, "I failed");
        mMockListener.testEnded(test2, 60L, metrics);
        mMockListener.testRunEnded(
                EasyMock.anyLong(), (HashMap<String, Metric>) EasyMock.anyObject());
        mMockListener.testModuleEnded();

        mMockListener.invocationEnded(500L);

        EasyMock.replay(mMockListener);
        // Invocation start
        mTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mTestParser.testModuleStarted(createModuleContext("arm64 module1"));
        mTestParser.testRunStarted("run1", 2);

        mTestParser.testStarted(test1, 5L);
        mTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mTestParser.testStarted(test2, 11L);
        mTestParser.testFailed(test2, "I failed");
        // test log
        mTestParser.logAssociation("log1", new LogFile("path", "url", false, LogDataType.TEXT, 5));

        mTestParser.testEnded(test2, 60L, metrics);
        // run log
        mTestParser.logAssociation(
                "run_log1", new LogFile("path", "url", false, LogDataType.LOGCAT, 5));
        mTestParser.testRunEnded(50L, new HashMap<String, Metric>());

        mTestParser.testModuleEnded();

        // Invocation ends
        mTestParser.invocationEnded(500L);
        EasyMock.verify(mMockListener);
    }

    /** Test that a run failure occurring inside a test case pair is handled properly. */
    @Test
    public void testRunFail_interleavedWithTest() {
        TestDescription test1 = new TestDescription("class1", "test1");

        // Verify Mocks
        mMockListener.invocationStarted(EasyMock.anyObject());

        mMockListener.testRunStarted("run1", 2);
        mMockListener.testStarted(test1, 5L);
        mMockListener.testEnded(test1, 10L, new HashMap<String, Metric>());

        mMockListener.testRunFailed("run failure");
        mMockListener.testRunEnded(
                EasyMock.anyLong(), (HashMap<String, Metric>) EasyMock.anyObject());

        mMockListener.invocationEnded(500L);

        EasyMock.replay(mMockListener);
        mTestParser.invocationStarted(mInvocationContext);
        // Run modules
        mTestParser.testRunStarted("run1", 2);

        mTestParser.testStarted(test1, 5L);
        // test run failed inside a test
        mTestParser.testRunFailed("run failure");

        mTestParser.testEnded(test1, 10L, new HashMap<String, Metric>());

        mTestParser.testRunEnded(50L, new HashMap<String, Metric>());
        // Invocation ends
        mTestParser.invocationEnded(500L);
        EasyMock.verify(mMockListener);
    }

    /** Helper to create a module context. */
    private IInvocationContext createModuleContext(String moduleId) {
        IInvocationContext context = new InvocationContext();
        context.addInvocationAttribute(ModuleDefinition.MODULE_ID, moduleId);
        context.setConfigurationDescriptor(new ConfigurationDescriptor());
        return context;
    }
}
