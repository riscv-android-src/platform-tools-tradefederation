/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tradefed.profiler;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.profiler.recorder.IMetricsRecorder;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Tests for {@link AggregatingProfiler}.
 */
@RunWith(JUnit4.class)
public class AggregatingProfilerTest {
    private static final double EPSILON = 1E-6;

    private AggregatingProfiler mProfiler;
    private TestIdentifier mTestId;
    private ITestDevice mTestDevice1 = EasyMock.createMock(ITestDevice.class);
    private ITestDevice mTestDevice2 = EasyMock.createMock(ITestDevice.class);
    private IMetricsRecorder mRecorderA1 = EasyMock.createMock(IMetricsRecorder.class);
    private IMetricsRecorder mRecorderA2 = EasyMock.createMock(IMetricsRecorder.class);
    private IMetricsRecorder mRecorderB1 = EasyMock.createMock(IMetricsRecorder.class);
    private IMetricsRecorder mRecorderB2 = EasyMock.createMock(IMetricsRecorder.class);
    private IInvocationContext mContext = EasyMock.createMock(IInvocationContext.class);

    @Before
    public void setUp() throws Exception {
        mProfiler =
                new AggregatingProfiler() {
                    @Override
                    public List<ITestDevice> getDevices() {
                        return Arrays.asList(mTestDevice1, mTestDevice2);
                    }

                    @Override
                    public List<IMetricsRecorder> getRecorders() {
                        return Arrays.asList(mRecorderA1, mRecorderA2, mRecorderB1, mRecorderB2);
                    }

                    @Override
                    public String getDescription() {
                        return "test";
                    }

                    @Override
                    public MetricOutputData getMetricOutputUtil() {
                        return new MetricOutputData();
                    }
                };

        mTestId = new TestIdentifier("foo", "bar");
        EasyMock.expect(mTestDevice1.getSerialNumber()).andReturn("-1").anyTimes();
        EasyMock.expect(mTestDevice2.getSerialNumber()).andReturn("-2").anyTimes();
        EasyMock.expect(mRecorderA1.getName()).andReturn("A1").anyTimes(); // device 1
        EasyMock.expect(mRecorderA2.getName()).andReturn("A2").anyTimes(); // device 2
        EasyMock.expect(mRecorderB1.getName()).andReturn("B1").anyTimes(); // device 1
        EasyMock.expect(mRecorderB2.getName()).andReturn("B2").anyTimes(); // device 2
        EasyMock.expect(mContext.getDevices()).andReturn(new ArrayList<>()).anyTimes();
        EasyMock.expect(mContext.getTestTag()).andReturn("AggregatingProfilerTest").anyTimes();
        EasyMock.replay(mContext);
        mProfiler.setUp(mContext);
    }

    @Test
    public void testStartMetrics() throws Exception {
        mRecorderA1.startRecording();
        EasyMock.expectLastCall().times(1);
        mRecorderA2.startRecording();
        EasyMock.expectLastCall().times(1);
        mRecorderB1.startRecording();
        EasyMock.expectLastCall().times(1);
        mRecorderB2.startRecording();
        EasyMock.expectLastCall().times(1);
        EasyMock.replay(
                mTestDevice1, mTestDevice2, mRecorderA1, mRecorderA2, mRecorderB1, mRecorderB2);
        mProfiler.startRecordingMetrics();
        EasyMock.verify(mRecorderA1, mRecorderA2, mRecorderB1, mRecorderB2);
    }

    @Test
    public void testStopMetrics() throws Exception {
        Map<String, Double> metricA1 = new HashMap<>();
        metricA1.put("x", 1.0);
        metricA1.put("y", 2.0);
        Map<String, Double> metricA2 = new HashMap<>();
        metricA2.put("x", 10.0);
        metricA2.put("y", 20.0);
        Map<String, Double> metricB1 = new HashMap<>();
        metricB1.put("x", 100.0);
        metricB1.put("y", 200.0);
        Map<String, Double> metricB2 = new HashMap<>();
        metricB2.put("x", 1000.0);
        metricB2.put("y", 2000.0);
        EasyMock.expect(mRecorderA1.stopRecordingAndReturnMetrics()).andReturn(metricA1).times(1);
        EasyMock.expect(mRecorderA2.stopRecordingAndReturnMetrics()).andReturn(metricA2).times(1);
        EasyMock.expect(mRecorderB1.stopRecordingAndReturnMetrics()).andReturn(metricB1).times(1);
        EasyMock.expect(mRecorderB2.stopRecordingAndReturnMetrics()).andReturn(metricB2).times(1);
        EasyMock.expect(mRecorderA1.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .times(4);
        EasyMock.expect(mRecorderA2.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .times(4);
        EasyMock.expect(mRecorderB1.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .times(4);
        EasyMock.expect(mRecorderB2.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .times(4);
        EasyMock.replay(
                mTestDevice1, mTestDevice2, mRecorderA1, mRecorderA2, mRecorderB1, mRecorderB2);

        Map<String, Double> m = mProfiler.stopRecordingMetrics(mTestId);
        EasyMock.verify(mRecorderA1, mRecorderA2, mRecorderB1, mRecorderB2);
        Assert.assertEquals(m, mProfiler.getAggregateMetrics());
        Assert.assertEquals(1111.0, mProfiler.getAggregateMetrics().get("x"), EPSILON);
        Assert.assertEquals(1111.0, m.get("x"), EPSILON);
    }

    @Test
    public void testAggregateMetrics() throws Exception {
        Map<String, Double> metric = new HashMap<>();
        metric.put("x", 5.0);
        EasyMock.expect(mRecorderA1.stopRecordingAndReturnMetrics()).andReturn(metric).anyTimes();
        EasyMock.expect(mRecorderB1.stopRecordingAndReturnMetrics()).andReturn(metric).anyTimes();
        EasyMock.expect(mRecorderA2.stopRecordingAndReturnMetrics()).andReturn(metric).anyTimes();
        EasyMock.expect(mRecorderB2.stopRecordingAndReturnMetrics()).andReturn(metric).anyTimes();
        EasyMock.expect(mRecorderA1.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .anyTimes();
        EasyMock.expect(mRecorderA2.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .anyTimes();
        EasyMock.expect(mRecorderB1.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .anyTimes();
        EasyMock.expect(mRecorderB2.getMergeFunction((String) EasyMock.anyObject()))
                .andReturn(sum())
                .anyTimes();
        EasyMock.replay(
                mTestDevice1, mTestDevice2, mRecorderA1, mRecorderA2, mRecorderB1, mRecorderB2);

        Map<String, Double> m = mProfiler.stopRecordingMetrics(mTestId);
        Assert.assertEquals(m, mProfiler.getAggregateMetrics());
        Assert.assertEquals(20.0, mProfiler.getAggregateMetrics().get("x"), EPSILON);
        Assert.assertEquals(20.0, m.get("x"), EPSILON);
        Map<String, Double> m2 = mProfiler.stopRecordingMetrics(mTestId);
        Assert.assertEquals(40.0, mProfiler.getAggregateMetrics().get("x"), EPSILON);
        Assert.assertEquals(20.0, m2.get("x"), EPSILON);
    }

    @Test
    public void testReportAllMetrics() throws Exception {
        Map<String, Double> metric1 = new HashMap<>();
        metric1.put("x", 5.0);
        mProfiler.setAggregateMetrics(metric1);
        ITestInvocationListener mockListener = EasyMock.createMock(ITestInvocationListener.class);
        mockListener.testLog((String)EasyMock.anyObject(), EasyMock.eq(LogDataType.MUGSHOT_LOG),
                (InputStreamSource)EasyMock.anyObject());
        EasyMock.replay(mockListener);
        mProfiler.reportAllMetrics(mockListener);
        EasyMock.verify(mockListener);
    }

    private BiFunction<Double, Double, Double> sum() {
        return (x, y) -> x + y;
    }
}
