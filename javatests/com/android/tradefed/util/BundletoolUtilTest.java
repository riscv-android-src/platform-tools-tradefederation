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

package com.android.tradefed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Unit tests for {@link BundletoolUtil} */
@RunWith(JUnit4.class)
public class BundletoolUtilTest {
    @Mock ITestDevice mMockDevice;
    @Mock IBuildInfo mMockBuildInfo;
    @Mock IRunUtil mMockRuntil;
    private BundletoolUtil mBundletoolUtil;
    private File mBundletoolJar;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockDevice.getSerialNumber()).thenReturn("serial");
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        mBundletoolJar = File.createTempFile("bundletool", ".jar");
        mBundletoolUtil =
                new BundletoolUtil(mBundletoolJar) {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRuntil;
                    }

                    @Override
                    protected String getAdbPath() {
                        return "adb";
                    }

                    @Override
                    protected File getBundletoolFile() {
                        return mBundletoolJar;
                    }
                };
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mBundletoolJar);
    }

    @Test
    public void testGetBundletoolFile() throws Exception {
        mBundletoolUtil = new BundletoolUtil(mBundletoolJar);

        assertEquals(mBundletoolUtil.getBundletoolFile(), mBundletoolJar);
    }

    @Test
    public void testGenerateDeviceSpecFile() throws Exception {
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.SUCCESS);
        when(mMockRuntil.runTimedCmd(
                        (Long) Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any()))
                .thenReturn(res);
        Path expectedSpecFilePath =
                Paths.get(mBundletoolJar.getParentFile().getAbsolutePath(), "serial.json");

        String actualSpecFilePath = mBundletoolUtil.generateDeviceSpecFile(mMockDevice);
        assertEquals(expectedSpecFilePath.toString(), actualSpecFilePath);
        verify(mMockRuntil, times(1))
                .runTimedCmd(
                        (Long) Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any());
    }

    @Test
    public void testextractSplitsFromApks() throws Exception {
        File fakeApks = File.createTempFile("fakeApks", ".apks");

        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.SUCCESS);
        when(mMockRuntil.runTimedCmd(
                        (Long) Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any()))
                .thenReturn(res);

        File splits =
                mBundletoolUtil.extractSplitsFromApks(
                        fakeApks, "/tmp/serial.json", mMockDevice, mMockBuildInfo);
        verify(mMockRuntil, times(1))
                .runTimedCmd(
                        (Long) Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any());

        FileUtil.deleteFile(fakeApks);
        FileUtil.deleteFile(splits);
        assertTrue(!fakeApks.exists());
        assertTrue(!splits.exists());
    }

    @Test
    public void test_extractSplitsFromApksFail() throws Exception {
        File fakeApks = File.createTempFile("fakeApks", ".apks");
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.FAILED);
        when(mMockRuntil.runTimedCmd(
                        (Long) Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any()))
                .thenReturn(res);

        File splits = null;

        splits =
                mBundletoolUtil.extractSplitsFromApks(
                        fakeApks, "/tmp/serial.json", mMockDevice, mMockBuildInfo);
        verify(mMockRuntil, times(1))
                .runTimedCmd(
                        (Long) Mockito.anyLong(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any(),
                        (String) Mockito.any());

        FileUtil.deleteFile(fakeApks);
        assertNull(splits);
    }
}
