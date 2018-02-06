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
package com.android.tradefed.util;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** A class for loading a TEST_MAPPING file. */
public class TestMapping {

    /** Stores the test information set in a TEST_MAPPING file. */
    protected class TestInfo {
        private String mName = null;

        public TestInfo(String name) {
            mName = name;
        }

        public String getName() {
            return mName;
        }
    }

    private static final String PRESUBMIT = "presubmit";
    private static final String POSTSUBMIT = "postsubmit";
    private static final String KEY_INCLUDE_PARENT = "include_parent";
    private static final String KEY_NAME = "name";
    private static final String TEST_MAPPING = "TEST_MAPPING";
    private static final String TEST_MAPPINGS_ZIP = "test_mappings.zip";

    private Map<String, List<TestInfo>> mTestCollection = null;

    /**
     * Constructor to create a {@link TestMapping} object from a path to TEST_MAPPING file.
     *
     * @param path The {@link Path} to a TEST_MAPPING file.
     */
    public TestMapping(Path path) {
        mTestCollection = new LinkedHashMap<>();
        String errorMessage = null;
        try {
            String content = String.join("", Files.readAllLines(path, StandardCharsets.UTF_8));
            if (content != null) {
                JSONTokener tokener = new JSONTokener(content);
                JSONObject root = new JSONObject(tokener);
                Iterator<String> testTypes = (Iterator<String>) root.keys();
                while (testTypes.hasNext()) {
                    String type = testTypes.next();
                    if (type.equals(KEY_INCLUDE_PARENT)) {
                        continue;
                    }
                    List<TestInfo> testsForType = new ArrayList<TestInfo>();
                    mTestCollection.put(type, testsForType);
                    JSONArray arr = root.getJSONArray(type);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject testObject = arr.getJSONObject(i);
                        testsForType.add(new TestInfo(testObject.getString(KEY_NAME)));
                    }
                }
            }
        } catch (IOException e) {
            errorMessage = String.format("TEST_MAPPING file does not exist: %s.", path.toString());
            CLog.e(errorMessage);
        } catch (JSONException e) {
            errorMessage = String.format("Error parsing TEST_MAPPING file: %s.", path.toString());
        }

        if (errorMessage != null) {
            CLog.e(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * Helper to get all tests set in a TEST_MAPPING file for a given type.
     *
     * @param testType A {@link String} of the test type.
     * @return A {@code List<String>} of the test names.
     */
    public List<String> getTests(String testType) {
        List<String> tests = new ArrayList<String>();

        if (mTestCollection.containsKey(testType)) {
            for (TestInfo test : mTestCollection.get(testType)) {
                tests.add(test.getName());
            }
        }
        // All presubmit tests should be part of postsubmit too.
        if (testType.equals(POSTSUBMIT) && mTestCollection.containsKey(PRESUBMIT)) {
            for (TestInfo test : mTestCollection.get(PRESUBMIT)) {
                tests.add(test.getName());
            }
        }

        return tests;
    }

    /**
     * Helper to find all tests in all TEST_MAPPING files. This is needed when a suite run requires
     * to run all tests in TEST_MAPPING files for a given type, e.g., presubmit.
     *
     * @param testType a {@link String} of the test type.
     * @return A {@code Set<String>} of tests set in the build artifact, test_mappings.zip.
     */
    public static Set<String> getTests(IBuildInfo buildInfo, String testType) {
        Set<String> tests = new HashSet<String>();

        File testMappingsZip = buildInfo.getFile(TEST_MAPPINGS_ZIP);
        File testMappingsDir = null;
        Stream<Path> stream = null;
        try {
            testMappingsDir = ZipUtil2.extractZipToTemp(testMappingsZip, TEST_MAPPINGS_ZIP);
            stream =
                    Files.walk(
                            Paths.get(testMappingsDir.getAbsolutePath()),
                            FileVisitOption.FOLLOW_LINKS);
            stream.filter(path -> path.getFileName().toString().equals(TEST_MAPPING))
                    .forEach(path -> tests.addAll((new TestMapping(path)).getTests(testType)));
        } catch (IOException e) {
            RuntimeException runtimeException =
                    new RuntimeException(
                            String.format(
                                    "IO error (%s) when reading tests from TEST_MAPPING files (%s)",
                                    e.getMessage(), testMappingsZip.getAbsolutePath()),
                            e);
            throw runtimeException;
        } finally {
            if (stream != null) {
                stream.close();
            }
            FileUtil.recursiveDelete(testMappingsDir);
        }

        return tests;
    }
}
