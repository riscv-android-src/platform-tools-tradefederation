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
package com.android.tradefed.targetprep.multi;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.multi.MixImageZipPreparer.InputStreamFactory;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Unit tests for {@link MixImageZipPreparer}. */
@RunWith(JUnit4.class)
public class MixImageZipPreparerTest {
    // Input build info.
    private static final String VENDOR_IMAGE_NAME = "vendor.img";
    private static final String SYSTEM_IMAGE_NAME = "system.img";
    private static final String VBMETA_IMAGE_NAME = "vbmeta.img";
    private static final String SYSTEM_BUILD_FLAVOR = "system_flavor";
    private static final String SYSTEM_BUILD_ID = "123456";
    private static final String DEVICE_LABEL = "device";
    private static final String SYSTEM_LABEL = "system";
    private static final String RESOURCE_LABEL = "resource";

    // The strings written to temporary image files.
    private static final String DEVICE_CONTENT = "device content";
    private static final String SYSTEM_CONTENT = "system content";
    private static final String RESOURCE_CONTENT = "resource content";

    private IInvocationContext mMockContext;
    private IDeviceBuildInfo mDeviceBuild;
    private IDeviceBuildInfo mSystemBuild;
    private IBuildInfo mResourceBuild;
    private File mDeviceImageZip;
    private File mSystemImageZip;
    private File mResourceDir;

    // The object under test.
    private MixImageZipPreparer mPreparer;

    private static class ByteArrayInputStreamFactory implements InputStreamFactory {
        private final byte[] mData;
        private List<InputStream> createdInputStreams;

        private ByteArrayInputStreamFactory(String data) {
            mData = data.getBytes();
            createdInputStreams = new ArrayList<InputStream>();
        }

        @Override
        public InputStream createInputStream() throws IOException {
            InputStream stream = Mockito.spy(new ByteArrayInputStream(mData));
            createdInputStreams.add(stream);
            return stream;
        }

        @Override
        public long getSize() {
            return mData.length;
        }

        @Override
        public long getCrc32() throws IOException {
            // calculateCrc32 closes the stream.
            return StreamUtil.calculateCrc32(createInputStream());
        }
    }

    private void setUpPreparer() throws IOException {
        mMockContext = Mockito.mock(InvocationContext.class);

        ITestDevice mockDevice = Mockito.mock(ITestDevice.class);
        ITestDevice mockSystem = Mockito.mock(ITestDevice.class);
        mDeviceImageZip =
                createImageZip(
                        DEVICE_CONTENT, VENDOR_IMAGE_NAME, SYSTEM_IMAGE_NAME, VBMETA_IMAGE_NAME);
        mSystemImageZip = createImageZip(SYSTEM_CONTENT, SYSTEM_IMAGE_NAME);
        mDeviceBuild = createDeviceBuildInfo("device_flavor", "device_build_id", mDeviceImageZip);
        mSystemBuild = createDeviceBuildInfo(SYSTEM_BUILD_FLAVOR, SYSTEM_BUILD_ID, mSystemImageZip);

        Mockito.when(mMockContext.getDevice(DEVICE_LABEL)).thenReturn(mockDevice);
        Mockito.when(mMockContext.getBuildInfo(mockDevice)).thenReturn(mDeviceBuild);
        Mockito.when(mMockContext.getDevice(SYSTEM_LABEL)).thenReturn(mockSystem);
        Mockito.when(mMockContext.getBuildInfo(mockSystem)).thenReturn(mSystemBuild);

        mPreparer = new MixImageZipPreparer();
        mPreparer.addSystemFileName(SYSTEM_IMAGE_NAME);
    }

    private void setUpResource() throws IOException {
        ITestDevice mockResource = Mockito.mock(ITestDevice.class);
        mResourceDir = createImageDir(RESOURCE_CONTENT, VBMETA_IMAGE_NAME);
        mResourceBuild = createBuildInfo(mResourceDir);

        Mockito.when(mMockContext.getDevice(RESOURCE_LABEL)).thenReturn(mockResource);
        Mockito.when(mMockContext.getBuildInfo(mockResource)).thenReturn(mResourceBuild);

        mPreparer.addResourceFileName(VBMETA_IMAGE_NAME);
    }

    @After
    public void tearDown() {
        if (mDeviceBuild != null) {
            mDeviceBuild.cleanUp();
            mDeviceBuild = null;
        }
        if (mSystemBuild != null) {
            mSystemBuild.cleanUp();
            mSystemBuild = null;
        }
        if (mResourceBuild != null) {
            mResourceBuild.cleanUp();
            mResourceBuild = null;
        }
        if (mDeviceImageZip != null) {
            mDeviceImageZip.delete();
            mDeviceImageZip = null;
        }
        if (mSystemImageZip != null) {
            mSystemImageZip.delete();
            mSystemImageZip = null;
        }
        if (mResourceDir != null) {
            FileUtil.recursiveDelete(mResourceDir);
            mResourceDir = null;
        }
    }

    private IDeviceBuildInfo createDeviceBuildInfo(
            String buildFlavor, String buildId, File imageZip) {
        IDeviceBuildInfo buildInfo = new DeviceBuildInfo();
        buildInfo.setBuildFlavor(buildFlavor);
        buildInfo.setBuildId(buildId);
        buildInfo.setDeviceImageFile(imageZip, buildId);
        return buildInfo;
    }

    private IBuildInfo createBuildInfo(File rootDir) {
        BuildInfo buildInfo = new BuildInfo();
        for (File file : rootDir.listFiles()) {
            buildInfo.setFile(file.getName(), file, "0");
        }
        return buildInfo;
    }

    private File createImageDir(String content, String... fileNames) throws IOException {
        File tempDir = FileUtil.createTempDir("createImageDir");
        for (String fileName : fileNames) {
            try (FileWriter writer = new FileWriter(new File(tempDir, fileName))) {
                writer.write(content);
            }
        }
        return tempDir;
    }

    private File createImageZip(String content, String... fileNames) throws IOException {
        // = new ArrayList<File>(fileNames.length);
        File tempDir = null;
        try {
            tempDir = createImageDir(content, fileNames);

            ArrayList<File> tempFiles = new ArrayList<File>(fileNames.length);
            for (String fileName : fileNames) {
                tempFiles.add(new File(tempDir, fileName));
            }

            return ZipUtil.createZip(tempFiles);
        } finally {
            FileUtil.recursiveDelete(tempDir);
        }
    }

    private void verifyImage(String content, File dir, String fileName)
            throws FileNotFoundException, IOException {
        try (FileReader reader = new FileReader(new File(dir, fileName))) {
            char[] buffer = new char[content.length()];
            reader.read(buffer);
            Assert.assertEquals(content, new String(buffer));
            Assert.assertTrue("Image contains extra content.", reader.read() < 0);
        }
    }

    private void verifyImageZip(File imageZip) throws FileNotFoundException, IOException {
        File mixedImageDir = ZipUtil.extractZipToTemp(imageZip, "verifyImageZip");
        try {
            verifyImage(DEVICE_CONTENT, mixedImageDir, VENDOR_IMAGE_NAME);
            verifyImage(SYSTEM_CONTENT, mixedImageDir, SYSTEM_IMAGE_NAME);
            if (mResourceBuild != null) {
                verifyImage(RESOURCE_CONTENT, mixedImageDir, VBMETA_IMAGE_NAME);
            }
        } finally {
            FileUtil.recursiveDelete(mixedImageDir);
        }
    }

    private void runPreparerTest()
            throws TargetSetupError, BuildError, DeviceNotAvailableException, ZipException,
                    IOException {
        mPreparer.setUp(mMockContext);

        ArgumentCaptor<IBuildInfo> argument = ArgumentCaptor.forClass(IBuildInfo.class);
        Mockito.verify(mMockContext)
                .addDeviceBuildInfo(Mockito.eq(DEVICE_LABEL), argument.capture());
        IDeviceBuildInfo addedBuildInfo = ((IDeviceBuildInfo) argument.getValue());
        try {
            Assert.assertFalse("Device build is not cleaned up.", mDeviceImageZip.exists());
            mDeviceImageZip = null;
            mDeviceBuild = null;

            Assert.assertEquals(SYSTEM_BUILD_FLAVOR, addedBuildInfo.getBuildFlavor());
            Assert.assertEquals(SYSTEM_BUILD_ID, addedBuildInfo.getDeviceBuildId());
            verifyImageZip(addedBuildInfo.getDeviceImageFile());
        } finally {
            addedBuildInfo.cleanUp();
        }
    }

    /**
     * Test that the mixed {@link IDeviceBuildInfo} contains the resource file and works with
     * non-default compression level.
     */
    @Test
    public void testSetUpWithResource()
            throws TargetSetupError, BuildError, DeviceNotAvailableException, IOException {
        setUpPreparer();
        setUpResource();
        mPreparer.setCompressionLevel(0);
        runPreparerTest();
    }

    /**
     * Test that the mixed {@link IDeviceBuildInfo} contains the system build's image, build flavor,
     * and build id.
     */
    @Test
    public void testSetUpWithSystem()
            throws TargetSetupError, BuildError, DeviceNotAvailableException, IOException {
        setUpPreparer();
        runPreparerTest();
    }

    private void runCreateZipTest(int compressionLevel) throws IOException {
        Map<String, ByteArrayInputStreamFactory> data =
                new HashMap<String, ByteArrayInputStreamFactory>();
        data.put("entry1", new ByteArrayInputStreamFactory("abcabcabcabcabcabc"));
        data.put("entry2", new ByteArrayInputStreamFactory("01230123012301230123"));

        File file = null;
        ZipFile zipFile = null;
        try {
            file = MixImageZipPreparer.createZip(data, compressionLevel);
            zipFile = new ZipFile(file);

            Assert.assertEquals(data.size(), zipFile.stream().count());
            for (Map.Entry<String, ByteArrayInputStreamFactory> entry : data.entrySet()) {
                ByteArrayInputStreamFactory expected = entry.getValue();
                ZipEntry actual = zipFile.getEntry(entry.getKey());
                Assert.assertEquals(expected.getSize(), actual.getSize());
                Assert.assertEquals(expected.getCrc32(), actual.getCrc());
                if (compressionLevel == Deflater.NO_COMPRESSION) {
                    Assert.assertEquals(expected.getSize(), actual.getCompressedSize());
                } else {
                    Assert.assertTrue(expected.getSize() > actual.getCompressedSize());
                }

                for (InputStream stream : expected.createdInputStreams) {
                    Mockito.verify(stream).close();
                }
            }
        } finally {
            ZipUtil.closeZip(zipFile);
            FileUtil.deleteFile(file);
        }
    }

    /** Verify createZip with default compression level. */
    @Test
    public void testCreateZip() throws IOException {
        runCreateZipTest(Deflater.DEFAULT_COMPRESSION);
    }

    /** Verify createZip with no compression. */
    @Test
    public void testCreateZipWithNoCompression() throws IOException {
        runCreateZipTest(Deflater.NO_COMPRESSION);
    }
}
