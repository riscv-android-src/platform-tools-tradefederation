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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildInfo.BuildInfoProperties;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** An {@link IMultiTargetPreparer} that mixes a system build's images in a device build. */
@OptionClass(alias = "mix-image-zip")
public class MixImageZipPreparer extends BaseMultiTargetPreparer {

    @Option(name = "device-label", description = "the label for the device.")
    private String mDeviceLabel = "device";

    @Option(
        name = "system-label",
        description = "the label for the null-device used to store the system image information."
    )
    private String mSystemLabel = "system";

    @Option(
        name = "resource-label",
        description = "the label for the null-device used to store the extra build information."
    )
    private String mResourceLabel = "resource";

    @Option(
        name = "extra-build-test-resource-name",
        description =
                "the name of the extra build file copied to device build. " + "Can be repeated."
    )
    private Set<String> mExtraBuildResourceFiles = new TreeSet<>();

    @Option(
        name = "system-build-file-name",
        description =
                "the name of the image file copied from system build to device build. "
                        + "Can be repeated.",
        mandatory = true
    )
    private Set<String> mSystemFileNames = new TreeSet<>();

    @Option(
        name = "compression-level",
        description =
                "the compression level of the mixed image zip. It is an integer between 0 "
                        + "and 9. Larger value indicates longer time and smaller output."
    )
    private int mCompressionLevel = Deflater.DEFAULT_COMPRESSION;

    /** The interface that creates {@link InputStream} from a file or a compressed file. */
    @VisibleForTesting
    static interface InputStreamFactory {
        /** Create a new stream. The caller should close it. */
        InputStream createInputStream() throws IOException;

        /** Return the uncompressed size of the data. */
        long getSize();

        /** Return the CRC32 of the data. */
        long getCrc32() throws IOException;
    }

    @Override
    public void setUp(IInvocationContext context)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {

        ITestDevice device = context.getDevice(mDeviceLabel);
        IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) context.getBuildInfo(device);

        ITestDevice systemNullDevice = context.getDevice(mSystemLabel);
        IDeviceBuildInfo systemBuildInfo =
                (IDeviceBuildInfo) context.getBuildInfo(systemNullDevice);

        IBuildInfo resourceBuildInfo = null;
        if (!mExtraBuildResourceFiles.isEmpty()) {
            ITestDevice resourceNullDevice = context.getDevice(mResourceLabel);
            resourceBuildInfo = context.getBuildInfo(resourceNullDevice);
        }

        ZipFile deviceImageZip = null;
        ZipFile systemImageZip = null;
        File mixedImageZip = null;
        try {
            deviceImageZip = new ZipFile(deviceBuildInfo.getDeviceImageFile());

            // Get all files from device build.
            Map<String, InputStreamFactory> files =
                    getInputStreamFactoriesFromImageZip(deviceImageZip, file -> true);
            Map<String, InputStreamFactory> filesNotInDeviceBuild =
                    new HashMap<String, InputStreamFactory>();

            // Get specified files from system build and replace those in device build.
            systemImageZip = new ZipFile(systemBuildInfo.getDeviceImageFile());
            Map<String, InputStreamFactory> systemFiles =
                    getInputStreamFactoriesFromImageZip(
                            systemImageZip, file -> mSystemFileNames.contains(file));
            systemFiles = replaceExistingEntries(systemFiles, files);
            filesNotInDeviceBuild.putAll(systemFiles);

            if (resourceBuildInfo != null) {
                // Get specified files from resource build and replace those in device build.
                Map<String, InputStreamFactory> resourceFiles =
                        getBuildFiles(resourceBuildInfo, mExtraBuildResourceFiles);
                resourceFiles = replaceExistingEntries(resourceFiles, files);
                filesNotInDeviceBuild.putAll(resourceFiles);
            }

            if (!filesNotInDeviceBuild.isEmpty()) {
                throw new TargetSetupError(
                        String.join(",", filesNotInDeviceBuild.keySet()) + " not in device build.",
                        device.getDeviceDescriptor());
            }

            CLog.d("Create mixed image zip.");
            mixedImageZip = createZip(files, mCompressionLevel);
        } catch (IOException e) {
            throw new TargetSetupError(
                    "Could not create mixed image zip", e, device.getDeviceDescriptor());
        } finally {
            ZipUtil.closeZip(deviceImageZip);
            ZipUtil.closeZip(systemImageZip);
        }

        IBuildInfo mixedBuildInfo =
                createBuildCopy(
                        deviceBuildInfo,
                        systemBuildInfo.getBuildFlavor(),
                        systemBuildInfo.getBuildId(),
                        mixedImageZip);
        // Replace the build
        context.addDeviceBuildInfo(mDeviceLabel, mixedBuildInfo);
        // Clean up the original build
        deviceBuildInfo.cleanUp();
    }

    /**
     * Get {@link InputStreamFactory} from entries in an image zip. The zip must not be closed when
     * the returned {@link InputStreamFactory} are in use.
     *
     * @param zipFile image zip.
     * @param predicate function that takes a file name as the argument and determines whether the
     *     file name and the content should be added to the output map.
     * @return map from file name to {@link InputStreamFactory}.
     * @throws IOException if fails to create the temporary directory.
     */
    private static Map<String, InputStreamFactory> getInputStreamFactoriesFromImageZip(
            final ZipFile zipFile, Predicate<String> predicate) throws IOException {
        Map<String, InputStreamFactory> factories = new HashMap<String, InputStreamFactory>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                CLog.w("Image zip contains subdirectory %s.", entry.getName());
                continue;
            }

            String name = new File(entry.getName()).getName();
            if (!predicate.test(name)) {
                continue;
            }

            if (entry.getSize() < 0) {
                throw new IllegalArgumentException("Invalid size.");
            }
            if (entry.getCrc() < 0) {
                throw new IllegalArgumentException("Invalid CRC value.");
            }

            factories.put(
                    name,
                    new InputStreamFactory() {
                        @Override
                        public InputStream createInputStream() throws IOException {
                            return zipFile.getInputStream(entry);
                        }

                        @Override
                        public long getSize() {
                            return entry.getSize();
                        }

                        @Override
                        public long getCrc32() {
                            return entry.getCrc();
                        }
                    });
        }
        return factories;
    }

    /**
     * Get {@link InputStreamFactory} from {@link IBuildInfo} by name.
     *
     * @param buildInfo {@link IBuildInfo} that contains files.
     * @param buildFileNames collection of file names.
     * @return map from file name to {@link InputStreamFactory}.
     * @throws IOException if fails to get files from the build info.
     */
    private static Map<String, InputStreamFactory> getBuildFiles(
            IBuildInfo buildInfo, Collection<String> buildFileNames) throws IOException {
        Map<String, InputStreamFactory> factories = new HashMap<String, InputStreamFactory>();
        for (String fileName : buildFileNames) {
            final File file = buildInfo.getFile(fileName);
            if (file == null) {
                throw new IOException(String.format("Could not get file with name: %s", fileName));
            }
            factories.put(
                    fileName,
                    new InputStreamFactory() {
                        @Override
                        public InputStream createInputStream() throws IOException {
                            return new FileInputStream(file);
                        }

                        @Override
                        public long getSize() {
                            return file.length();
                        }

                        @Override
                        public long getCrc32() throws IOException {
                            return FileUtil.calculateCrc32(file);
                        }
                    });
        }
        return factories;
    }

    private static void initStoredZipEntry(ZipEntry entry, InputStreamFactory factory)
            throws IOException {
        entry.setMethod(ZipOutputStream.STORED);
        entry.setCompressedSize(factory.getSize());
        entry.setSize(factory.getSize());
        entry.setCrc(factory.getCrc32());
    }

    /**
     * Create a zip file from {@link InputStreamFactory} instances.
     *
     * @param factories the map where the keys are the entry names and the values provide the data
     *     to be compressed.
     * @param compressionLevel an integer between 0 and 9. If the value is 0, this method creates
     *     {@link ZipOutputStream#STORED} entries instead of default ones.
     * @return the created zip file in temporary directory.
     * @throws IOException if any file operation fails.
     */
    @VisibleForTesting
    static File createZip(Map<String, ? extends InputStreamFactory> factories, int compressionLevel)
            throws IOException {
        File zipFile = null;
        OutputStream out = null;
        try {
            zipFile = FileUtil.createTempFile("MixedImg", ".zip");
            out = new FileOutputStream(zipFile);
            out = new BufferedOutputStream(out);
            out = new ZipOutputStream(out);
            ZipOutputStream zipOut = (ZipOutputStream) out;
            zipOut.setLevel(compressionLevel);

            for (Map.Entry<String, ? extends InputStreamFactory> factory : factories.entrySet()) {
                ZipEntry entry = new ZipEntry(factory.getKey());
                // STORED is faster than the default DEFLATED in no compression mode.
                if (compressionLevel == Deflater.NO_COMPRESSION) {
                    initStoredZipEntry(entry, factory.getValue());
                }
                zipOut.putNextEntry(entry);
                try (InputStream in =
                        new BufferedInputStream(factory.getValue().createInputStream())) {
                    StreamUtil.copyStreams(in, zipOut);
                }
                zipOut.closeEntry();
            }

            File returnValue = zipFile;
            zipFile = null;
            return returnValue;
        } finally {
            StreamUtil.close(out);
            FileUtil.deleteFile(zipFile);
        }
    }

    private static IBuildInfo createBuildCopy(
            IDeviceBuildInfo deviceBuildInfo, String buildFlavor, String buildId, File imageZip) {
        deviceBuildInfo.setProperties(BuildInfoProperties.DO_NOT_COPY_IMAGE_FILE);
        IDeviceBuildInfo newBuildInfo = (IDeviceBuildInfo) deviceBuildInfo.clone();
        newBuildInfo.setBuildFlavor(buildFlavor);
        newBuildInfo.setDeviceImageFile(imageZip, buildId);
        return newBuildInfo;
    }

    /**
     * Replace the values if the keys exists in the map.
     *
     * @param replacement the map containing the entries to be added to the original map.
     * @param original the map whose entries are replaced.
     * @return the entries which are in the replacement map but not added to the original map.
     */
    private static <T> Map<String, T> replaceExistingEntries(
            Map<String, T> replacement, Map<String, T> original) {
        Map<String, T> remaining = new HashMap<String, T>();
        for (Map.Entry<String, T> entry : replacement.entrySet()) {
            String key = entry.getKey();
            if (original.containsKey(key)) {
                original.put(key, entry.getValue());
            } else {
                remaining.put(key, entry.getValue());
            }
        }
        return remaining;
    }

    @VisibleForTesting
    void addSystemFileName(String fileName) {
        mSystemFileNames.add(fileName);
    }

    @VisibleForTesting
    void addResourceFileName(String fileName) {
        mExtraBuildResourceFiles.add(fileName);
    }

    @VisibleForTesting
    void setCompressionLevel(int compressionLevel) {
        mCompressionLevel = compressionLevel;
    }
}
