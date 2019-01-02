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
package com.android.tradefed.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.config.remote.GcsRemoteFileResolver;
import com.android.tradefed.config.remote.IRemoteFileResolver;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.Nonnull;

/** Unit tests for {@link DynamicRemoteFileResolver}. */
@RunWith(JUnit4.class)
public class DynamicRemoteFileResolverTest {

    private static class RemoteFileOption {
        @Option(name = "remote-file")
        public File remoteFile = null;

        @Option(name = "remote-file-list")
        public Collection<File> remoteFileList = new ArrayList<>();
    }

    @OptionClass(alias = "option-class-alias", global_namespace = false)
    private static class RemoteFileOptionWithOptionClass {
        @Option(name = "remote-file")
        public File remoteFile = null;
    }

    private DynamicRemoteFileResolver mResolver;
    private IRemoteFileResolver mMockResolver;

    @Before
    public void setUp() {
        mMockResolver = EasyMock.createMock(IRemoteFileResolver.class);
        mResolver =
                new DynamicRemoteFileResolver() {
                    @Override
                    IRemoteFileResolver getResolver(String protocol) {
                        if (protocol.equals(GcsRemoteFileResolver.PROTOCOL)) {
                            return mMockResolver;
                        }
                        return null;
                    }

                    @Override
                    boolean updateProtocols() {
                        // Do not set the static variable
                        return false;
                    }
                };
    }

    @Test
    public void testResolve() throws Exception {
        RemoteFileOption object = new RemoteFileOption();
        OptionSetter setter =
                new OptionSetter(object) {
                    @Override
                    DynamicRemoteFileResolver createResolver() {
                        return mResolver;
                    }
                };

        File fake = FileUtil.createTempFile("gs-option-setter-test", "txt");

        setter.setOptionValue("remote-file", "gs://fake/path");
        assertEquals("gs:/fake/path", object.remoteFile.getPath());

        EasyMock.expect(
                        mMockResolver.resolveRemoteFiles(
                                EasyMock.eq(new File("gs:/fake/path")), EasyMock.anyObject()))
                .andReturn(fake);
        EasyMock.replay(mMockResolver);

        Set<File> downloadedFile = setter.validateRemoteFilePath();
        try {
            assertEquals(1, downloadedFile.size());
            File downloaded = downloadedFile.iterator().next();
            // The file has been replaced by the downloaded one.
            assertEquals(downloaded.getAbsolutePath(), object.remoteFile.getAbsolutePath());
        } finally {
            for (File f : downloadedFile) {
                FileUtil.recursiveDelete(f);
            }
        }
        EasyMock.verify(mMockResolver);
    }

    @Test
    public void testResolve_remoteFileList() throws Exception {
        RemoteFileOption object = new RemoteFileOption();
        OptionSetter setter =
                new OptionSetter(object) {
                    @Override
                    DynamicRemoteFileResolver createResolver() {
                        return mResolver;
                    }
                };

        File fake = FileUtil.createTempFile("gs-option-setter-test", "txt");

        setter.setOptionValue("remote-file-list", "gs://fake/path");
        setter.setOptionValue("remote-file-list", "fake/file");
        assertEquals(2, object.remoteFileList.size());

        EasyMock.expect(
                        mMockResolver.resolveRemoteFiles(
                                EasyMock.eq(new File("gs:/fake/path")), EasyMock.anyObject()))
                .andReturn(fake);
        EasyMock.replay(mMockResolver);

        Set<File> downloadedFile = setter.validateRemoteFilePath();
        try {
            assertEquals(1, downloadedFile.size());
            File downloaded = downloadedFile.iterator().next();
            // The file has been replaced by the downloaded one.
            assertEquals(2, object.remoteFileList.size());
            Iterator<File> ite = object.remoteFileList.iterator();
            File notGsFile = ite.next();
            assertEquals("fake/file", notGsFile.getPath());
            File gsFile = ite.next();
            assertEquals(downloaded.getAbsolutePath(), gsFile.getAbsolutePath());
        } finally {
            for (File f : downloadedFile) {
                FileUtil.recursiveDelete(f);
            }
        }
        EasyMock.verify(mMockResolver);
    }

    @Test
    public void testResolve_remoteFileList_downloadError() throws Exception {
        RemoteFileOption object = new RemoteFileOption();
        OptionSetter setter =
                new OptionSetter(object) {
                    @Override
                    DynamicRemoteFileResolver createResolver() {
                        return mResolver;
                    }
                };
        setter.setOptionValue("remote-file-list", "fake/file");
        setter.setOptionValue("remote-file-list", "gs://success/fake/path");
        setter.setOptionValue("remote-file-list", "gs://success/fake/path2");
        setter.setOptionValue("remote-file-list", "gs://failure/test");
        assertEquals(4, object.remoteFileList.size());

        File fake = FileUtil.createTempFile("gs-option-setter-test", "txt");
        EasyMock.expect(
                        mMockResolver.resolveRemoteFiles(
                                EasyMock.eq(new File("gs://success/fake/path")),
                                EasyMock.anyObject()))
                .andReturn(fake);
        EasyMock.expect(
                        mMockResolver.resolveRemoteFiles(
                                EasyMock.eq(new File("gs://success/fake/path2")),
                                EasyMock.anyObject()))
                .andReturn(fake);
        EasyMock.expect(
                        mMockResolver.resolveRemoteFiles(
                                EasyMock.eq(new File("gs://failure/test")), EasyMock.anyObject()))
                .andThrow(new ConfigurationException("retrieval error"));
        EasyMock.replay(mMockResolver);
        try {
            setter.validateRemoteFilePath();
            fail("Should have thrown an exception");
        } catch (ConfigurationException expected) {
            // Only when we reach failure/test it fails
            assertTrue(expected.getMessage().contains("retrieval error"));
        }
        EasyMock.verify(mMockResolver);
    }

    @Test
    public void testResolve_withNoGlobalNameSpace() throws Exception {
        RemoteFileOptionWithOptionClass object = new RemoteFileOptionWithOptionClass();
        OptionSetter setter =
                new OptionSetter(object) {
                    @Override
                    DynamicRemoteFileResolver createResolver() {
                        return mResolver;
                    }
                };

        File fake = FileUtil.createTempFile("gs-option-setter-test", "txt");

        setter.setOptionValue("option-class-alias:remote-file", "gs://fake/path");
        assertEquals("gs:/fake/path", object.remoteFile.getPath());

        // File is downloaded the first time, then is ignored since it doesn't have the protocol
        // anymore
        EasyMock.expect(
                        mMockResolver.resolveRemoteFiles(
                                EasyMock.eq(new File("gs:/fake/path")), EasyMock.anyObject()))
                .andReturn(fake);
        EasyMock.replay(mMockResolver);

        Set<File> downloadedFile = setter.validateRemoteFilePath();
        try {
            assertEquals(1, downloadedFile.size());
            File downloaded = downloadedFile.iterator().next();
            // The file has been replaced by the downloaded one.
            assertEquals(downloaded.getAbsolutePath(), object.remoteFile.getAbsolutePath());
        } finally {
            for (File f : downloadedFile) {
                FileUtil.recursiveDelete(f);
            }
        }
        EasyMock.verify(mMockResolver);
    }

    /** Allow extra resolver to be provided via global configuration. */
    @Test
    public void testResolve_addition() throws Exception {
        mResolver =
                new DynamicRemoteFileResolver() {
                    @Override
                    boolean updateProtocols() {
                        // Do not set the static variable
                        return true;
                    }

                    @Override
                    IGlobalConfiguration getGlobalConfig() {
                        IGlobalConfiguration config;
                        try {
                            config =
                                    ConfigurationFactory.getInstance()
                                            .createGlobalConfigurationFromArgs(
                                                    new String[] {"empty"}, new ArrayList<>());
                            // Add a custom object to the resolving map
                            config.setConfigurationObject(
                                    DynamicRemoteFileResolver.DYNAMIC_RESOLVER,
                                    new IRemoteFileResolver() {

                                        @Override
                                        public @Nonnull File resolveRemoteFiles(
                                                File consideredFile, Option option)
                                                throws ConfigurationException {
                                            return mMockResolver.resolveRemoteFiles(
                                                    consideredFile, option);
                                        }

                                        @Override
                                        public @Nonnull String getSupportedProtocol() {
                                            return "fakeprotocol";
                                        }
                                    });

                        } catch (ConfigurationException e) {
                            throw new RuntimeException(e);
                        }
                        return config;
                    }
                };
        RemoteFileOption object = new RemoteFileOption();
        OptionSetter setter =
                new OptionSetter(object) {
                    @Override
                    DynamicRemoteFileResolver createResolver() {
                        return mResolver;
                    }
                };

        File fake = FileUtil.createTempFile("gs-option-setter-test", "txt");

        setter.setOptionValue("remote-file", "fakeprotocol://fake/path");
        assertEquals("fakeprotocol:/fake/path", object.remoteFile.getPath());

        EasyMock.expect(
                        mMockResolver.resolveRemoteFiles(
                                EasyMock.eq(new File("fakeprotocol:/fake/path")),
                                EasyMock.anyObject()))
                .andReturn(fake);
        EasyMock.replay(mMockResolver);

        Set<File> downloadedFile = setter.validateRemoteFilePath();
        try {
            assertEquals(1, downloadedFile.size());
            File downloaded = downloadedFile.iterator().next();
            // The file has been replaced by the downloaded one.
            assertEquals(downloaded.getAbsolutePath(), object.remoteFile.getAbsolutePath());
        } finally {
            for (File f : downloadedFile) {
                FileUtil.recursiveDelete(f);
            }
        }
        EasyMock.verify(mMockResolver);
    }
}
