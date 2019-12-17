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
package com.android.tradefed.config.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;

/** Unit tests for {@link LocalFileResolver}. */
@RunWith(JUnit4.class)
public class LocalFileResolverTest {

    private LocalFileResolver mResolver;

    @Before
    public void setUp() {
        mResolver = new LocalFileResolver();
    }

    @Test
    public void testResolveLocalFile() throws Exception {
        File testFile = FileUtil.createTempFile("test-local-file", ".txt");
        try {
            File markedFile = new File("file:" + testFile.getAbsolutePath());
            File returned = mResolver.resolveRemoteFiles(markedFile, Mockito.mock(Option.class));
            assertEquals(testFile, returned);
        } finally {
            FileUtil.deleteFile(testFile);
        }
    }

    @Test
    public void testResolveLocalFile_notFound() throws Exception {
        File markedFile = new File("file:whateverpathsomewhere");
        try {
            mResolver.resolveRemoteFiles(markedFile, Mockito.mock(Option.class));
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            // Expected
        }
    }
}
