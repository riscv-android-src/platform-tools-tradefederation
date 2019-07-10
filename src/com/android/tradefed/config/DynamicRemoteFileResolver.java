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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.OptionSetter.OptionFieldsForName;
import com.android.tradefed.config.remote.GcsRemoteFileResolver;
import com.android.tradefed.config.remote.HttpRemoteFileResolver;
import com.android.tradefed.config.remote.HttpsRemoteFileResolver;
import com.android.tradefed.config.remote.IRemoteFileResolver;
import com.android.tradefed.config.remote.LocalFileResolver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.ZipUtil2;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class that helps resolving path to remote files.
 *
 * <p>For example: gs://bucket/path/file.txt will be resolved by downloading the file from the GCS
 * bucket.
 */
public class DynamicRemoteFileResolver {

    public static final String DYNAMIC_RESOLVER = "dynamic-resolver";
    private static final Map<String, IRemoteFileResolver> PROTOCOL_SUPPORT = new HashMap<>();

    static {
        PROTOCOL_SUPPORT.put(GcsRemoteFileResolver.PROTOCOL, new GcsRemoteFileResolver());
        PROTOCOL_SUPPORT.put(LocalFileResolver.PROTOCOL, new LocalFileResolver());
        PROTOCOL_SUPPORT.put(HttpRemoteFileResolver.PROTOCOL_HTTP, new HttpRemoteFileResolver());
        PROTOCOL_SUPPORT.put(HttpsRemoteFileResolver.PROTOCOL_HTTPS, new HttpsRemoteFileResolver());
    }
    // The configuration map being static, we only need to update it once per TF instance.
    private static AtomicBoolean sIsUpdateDone = new AtomicBoolean(false);
    // Query key for requesting to unzip a downloaded file automatically.
    public static final String UNZIP_KEY = "unzip";
    // Query key for requesting a download to be optional, so if it fails we don't replace it.
    public static final String OPTIONAL_KEY = "optional";

    private Map<String, OptionFieldsForName> mOptionMap;

    /** Sets the map of options coming from {@link OptionSetter} */
    public void setOptionMap(Map<String, OptionFieldsForName> optionMap) {
        mOptionMap = optionMap;
    }

    /**
     * Runs through all the {@link File} option type and check if their path should be resolved.
     *
     * @return The list of {@link File} that was resolved that way.
     * @throws ConfigurationException
     */
    public final Set<File> validateRemoteFilePath() throws ConfigurationException {
        Set<File> downloadedFiles = new HashSet<>();
        try {
            Set<Field> fieldSet = new HashSet<>();
            for (Map.Entry<String, OptionFieldsForName> optionPair : mOptionMap.entrySet()) {
                final OptionFieldsForName optionFields = optionPair.getValue();
                for (Map.Entry<Object, Field> fieldEntry : optionFields) {

                    final Object obj = fieldEntry.getKey();

                    final Field field = fieldEntry.getValue();
                    if (fieldSet.contains(field)) {
                        // Avoid reprocessing a Field we already saw.
                        continue;
                    }
                    fieldSet.add(field);
                    final Option option = field.getAnnotation(Option.class);
                    if (option == null) {
                        continue;
                    }
                    // At this point, we know this is an option field; make sure it's set
                    field.setAccessible(true);
                    final Object value;
                    try {
                        value = field.get(obj);
                    } catch (IllegalAccessException e) {
                        throw new ConfigurationException(
                                String.format("internal error: %s", e.getMessage()));
                    }

                    if (value == null) {
                        continue;
                    } else if (value instanceof File) {
                        File consideredFile = (File) value;
                        File downloadedFile = resolveRemoteFiles(consideredFile, option);
                        if (downloadedFile != null) {
                            downloadedFiles.add(downloadedFile);
                            // Replace the field value
                            try {
                                field.set(obj, downloadedFile);
                            } catch (IllegalAccessException e) {
                                CLog.e(e);
                                throw new ConfigurationException(
                                        String.format(
                                                "Failed to download %s due to '%s'",
                                                consideredFile.getPath(), e.getMessage()),
                                        e);
                            }
                        }
                    } else if (value instanceof Collection) {
                        Collection<Object> c = (Collection<Object>) value;
                        Collection<Object> copy = new ArrayList<>(c);
                        for (Object o : copy) {
                            if (o instanceof File) {
                                File consideredFile = (File) o;
                                File downloadedFile = resolveRemoteFiles(consideredFile, option);
                                if (downloadedFile != null) {
                                    downloadedFiles.add(downloadedFile);
                                    // TODO: See if order could be preserved.
                                    c.remove(consideredFile);
                                    c.add(downloadedFile);
                                }
                            }
                        }
                    } else if (value instanceof Map) {
                        Map<Object, Object> m = (Map<Object, Object>) value;
                        Map<Object, Object> copy = new LinkedHashMap<>(m);
                        for (Entry<Object, Object> entry : copy.entrySet()) {
                            Object key = entry.getKey();
                            Object val = entry.getValue();

                            Object finalKey = key;
                            Object finalVal = val;
                            if (key instanceof File) {
                                key = resolveRemoteFiles((File) key, option);
                                if (key != null) {
                                    downloadedFiles.add((File) key);
                                    finalKey = key;
                                }
                            }
                            if (val instanceof File) {
                                val = resolveRemoteFiles((File) val, option);
                                if (val != null) {
                                    downloadedFiles.add((File) val);
                                    finalVal = val;
                                }
                            }

                            m.remove(entry.getKey());
                            m.put(finalKey, finalVal);
                        }
                    } else if (value instanceof MultiMap) {
                        MultiMap<Object, Object> m = (MultiMap<Object, Object>) value;
                        MultiMap<Object, Object> copy = new MultiMap<>(m);
                        for (Object key : copy.keySet()) {
                            List<Object> mapValues = copy.get(key);

                            m.remove(key);
                            Object finalKey = key;
                            if (key instanceof File) {
                                key = resolveRemoteFiles((File) key, option);
                                if (key != null) {
                                    downloadedFiles.add((File) key);
                                    finalKey = key;
                                }
                            }
                            for (Object mapValue : mapValues) {
                                if (mapValue instanceof File) {
                                    File f = resolveRemoteFiles((File) mapValue, option);
                                    if (f != null) {
                                        downloadedFiles.add(f);
                                        mapValue = f;
                                    }
                                }
                                m.put(finalKey, mapValue);
                            }
                        }
                    }
                }
            }
        } catch (ConfigurationException e) {
            // Clean up the files before throwing
            for (File f : downloadedFiles) {
                FileUtil.recursiveDelete(f);
            }
            throw e;
        }
        return downloadedFiles;
    }

    /**
     * Download the files matching given filters in a remote zip file.
     *
     * <p>A file inside the remote zip file is only downloaded if its path matches any of the
     * include filters but not the exclude filters.
     *
     * @param destDir the file to place the downloaded contents into.
     * @param remoteZipFilePath the remote path to the zip file to download, relative to an
     *     implementation specific root.
     * @param includeFilters a list of regex strings to download matching files. A file's path
     *     matching any filter will be downloaded.
     * @param excludeFilters a list of regex strings to skip downloading matching files. A file's
     *     path matching any filter will not be downloaded.
     * @throws ConfigurationException if files could not be downloaded.
     */
    public void resolvePartialDownloadZip(
            File destDir,
            String remoteZipFilePath,
            List<String> includeFilters,
            List<String> excludeFilters)
            throws ConfigurationException {
        Map<String, String> queryArgs;
        String protocol;
        try {
            URI uri = new URI(remoteZipFilePath);
            protocol = uri.getScheme();
            queryArgs = parseQuery(uri.getQuery());
        } catch (URISyntaxException e) {
            throw new ConfigurationException(
                    String.format(
                            "Failed to parse the remote zip file path: %s", remoteZipFilePath),
                    e);
        }
        IRemoteFileResolver resolver = getResolver(protocol);

        queryArgs.put("partial_download_dir", destDir.getAbsolutePath());
        if (includeFilters != null) {
            queryArgs.put("include_filters", String.join(";", includeFilters));
        }
        if (excludeFilters != null) {
            queryArgs.put("exclude_filters", String.join(";", excludeFilters));
        }
        // Downloaded individual files should be saved to destDir, return value is not needed.
        try {
            resolver.resolveRemoteFiles(new File(remoteZipFilePath), null, queryArgs);
        } catch (ConfigurationException e) {
            if (isOptional(queryArgs)) {
                CLog.d(
                        "Failed to partially download '%s' but marked optional so skipping: %s",
                        remoteZipFilePath, e.getMessage());
            } else {
                throw e;
            }
        }
    }

    @VisibleForTesting
    protected IRemoteFileResolver getResolver(String protocol) {
        if (updateProtocols()) {
            IGlobalConfiguration globalConfig = getGlobalConfig();
            Object o = globalConfig.getConfigurationObject(DYNAMIC_RESOLVER);
            if (o != null) {
                if (o instanceof IRemoteFileResolver) {
                    IRemoteFileResolver resolver = (IRemoteFileResolver) o;
                    CLog.d("Adding %s to supported remote file resolver", resolver);
                    PROTOCOL_SUPPORT.put(resolver.getSupportedProtocol(), resolver);
                } else {
                    CLog.e("%s is not of type IRemoteFileResolver", o);
                }
            }
        }
        return PROTOCOL_SUPPORT.get(protocol);
    }

    @VisibleForTesting
    protected boolean updateProtocols() {
        return sIsUpdateDone.compareAndSet(false, true);
    }

    @VisibleForTesting
    IGlobalConfiguration getGlobalConfig() {
        return GlobalConfiguration.getInstance();
    }

    /**
     * Utility that allows to check whether or not a file should be unzip and unzip it if required.
     */
    public static final File unzipIfRequired(File downloadedFile, Map<String, String> query)
            throws IOException {
        String unzipValue = query.get(UNZIP_KEY);
        if (unzipValue != null && "true".equals(unzipValue.toLowerCase())) {
            // File was requested to be unzipped.
            if (ZipUtil.isZipFileValid(downloadedFile, false)) {
                File unzipped =
                        ZipUtil2.extractZipToTemp(
                                downloadedFile, FileUtil.getBaseName(downloadedFile.getName()));
                FileUtil.deleteFile(downloadedFile);
                return unzipped;
            } else {
                CLog.w("%s was requested to be unzipped but is not a valid zip.", downloadedFile);
            }
        }
        // Return the original file untouched
        return downloadedFile;
    }

    private File resolveRemoteFiles(File consideredFile, Option option)
            throws ConfigurationException {
        File fileToResolve;
        String path = consideredFile.getPath();
        String protocol;
        Map<String, String> query;
        try {
            URI uri = new URI(path);
            protocol = uri.getScheme();
            query = parseQuery(uri.getQuery());
            fileToResolve = new File(protocol + ":" + uri.getPath());
        } catch (URISyntaxException e) {
            CLog.e(e);
            return null;
        }
        IRemoteFileResolver resolver = getResolver(protocol);
        if (resolver != null) {
            try {
                return resolver.resolveRemoteFiles(fileToResolve, option, query);
            } catch (ConfigurationException e) {
                if (isOptional(query)) {
                    CLog.d(
                            "Failed to resolve '%s' but marked optional so skipping: %s",
                            fileToResolve, e.getMessage());
                } else {
                    throw e;
                }
            }
        }
        // Not a remote file
        return null;
    }

    /**
     * Parse a URL query style. Delimited by &, and map values represented by =. Example:
     * ?key=value&key2=value2
     */
    private Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null) {
            return values;
        }
        for (String maps : query.split("&")) {
            String[] keyVal = maps.split("=");
            values.put(keyVal[0], keyVal[1]);
        }
        return values;
    }

    /** Whether or not a link was requested as optional. */
    private boolean isOptional(Map<String, String> query) {
        String value = query.get(OPTIONAL_KEY);
        if (value == null) {
            return false;
        }
        return "true".equals(value.toLowerCase());
    }
}
