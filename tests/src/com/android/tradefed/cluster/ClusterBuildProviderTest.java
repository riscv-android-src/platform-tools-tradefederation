/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.cluster;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

/** Unit tests for {@link ClusterBuildProvider}. */
@RunWith(JUnit4.class)
public class ClusterBuildProviderTest {

    private static final String RESOURCE_KEY_1 = "resource_key_1";
    private static final String RESOURCE_KEY_2 = "resource_key_2";

    private File mRootDir;
    private File mResourceFile;
    private Map<String, File> mDownloadCache;
    private TestResourceDownloader mSpyDownloader;

    @Before
    public void setUp() throws IOException {
        mRootDir = FileUtil.createTempDir("ClusterBuildProvider");
        mResourceFile = FileUtil.createTempFile("TestResource", ".txt");
        mDownloadCache = ClusterBuildProvider.sDownloadCache.get();
        mSpyDownloader = Mockito.spy(new TestResourceDownloader());
    }

    @After
    public void tearDown() {
        mDownloadCache.clear();
        FileUtil.deleteFile(mResourceFile);
        FileUtil.recursiveDelete(mRootDir);
    }

    private ClusterBuildProvider createClusterBuildProvider() throws MalformedURLException {
        ClusterBuildProvider provider =
                new ClusterBuildProvider() {
                    @Override
                    TestResourceDownloader createTestResourceDownloader() {
                        return mSpyDownloader;
                    }
                };
        provider.setRootDir(mRootDir);
        provider.getTestResources().put(RESOURCE_KEY_1, mResourceFile.toURI().toURL().toString());
        return provider;
    }

    /** Test one provider with two identical URLs. */
    @Test
    public void testGetBuild_multipleTestResources() throws BuildRetrievalError, IOException {
        ClusterBuildProvider provider = createClusterBuildProvider();
        provider.getTestResources().put(RESOURCE_KEY_2, mResourceFile.toURI().toURL().toString());
        provider.getBuild();

        File file1 = new File(mRootDir, RESOURCE_KEY_1);
        File file2 = new File(mRootDir, RESOURCE_KEY_2);
        Assert.assertTrue(file1.isFile());
        Assert.assertTrue(file2.isFile());
        Assert.assertEquals(1, mDownloadCache.size());
        Assert.assertEquals(file1, mDownloadCache.get(mResourceFile.toURI().toURL().toString()));
        Mockito.verify(mSpyDownloader).download(Mockito.any(), Mockito.eq(file1));
        Mockito.verify(mSpyDownloader, Mockito.never()).download(Mockito.any(), Mockito.eq(file2));
    }

    /** Test two providers downloading from the same URL. */
    @Test
    public void testGetBuild_multipleBuildProviders() throws BuildRetrievalError, IOException {
        ClusterBuildProvider provider1 = createClusterBuildProvider();
        ClusterBuildProvider provider2 = createClusterBuildProvider();
        provider1.getBuild();
        provider2.getBuild();

        File file = new File(mRootDir, RESOURCE_KEY_1);
        Assert.assertTrue(file.isFile());
        Assert.assertEquals(1, mDownloadCache.size());
        Assert.assertEquals(file, mDownloadCache.get(mResourceFile.toURI().toURL().toString()));
        Mockito.verify(mSpyDownloader).download(Mockito.any(), Mockito.eq(file));
    }
}
