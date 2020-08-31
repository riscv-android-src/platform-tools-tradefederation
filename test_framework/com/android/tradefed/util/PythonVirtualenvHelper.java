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

package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;

public class PythonVirtualenvHelper {

    private static final String PATH = "PATH";
    private static final String PYTHONHOME = "PYTHONHOME";
    private static final String PYTHONPATH = "PYTHONPATH";
    public static final String VIRTUAL_ENV = "VIRTUAL_ENV";

    /**
     * Gets python bin directory path.
     *
     * <p>This method will check the directory existence.
     *
     * @return python bin directory; null if not exist.
     */
    public static String getPythonBinDir(String virtualenvPath) {
        if (virtualenvPath == null) {
            return null;
        }
        File res = new File(virtualenvPath, "bin");
        if (!res.exists()) {
            return null;
        }
        return res.getAbsolutePath();
    }

    /**
     * Activate virtualenv for a RunUtil.
     *
     * @param runUtil an utility object for running virtualenv activation commands.
     * @param virtualenvDir a File object representing the created virtualenv directory.
     */
    public static void activate(IRunUtil runUtil, File virtualenvDir) {
        activate(runUtil, virtualenvDir.getAbsolutePath());
    }

    /**
     * Activate virtualenv for a RunUtil.
     *
     * <p>This method will check for python bin directory existence
     *
     * @param runUtil an utility object for running virtualenv activation commands.
     * @param virtualenvPath the path to the created virtualenv directory.
     */
    public static void activate(IRunUtil runUtil, String virtualenvPath) {
        String pythonBinDir = getPythonBinDir(virtualenvPath);
        if (pythonBinDir == null) {
            CLog.e("Invalid python virtualenv path. Using python from system path.");
            // TODO(b/166688151): throw an exception to fail early.
            return;
        }
        String separater = ":";
        String pythonPath =
                new File(virtualenvPath, "lib/python3.8/site-packages").getAbsolutePath()
                        + separater
                        + System.getenv(PYTHONPATH);
        runUtil.setEnvVariable(PATH, pythonBinDir + separater + System.getenv().get(PATH));
        runUtil.setEnvVariable(VIRTUAL_ENV, virtualenvPath);
        runUtil.setEnvVariable(PYTHONPATH, pythonPath);
        runUtil.unsetEnvVariable(PYTHONHOME);
        CLog.d("Set environment variables:");
        CLog.d("%s: %s", PATH, pythonBinDir + separater + System.getenv().get(PATH));
        CLog.d("%s: %s", VIRTUAL_ENV, virtualenvPath);
        CLog.d("%s: %s", PYTHONPATH, pythonPath);
    }
}
