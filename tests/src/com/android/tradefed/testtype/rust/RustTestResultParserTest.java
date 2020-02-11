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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.ArrayUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Vector;

/** Unit tests for {@link RustTestResultParser}. */
@RunWith(JUnit4.class)
public class RustTestResultParserTest {

    public static final String RUST_OUTPUT_FILE_1 = "rust_output1.txt";
    public static final String RUST_OUTPUT_FILE_2 = "rust_output2.txt";
    public static final String RUST_OUTPUT_FILE_3 = "rust_output3.txt";

    private RustTestResultParser mParser;
    private ITestInvocationListener mMockListener;

    @Before
    public void setUp() throws Exception {
        mMockListener = createMock(ITestInvocationListener.class);
        mParser = new RustTestResultParser(ArrayUtil.list(mMockListener), "test");
    }

    @Test
    public void testRegexTestCase() {
        String[] goodPatterns = {
            "test some_test ... ok",
            "test some_test ... pass",
            "test other-test ... fail",
            "test any ... FAIL",
            "test some_test ... ignored",
            "test some_test ... other_strings",
        };
        String[] wrongPatterns = {
            " test some_test ... ok",
            "test  other-test ... fail",
            "test any ...  FAIL",
            "test some_test  ... ignored",
            "test some_test .. other",
            "test some_test ... other strings",
            "test some_test .... ok",
        };
        for (String s : goodPatterns) {
            assertTrue(s, RustTestResultParser.RUST_ONE_LINE_RESULT.matcher(s).matches());
        }
        for (String s : wrongPatterns) {
            assertFalse(s, RustTestResultParser.RUST_ONE_LINE_RESULT.matcher(s).matches());
        }
    }

    @Test
    public void testRegexRunSummary() {
        String[] goodPatterns = {
            "test result: some_test 0 passed; 0 failed; 15 ignored; ...",
            "test result: some_test 10 passed; 21 failed; 0 ignored;",
            "test result: any string here 2 passed; 0 failed; 0 ignored;...",
        };
        String[] wrongPatterns = {
            "test result: some_test 0 passed 0 failed 15 ignored; ...",
            "test result: some_test 10 passed; 21 failed;",
            "test some_test 0 passed; 0 failed; 15 ignored; ...",
            "  test result: some_test 10 passed; 21 failed;",
            "test result here 2 passed; 0 failed; 0 ignored...",
        };
        for (String s : goodPatterns) {
            assertTrue(s, RustTestResultParser.COMPLETE_PATTERN.matcher(s).matches());
        }
        for (String s : wrongPatterns) {
            assertFalse(s, RustTestResultParser.COMPLETE_PATTERN.matcher(s).matches());
        }
    }

    @Test
    public void testParseRealOutput() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_1);

        mMockListener.testRunStarted("test", 10);
        for (int i = 0; i < 10; i++) {
            mMockListener.testStarted(EasyMock.anyObject());
            mMockListener.testEnded(
                    EasyMock.anyObject(), EasyMock.<HashMap<String, Metric>>anyObject());
        }

        mMockListener.testRunEnded(0, new HashMap<String, Metric>()); // no total time yet.
        replay(mMockListener);
        mParser.processNewLines(contents);
        verify(mMockListener);
    }

    @Test
    public void testParseRealOutput2() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_2);
        mMockListener.testRunStarted("test", 23);
        for (int i = 0; i < 23; i++) {
            mMockListener.testStarted(EasyMock.anyObject());
            mMockListener.testEnded(
                    EasyMock.anyObject(), EasyMock.<HashMap<String, Metric>>anyObject());
        }
        mMockListener.testFailed(
                EasyMock.eq(new TestDescription("test", "idents")), (String) EasyMock.anyObject());
        mMockListener.testFailed(
                EasyMock.eq(new TestDescription("test", "literal_string")),
                (String) EasyMock.anyObject());
        mMockListener.testRunEnded(0, new HashMap<String, Metric>());
        replay(mMockListener);
        mParser.processNewLines(contents);
        verify(mMockListener);
    }

    @Test
    public void testParseRealOutput3() {
        String[] contents = readInFile(RUST_OUTPUT_FILE_3);
        mMockListener.testRunStarted("test", 1);
        for (int i = 0; i < 1; i++) {
            mMockListener.testStarted(EasyMock.anyObject());
            mMockListener.testEnded(
                    EasyMock.anyObject(), EasyMock.<HashMap<String, Metric>>anyObject());
        }
        mMockListener.testIgnored(
                EasyMock.eq(new TestDescription("test", "make_sure_no_proc_macro")));
        mMockListener.testRunEnded(0, new HashMap<String, Metric>());
        replay(mMockListener);
        mParser.processNewLines(contents);
        verify(mMockListener);
    }

    /**
     * Helper to read a file from the res/testtype directory and return its contents as a String[]
     *
     * @param filename the name of the file (without the extension) in the res/testtype directory
     * @return a String[] of the
     */
    private String[] readInFile(String filename) {
        Vector<String> fileContents = new Vector<String>();
        try {
            InputStream gtestResultStream1 =
                    getClass()
                            .getResourceAsStream(
                                    File.separator + "testtype" + File.separator + filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(gtestResultStream1));
            String line = null;
            while ((line = reader.readLine()) != null) {
                fileContents.add(line);
            }
        } catch (NullPointerException e) {
            CLog.e("Gest output file does not exist: " + filename);
        } catch (IOException e) {
            CLog.e("Unable to read contents of gtest output file: " + filename);
        }
        return fileContents.toArray(new String[fileContents.size()]);
    }
}
