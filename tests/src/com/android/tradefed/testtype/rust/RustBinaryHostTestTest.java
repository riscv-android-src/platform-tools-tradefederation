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
package com.android.tradefed.testtype.rust;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.HashMap;

/** Unit tests for {@link RustBinaryHostTest}. */
@RunWith(JUnit4.class)
public class RustBinaryHostTestTest {
    private RustBinaryHostTest mTest;
    private IRunUtil mMockRunUtil;
    private IBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;
    private ITestInvocationListener mMockListener;
    // TODO(chh): maybe we will need mFakeAdb later like PythonBinaryHostTestTest.

    @Before
    public void setUp() throws Exception {
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mTest =
                new RustBinaryHostTest() {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mTest.setBuild(mMockBuildInfo);
        InvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    /** Test that when running a rust binary the output is parsed to obtain results. */
    @Test
    public void testRun() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            res.setStderr("");
            res.setStdout("test result: ok. 6 passed; 1 failed; 2 ignored;");
            EasyMock.expect(
                            mMockRunUtil.runTimedCmd(
                                    EasyMock.anyLong(), EasyMock.eq(binary.getAbsolutePath())))
                    .andReturn(res);
            mMockListener.testRunStarted(
                    EasyMock.eq(binary.getName()),
                    EasyMock.eq(9),
                    EasyMock.eq(0),
                    EasyMock.anyLong());
            mMockListener.testRunEnded(0, new HashMap<String, Metric>());
            mMockListener.testLog(
                    EasyMock.eq(binary.getName() + "-stderr"),
                    EasyMock.eq(LogDataType.TEXT),
                    EasyMock.anyObject());

            EasyMock.replay(mMockRunUtil, mMockBuildInfo, mMockListener);
            mTest.run(mTestInfo, mMockListener);
            EasyMock.verify(mMockRunUtil, mMockBuildInfo, mMockListener);
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /**
     * Test running the rust tests when an adb path has been set. In that case we ensure the rust
     * test will use the provided adb.
     */
    @Test
    public void testRun_withAdbPath() throws Exception {
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mTest.setBuild(mMockBuildInfo);

        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.SUCCESS);
            res.setStderr("");
            res.setStdout("test result: ok. 6 passed; 1 failed; 2 ignored;");
            EasyMock.expect(
                            mMockRunUtil.runTimedCmd(
                                    EasyMock.anyLong(), EasyMock.eq(binary.getAbsolutePath())))
                    .andReturn(res);
            mMockListener.testRunStarted(
                    EasyMock.eq(binary.getName()),
                    EasyMock.eq(9),
                    EasyMock.eq(0),
                    EasyMock.anyLong());
            mMockListener.testRunEnded(0, new HashMap<String, Metric>());
            mMockListener.testLog(
                    EasyMock.eq(binary.getName() + "-stderr"),
                    EasyMock.eq(LogDataType.TEXT),
                    EasyMock.anyObject());

            EasyMock.replay(mMockRunUtil, mMockBuildInfo, mMockListener);
            mTest.run(mTestInfo, mMockListener);
            EasyMock.verify(mMockRunUtil, mMockBuildInfo, mMockListener);
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /**
     * If the binary returns an exception status, we should throw a runtime exception since
     * something went wrong with the binary setup.
     */
    @Test
    public void testRunFail_exception() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.EXCEPTION);
            res.setStderr("Could not execute.");
            res.setStdout("Could not execute.");
            EasyMock.expect(
                            mMockRunUtil.runTimedCmd(
                                    EasyMock.anyLong(), EasyMock.eq(binary.getAbsolutePath())))
                    .andReturn(res);

            mMockListener.testLog(
                    EasyMock.eq(binary.getName() + "-stderr"),
                    EasyMock.eq(LogDataType.TEXT),
                    EasyMock.anyObject());
            // Report a failure if we cannot parse the logs and the logs is not empty.
            mMockListener.testRunStarted(binary.getName(), 0);
            mMockListener.testRunFailed(EasyMock.anyObject());
            mMockListener.testRunEnded(
                    EasyMock.anyLong(), EasyMock.<HashMap<String, Metric>>anyObject());

            EasyMock.replay(mMockRunUtil, mMockBuildInfo, mMockListener);
            mTest.run(mTestInfo, mMockListener);
            EasyMock.verify(mMockRunUtil, mMockBuildInfo, mMockListener);
        } finally {
            FileUtil.deleteFile(binary);
        }
    }

    /**
     * If the binary reports a FAILED status but the output actually have some tests, it most likely
     * means that some tests failed. So we simply continue with parsing the results.
     */
    @Test
    public void testRunFail_failureOnly() throws Exception {
        File binary = FileUtil.createTempFile("rust-dir", "");
        try {
            OptionSetter setter = new OptionSetter(mTest);
            setter.setOptionValue("test-file", binary.getAbsolutePath());

            CommandResult res = new CommandResult();
            res.setStatus(CommandStatus.FAILED);
            res.setStderr("");
            res.setStdout("test result: ok. 6 passed; 1 failed; 2 ignored;");
            EasyMock.expect(
                            mMockRunUtil.runTimedCmd(
                                    EasyMock.anyLong(), EasyMock.eq(binary.getAbsolutePath())))
                    .andReturn(res);
            mMockListener.testRunStarted(
                    EasyMock.eq(binary.getName()),
                    EasyMock.eq(9),
                    EasyMock.eq(0),
                    EasyMock.anyLong());
            mMockListener.testRunEnded(0, new HashMap<String, Metric>());
            mMockListener.testLog(
                    EasyMock.eq(binary.getName() + "-stderr"),
                    EasyMock.eq(LogDataType.TEXT),
                    EasyMock.anyObject());
            EasyMock.replay(mMockRunUtil, mMockBuildInfo, mMockListener);
            mTest.run(mTestInfo, mMockListener);
            EasyMock.verify(mMockRunUtil, mMockBuildInfo, mMockListener);
        } finally {
            FileUtil.deleteFile(binary);
        }
    }
}
