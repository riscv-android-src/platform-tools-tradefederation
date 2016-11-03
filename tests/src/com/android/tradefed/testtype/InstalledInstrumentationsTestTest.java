/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tradefed.testtype;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link InstalledInstrumentationsTestTest}.
 */
public class InstalledInstrumentationsTestTest extends TestCase {

    private static final String TEST_PKG = "com.example.tests";
    private static final String TEST_COVERAGE_TARGET = "com.example";
    private static final String TEST_RUNNER = "android.support.runner.AndroidJUnitRunner";
    private static final String ABI = "forceMyAbiSettingPlease";
    private ITestDevice mMockTestDevice;
    private ITestInvocationListener mMockListener;
    private List<MockInstrumentationTest> mMockInstrumentationTests;
    private InstalledInstrumentationsTest mInstalledInstrTest;
    private ListInstrResponseBuilder mListInstrResponseBuilder;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andStubReturn("foo");
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockInstrumentationTests = new ArrayList<MockInstrumentationTest>();
        mInstalledInstrTest = createInstalledInstrumentationsTest();
        mInstalledInstrTest.setDevice(mMockTestDevice);
        mListInstrResponseBuilder = new ListInstrResponseBuilder();
    }

    /**
     * Test the run normal case. Simple verification that expected data is passed along, etc.
     */
    public void testRun() throws Exception {
        mListInstrResponseBuilder
                .addInstrumentation(TEST_PKG, TEST_RUNNER, TEST_COVERAGE_TARGET)
                .injectListInstrResponse();

        mMockListener.testRunStarted(TEST_PKG, 0);
        Capture<Map<String, String>> captureMetrics = new Capture<Map<String, String>>();
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.capture(captureMetrics));
        ArgsOptionParser p = new ArgsOptionParser(mInstalledInstrTest);
        p.parse("--size", "small", "--force-abi", ABI);
        mInstalledInstrTest.setSendCoverage(true);
        EasyMock.replay(mMockTestDevice, mMockListener);
        mInstalledInstrTest.run(mMockListener);
        assertEquals(1, mMockInstrumentationTests.size());
        MockInstrumentationTest mockInstrumentationTest = mMockInstrumentationTests.get(0);
        assertEquals(mMockListener, mockInstrumentationTest.getListener());
        assertEquals(TEST_PKG, mockInstrumentationTest.getPackageName());
        assertEquals(TEST_RUNNER, mockInstrumentationTest.getRunnerName());
        assertEquals(TEST_COVERAGE_TARGET, captureMetrics.getValue().get(
                InstalledInstrumentationsTest.COVERAGE_TARGET_KEY));
        assertEquals("small", mockInstrumentationTest.getTestSize());
        assertEquals(ABI, mockInstrumentationTest.getForceAbi());

        EasyMock.verify(mMockListener, mMockTestDevice);
    }

    /**
     * Tests the run of sharded InstalledInstrumentationsTests.
     */
    public void testShardedRun() throws Exception {
        final String shardableRunner = "android.support.test.runner.AndroidJUnitRunner";
        final String nonshardableRunner = "android.test.InstrumentationTestRunner";

        final String shardableTestPkg = "com.example.shardabletest";
        final String nonshardableTestPkg1 = "com.example.nonshardabletest1";
        final String nonshardableTestPkg2 = "com.example.nonshardabletest2";

        Set<String> nonshardableTestPkgs = new HashSet<String>(Arrays.asList(
                nonshardableTestPkg1,
                nonshardableTestPkg2));
        Set<String> testShards = new HashSet<String>(Arrays.asList("0", "1"));

        // Instrumentations to be reported by the test device
        mListInstrResponseBuilder
                .addInstrumentation(shardableTestPkg, shardableRunner, TEST_COVERAGE_TARGET)
                .addInstrumentation(nonshardableTestPkg1, nonshardableRunner, TEST_COVERAGE_TARGET)
                .addInstrumentation(nonshardableTestPkg2, nonshardableRunner, TEST_COVERAGE_TARGET)
                .injectListInstrResponse(2);

        // Instantiate InstalledInstrumentationTest shards
        EasyMock.replay(mMockTestDevice, mMockListener);
        InstalledInstrumentationsTest shard0 = createInstalledInstrumentationsTest();
        shard0.setDevice(mMockTestDevice);
        shard0.setShardIndex(0);
        shard0.setTotalShards(2);
        InstalledInstrumentationsTest shard1 = createInstalledInstrumentationsTest();
        shard1.setDevice(mMockTestDevice);
        shard1.setShardIndex(1);
        shard1.setTotalShards(2);

        // Run tests in first shard. There should be only two tests run: a test shard, and a
        // nonshardable test.

        shard0.run(mMockListener);
        assertEquals(2, mMockInstrumentationTests.size());
        assertEquals(nonshardableTestPkg1, mMockInstrumentationTests.get(0).getPackageName());
        assertEquals(shardableTestPkg, mMockInstrumentationTests.get(1).getPackageName());
        assertEquals("0", mMockInstrumentationTests.get(1).getInstrumentationArg("shardIndex"));
        assertEquals("2", mMockInstrumentationTests.get(1).getInstrumentationArg("numShards"));
        mMockInstrumentationTests.clear();

        // Run tests in second shard. All tests should be accounted for.
        shard1.run(mMockListener);
        assertEquals(2, mMockInstrumentationTests.size());
        assertEquals(nonshardableTestPkg2, mMockInstrumentationTests.get(0).getPackageName());
        assertEquals(shardableTestPkg, mMockInstrumentationTests.get(1).getPackageName());
        assertEquals("1", mMockInstrumentationTests.get(1).getInstrumentationArg("shardIndex"));
        assertEquals("2", mMockInstrumentationTests.get(1).getInstrumentationArg("numShards"));

        EasyMock.verify(mMockListener, mMockTestDevice);
    }

    /** A utility class for building the output of the list instrumentation response. */
    private class ListInstrResponseBuilder {

        private static final String INSTR_RESPONSE_FORMAT = "instrumentation:%s/%s (target=%s)\r\n";
        private StringBuilder mResponseBuilder = new StringBuilder();

        public ListInstrResponseBuilder addInstrumentation(
                String test_package, String test_runner, String test_target) {
            mResponseBuilder.append(
                    String.format(INSTR_RESPONSE_FORMAT, test_package, test_runner, test_target));
            return this;
        }

        public String toString() {
            return mResponseBuilder.toString();
        }

        public void injectListInstrResponse() throws DeviceNotAvailableException {
            injectListInstrResponse(1);
        }

        public void injectListInstrResponse(int numCalls) throws DeviceNotAvailableException {
            injectShellResponse(mListInstrResponseBuilder.toString(), numCalls);
        }
    }

    @SuppressWarnings("unchecked")
    private void injectShellResponse(final String shellResponse, int numExpectedCalls)
            throws DeviceNotAvailableException {
        IAnswer<Object> shellAnswer = new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                IShellOutputReceiver receiver =
                        (IShellOutputReceiver)EasyMock.getCurrentArguments()[1];
                byte[] bytes = shellResponse.getBytes();
                receiver.addOutput(bytes, 0, bytes.length);
                receiver.flush();
                return null;
            }
        };
        mMockTestDevice.executeShellCommand(EasyMock.<String>anyObject(),
                EasyMock.<IShellOutputReceiver>anyObject());
        EasyMock.expectLastCall().andAnswer(shellAnswer).times(numExpectedCalls);
    }

    /**
     * Utility method for creating an InstalledInstrumentationsTest for testing.
     *
     * InstalledInstrumentationsTests need to create a MockInstrumentationTest, and we need to be
     * able to keep track of all mocks created in this manner.
     */
    private InstalledInstrumentationsTest createInstalledInstrumentationsTest() {
        InstalledInstrumentationsTest test = new InstalledInstrumentationsTest() {
            @Override
            InstrumentationTest createInstrumentationTest() {
                MockInstrumentationTest test = new MockInstrumentationTest();
                mMockInstrumentationTests.add(test);
                return test;
            }
        };
        return test;
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting device.
     */
    public void testRun_noDevice() throws Exception {
        mListInstrResponseBuilder.injectListInstrResponse();
        mInstalledInstrTest.setDevice(null);
        try {
            mInstalledInstrTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run when no instrumentations
     * are present.
     */
    public void testRun_noInstr() throws Exception {
        mListInstrResponseBuilder.injectListInstrResponse();
        try {
            mInstalledInstrTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
