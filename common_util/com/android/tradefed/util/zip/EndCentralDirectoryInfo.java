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

package com.android.tradefed.util.zip;

import com.android.tradefed.util.ByteArrayUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * EndCentralDirectoryInfo is a class containing the overall information of a zip file. It's at the
 * end of the zip file.
 *
 * <p>Overall zipfile format: [Local file header + Compressed data [+ Extended local header]?]*
 * [Central directory]* [End of central directory record]
 *
 * <p>Refer to following link for more details: https://en.wikipedia.org/wiki/Zip_(file_format)
 */
public final class EndCentralDirectoryInfo {

    // End central directory of a zip file is at the end of the file, and its size shouldn't be
    // larger than 64k.
    public static final int MAX_LOOKBACK = 64 * 1024;

    private static final byte[] END_CENTRAL_DIRECTORY_SIGNATURE = {0x50, 0x4b, 0x05, 0x06};
    // Central directory signature is always 4 bytes.
    private static final int CENTRAL_DIRECTORY_MAGIC_LENGTH = 4;

    private int mEntryNumber;
    private long mCentralDirSize;
    private long mCentralDirOffset;

    public int getEntryNumber() {
        return mEntryNumber;
    }

    public long getCentralDirSize() {
        return mCentralDirSize;
    }

    public long getCentralDirOffset() {
        return mCentralDirOffset;
    }

    /**
     * Constructor to collect end central directory information of a zip file.
     *
     * @param zipFile a {@link File} contains the end central directory information. It's likely the
     *     ending part of the zip file.
     * @throws IOException
     */
    public EndCentralDirectoryInfo(File zipFile) throws IOException {
        // End of central directory record:
        //    Offset   Length   Contents
        //      0      4 bytes  End of central dir signature (0x06054b50)
        //      4      2 bytes  Number of this disk
        //      6      2 bytes  Number of the disk with the start of the central directory
        //      8      2 bytes  Total number of entries in the central dir on this disk
        //     10      2 bytes  Total number of entries in the central dir
        //     12      4 bytes  Size of the central directory
        //     16      4 bytes  Offset of start of central directory with respect to the starting
        //                      disk number
        //     20      2 bytes  zipfile comment length (c)
        //     22     (c)bytes  zipfile comment

        try (FileInputStream stream = new FileInputStream(zipFile)) {
            long size = stream.getChannel().size();
            if (size > MAX_LOOKBACK) {
                stream.skip(size - MAX_LOOKBACK);
                size = MAX_LOOKBACK;
            }
            byte[] endCentralDir = new byte[(int) size];
            stream.read(endCentralDir);
            int offset = (int) size - CENTRAL_DIRECTORY_MAGIC_LENGTH - 1;
            // Seek from the end of the file, searching for the end central directory signature.
            while (offset >= 0) {
                if (!java.util.Arrays.equals(
                        END_CENTRAL_DIRECTORY_SIGNATURE,
                        Arrays.copyOfRange(endCentralDir, offset, offset + 4))) {
                    offset--;
                    continue;
                }
                // Get the total number of entries in the central directory
                mEntryNumber = ByteArrayUtil.getInt(endCentralDir, offset + 10, 2);
                // Get the size of the central directory block
                mCentralDirSize = ByteArrayUtil.getLong(endCentralDir, offset + 12, 4);
                // Get the offset of start of central directory
                mCentralDirOffset = ByteArrayUtil.getLong(endCentralDir, offset + 16, 4);

                if (mCentralDirOffset < 0) {
                    throw new IOException(
                            "Failed to get offset of EndCentralDirectoryInfo. Partial unzip doesn't support zip files larger than 4GB.");
                }
                break;
            }
            if (offset < 0) {
                throw new RuntimeException(
                        "Failed to find end central directory info for zip file: "
                                + zipFile.getPath());
            }
        }
    }
}
