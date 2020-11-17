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
import java.util.ArrayList;
import java.util.Map;

/** Unit tests for {@link ClusterBuildProvider}. */
@RunWith(JUnit4.class)
public class ClusterBuildProviderTest {

    private static final String RESOURCE_KEY = "resource_key_1";
    private static final String EXTRA_RESOURCE_KEY = "resource_key_2";

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
        if (mDownloadCache != null) {
            mDownloadCache.clear();
        }
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
        provider.getTestResources().put(RESOURCE_KEY, mResourceFile.toURI().toURL().toString());
        return provider;
    }

    private void verifyDownloadedResource() throws IOException {
        File file = new File(mRootDir, RESOURCE_KEY);
        Assert.assertTrue(file.isFile());
        Assert.assertEquals(file, mDownloadCache.get(mResourceFile.toURI().toURL().toString()));
        Mockito.verify(mSpyDownloader).download(Mockito.any(), Mockito.eq(file));
    }

    /** Test one provider with two identical URLs. */
    @Test
    public void testGetBuild_multipleTestResources() throws BuildRetrievalError, IOException {
        ClusterBuildProvider provider = createClusterBuildProvider();
        provider.getTestResources()
                .put(EXTRA_RESOURCE_KEY, mResourceFile.toURI().toURL().toString());
        provider.getBuild();

        verifyDownloadedResource();
        Assert.assertEquals(1, mDownloadCache.size());
        File file = new File(mRootDir, EXTRA_RESOURCE_KEY);
        Assert.assertTrue(file.isFile());
        Mockito.verify(mSpyDownloader, Mockito.never()).download(Mockito.any(), Mockito.eq(file));
    }

    /** Test two providers downloading from the same URL. */
    @Test
    public void testGetBuild_multipleBuildProviders() throws BuildRetrievalError, IOException {
        ClusterBuildProvider provider1 = createClusterBuildProvider();
        ClusterBuildProvider provider2 = createClusterBuildProvider();
        provider1.getBuild();
        provider2.getBuild();

        verifyDownloadedResource();
        Assert.assertEquals(1, mDownloadCache.size());
    }

    /** Test {@link ClusterBuildProvider#getBuild()} in an invocation thread. */
    private void testGetBuild(File extraResourceFile) throws BuildRetrievalError, IOException {
        ClusterBuildProvider provider = createClusterBuildProvider();
        provider.getTestResources()
                .put(EXTRA_RESOURCE_KEY, extraResourceFile.toURI().toURL().toString());
        provider.getBuild();

        verifyDownloadedResource();
        Assert.assertEquals(2, mDownloadCache.size());
        File file = new File(mRootDir, EXTRA_RESOURCE_KEY);
        Assert.assertTrue(file.isFile());
        Mockito.verify(mSpyDownloader).download(Mockito.any(), Mockito.eq(file));
    }

    /** Test two invocation threads downloading twice from the same URL. */
    @Test
    public void testGetBuild_multipleInvocations()
            throws BuildRetrievalError, IOException, InterruptedException {
        File sharedResourceFile = FileUtil.createTempFile("SharedTestResource", ".txt");
        ClusterBuildProviderTest anotherTest = new ClusterBuildProviderTest();
        try {
            ArrayList<Throwable> threadExceptions = new ArrayList<>();
            Runnable runnable =
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                anotherTest.setUp();
                                anotherTest.testGetBuild(sharedResourceFile);
                            } catch (Throwable e) {
                                threadExceptions.add(e);
                            }
                        }
                    };

            ThreadGroup anotherGroup = new ThreadGroup("unit test");
            anotherGroup.setDaemon(true);
            Thread anotherThread =
                    new Thread(anotherGroup, runnable, "ClusterBuildProviderTestThread");
            // Terminate the thread when the main thread throws an exception and exits.
            anotherThread.setDaemon(true);
            anotherThread.start();
            testGetBuild(sharedResourceFile);
            anotherThread.join();
            if (threadExceptions.size() > 0) {
                throw new AssertionError(
                        anotherThread.getName() + " failed.", threadExceptions.get(0));
            }
        } finally {
            FileUtil.deleteFile(sharedResourceFile);
            anotherTest.tearDown();
        }
    }
}
