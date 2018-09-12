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
package com.android.tradefed.util.testmapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Unit tests for {@link TestMapping}. */
@RunWith(JUnit4.class)
public class TestMappingTest {

    private static final String TEST_DATA_DIR = "testdata";
    private static final String TEST_MAPPING = "TEST_MAPPING";
    private static final String TEST_MAPPINGS_ZIP = "test_mappings.zip";

    /** Test for {@link TestMapping#getTests()} implementation. */
    @Test
    public void testparseTestMapping() throws Exception {
        File tempDir = null;
        File testMappingFile = null;

        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            testMappingFile = FileUtil.saveResourceFile(resourceStream, tempDir, TEST_MAPPING);
            List<TestInfo> tests = new TestMapping(testMappingFile.toPath()).getTests("presubmit");
            assertEquals(1, tests.size());
            assertEquals("test1", tests.get(0).getName());
            tests = new TestMapping(testMappingFile.toPath()).getTests("postsubmit");
            assertEquals(3, tests.size());
            assertEquals("test2", tests.get(0).getName());
            TestOption option = tests.get(0).getOptions().get(0);
            assertEquals("instrumentation-arg", option.getName());
            assertEquals(
                    "annotation=android.platform.test.annotations.Presubmit", option.getValue());
            assertEquals("instrument", tests.get(1).getName());
            tests = new TestMapping(testMappingFile.toPath()).getTests("othertype");
            assertEquals(1, tests.size());
            assertEquals("test3", tests.get(0).getName());
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /** Test for {@link TestMapping#getTests()} throw exception for malformated json file. */
    @Test(expected = RuntimeException.class)
    public void testparseTestMapping_BadJson() throws Exception {
        File tempDir = null;

        try {
            tempDir = FileUtil.createTempDir("test_mapping");
            File testMappingFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPING).toFile();
            FileUtil.writeToFile("bad format json file", testMappingFile);
            List<TestInfo> tests = new TestMapping(testMappingFile.toPath()).getTests("presubmit");
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /** Test for {@link TestMapping#getTests()} for loading tests from test_mappings.zip. */
    @Test
    public void testGetTests() throws Exception {
        File tempDir = null;
        try {
            tempDir = FileUtil.createTempDir("test_mapping");

            File srcDir = FileUtil.createTempDir("src", tempDir);
            String srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_1";
            InputStream resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, srcDir, TEST_MAPPING);
            File subDir = FileUtil.createTempDir("sub_dir", srcDir);
            srcFile = File.separator + TEST_DATA_DIR + File.separator + "test_mapping_2";
            resourceStream = this.getClass().getResourceAsStream(srcFile);
            FileUtil.saveResourceFile(resourceStream, subDir, TEST_MAPPING);

            File zipFile = Paths.get(tempDir.getAbsolutePath(), TEST_MAPPINGS_ZIP).toFile();
            ZipUtil.createZip(srcDir, zipFile);
            IBuildInfo mockBuildInfo = EasyMock.createMock(IBuildInfo.class);
            EasyMock.expect(mockBuildInfo.getFile(TEST_MAPPINGS_ZIP)).andReturn(zipFile);

            EasyMock.replay(mockBuildInfo);
            Set<TestInfo> tests = TestMapping.getTests(mockBuildInfo, "presubmit");

            assertEquals(2, tests.size());
            Set<String> names = new HashSet<String>();
            for (TestInfo test : tests) {
                names.add(test.getName());
                // Make sure the tests for `test1` are merged and no option is kept.
                assertTrue(test.getOptions().isEmpty());
            }
            assertTrue(names.contains("suite/stub1"));
            assertTrue(names.contains("test1"));
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects to fail when module names
     * are different.
     */
    @Test(expected = RuntimeException.class)
    public void testMergeFailByName() throws Exception {
        TestInfo test1 = new TestInfo("test1");
        TestInfo test2 = new TestInfo("test2");
        test1.merge(test2);
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects, one of which has no
     * option.
     */
    @Test
    public void testMergeSuccess() throws Exception {
        // Check that the test without any option should be the merge result.
        TestInfo test1 = new TestInfo("test1");
        TestInfo test2 = new TestInfo("test1");
        test2.addOption(new TestOption("include-filter", "value"));
        test1.merge(test2);
        assertTrue(test1.getOptions().isEmpty());

        test1 = new TestInfo("test1");
        test2 = new TestInfo("test1");
        test1.addOption(new TestOption("include-filter", "value"));
        test1.merge(test2);
        assertTrue(test1.getOptions().isEmpty());
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects, each has a different
     * include-filter.
     */
    @Test
    public void testMergeSuccess_2Filters() throws Exception {
        // Check that the test without any option should be the merge result.
        TestInfo test1 = new TestInfo("test1");
        TestInfo test2 = new TestInfo("test1");
        TestOption option1 = new TestOption("include-filter", "value1");
        test1.addOption(option1);
        TestOption option2 = new TestOption("include-filter", "value2");
        test2.addOption(option2);
        test1.merge(test2);
        assertEquals(2, test1.getOptions().size());
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(option1));
        assertTrue(new HashSet<TestOption>(test1.getOptions()).contains(option2));
    }

    /**
     * Test for {@link TestInfo#merge()} for merging two TestInfo objects, each has mixed
     * include-filter and exclude-filter.
     */
    @Test
    public void testMergeSuccess_multiFilters() throws Exception {
        // Check that the test without any option should be the merge result.
        TestInfo test1 = new TestInfo("test1");
        TestInfo test2 = new TestInfo("test1");
        TestOption inclusiveOption1 = new TestOption("include-filter", "value1");
        test1.addOption(inclusiveOption1);
        TestOption exclusiveOption1 = new TestOption("exclude-filter", "exclude-value1");
        test1.addOption(exclusiveOption1);
        TestOption exclusiveOption2 = new TestOption("exclude-filter", "exclude-value2");
        test1.addOption(exclusiveOption2);
        TestOption otherOption1 = new TestOption("somefilter", "");
        test1.addOption(otherOption1);

        TestOption inclusiveOption2 = new TestOption("include-filter", "value2");
        test2.addOption(inclusiveOption2);
        // Same exclusive option as in test1.
        test2.addOption(exclusiveOption1);
        TestOption exclusiveOption3 = new TestOption("exclude-filter", "exclude-value3");
        test2.addOption(exclusiveOption3);
        TestOption otherOption2 = new TestOption("somefilter2", "value2");
        test2.addOption(otherOption2);

        test1.merge(test2);
        assertEquals(5, test1.getOptions().size());
        Set<TestOption> mergedOptions = new HashSet<TestOption>(test1.getOptions());
        // Options from test1.
        assertTrue(mergedOptions.contains(inclusiveOption1));
        assertTrue(mergedOptions.contains(otherOption1));
        // Shared exclusive option between test1 and test2.
        assertTrue(mergedOptions.contains(exclusiveOption1));
        // Options from test2.
        assertTrue(mergedOptions.contains(inclusiveOption2));
        assertTrue(mergedOptions.contains(otherOption2));
    }
}
