/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.result.InputStreamSource;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides an reliable and slightly higher level API to a ddmlib {@link IDevice}.
 * <p/>
 * Retries device commands for a configurable amount, and provides a device recovery
 * interface for devices which are unresponsive.
 */
public interface ITestDevice extends INativeDevice {

    public enum RecoveryMode {
        /** don't attempt to recover device. */
        NONE,
        /** recover device to online state only */
        ONLINE,
        /**
         * Recover device into fully testable state - framework is up, and external storage is
         * mounted.
         */
        AVAILABLE
    }

    /**
     * A simple struct class to store information about a single mountpoint
     */
    public static class MountPointInfo {
        public String filesystem;
        public String mountpoint;
        public String type;
        public List<String> options;

        /** Simple constructor */
        public MountPointInfo() {}

        /**
         * Convenience constructor to set all members
         */
        public MountPointInfo(String filesystem, String mountpoint, String type,
                List<String> options) {
            this.filesystem = filesystem;
            this.mountpoint = mountpoint;
            this.type = type;
            this.options = options;
        }

        public MountPointInfo(String filesystem, String mountpoint, String type, String optString) {
            this(filesystem, mountpoint, type, splitMountOptions(optString));
        }

        public static List<String> splitMountOptions(String options) {
            List<String> list = Arrays.asList(options.split(","));
            return list;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s %s", this.filesystem, this.mountpoint, this.type,
                    this.options);
        }
    }

    /**
     * Install an Android package on device.
     *
     * @param packageFile the apk file to install
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String installPackage(File packageFile, boolean reinstall, String... extraArgs)
            throws DeviceNotAvailableException;

    /**
     * Install an Android package on device.
     * <p>Note: Only use cases that requires explicit control of granting runtime permission at
     * install time should call this function.
     * @param packageFile the apk file to install
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param grantPermissions if all runtime permissions should be granted at install time
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     * @throws UnsupportedOperationException if runtime permission is not supported by the platform
     *         on device.
     */
    public String installPackage(File packageFile, boolean reinstall, boolean grantPermissions,
            String... extraArgs) throws DeviceNotAvailableException;

    /**
     * Install an Android package on device for a given user.
     *
     * @param packageFile the apk file to install
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param userId the integer user id to install for.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String installPackageForUser(File packageFile, boolean reinstall, int userId,
            String... extraArgs) throws DeviceNotAvailableException;

    /**
     * Install an Android package on device for a given user.
     * <p>Note: Only use cases that requires explicit control of granting runtime permission at
     * install time should call this function.
     * @param packageFile the apk file to install
     * @param reinstall <code>true</code> if a reinstall should be performed
     * @param grantPermissions if all runtime permissions should be granted at install time
     * @param userId the integer user id to install for.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     *            available options.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     * @throws UnsupportedOperationException if runtime permission is not supported by the platform
     *         on device.
     */
    public String installPackageForUser(File packageFile, boolean reinstall,
            boolean grantPermissions, int userId, String... extraArgs)
                    throws DeviceNotAvailableException;

    /**
     * Uninstall an Android package from device.
     *
     * @param packageName the Android package to uninstall
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String uninstallPackage(String packageName) throws DeviceNotAvailableException;

    /**
     * Retrieves a bugreport from the device.
     * <p/>
     * The implementation of this is guaranteed to continue to work on a device without an sdcard
     * (or where the sdcard is not yet mounted).
     *
     * @return An {@link InputStreamSource} which will produce the bugreport contents on demand.  In
     *         case of failure, the {@code InputStreamSource} will produce an empty
     *         {@link InputStream}.
     */
    public InputStreamSource getBugreport();

    /**
     * Retrieves a file off device.
     *
     * @param remoteFilePath the absolute path to file on device.
     * @param localFile the local file to store contents in. If non-empty, contents will be
     *            replaced.
     * @return <code>true</code> if file was retrieved successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean pullFile(String remoteFilePath, File localFile)
            throws DeviceNotAvailableException;

    /**
     * Retrieves a file off device, stores it in a local temporary {@link File}, and returns that
     * {@code File}.
     *
     * @param remoteFilePath the absolute path to file on device.
     * @return A {@link File} containing the contents of the device file, or {@code null} if the
     *         copy failed for any reason (including problems with the host filesystem)
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public File pullFile(String remoteFilePath) throws DeviceNotAvailableException;

    /**
     * A convenience method to retrieve a file from the device's external storage, stores it in a
     * local temporary {@link File}, and return a reference to that {@code File}.
     *
     * @param remoteFilePath the path to file on device, relative to the device's external storage
     *        mountpoint
     * @return A {@link File} containing the contents of the device file, or {@code null} if the
     *         copy failed for any reason (including problems with the host filesystem)
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public File pullFileFromExternal(String remoteFilePath) throws DeviceNotAvailableException;

    /**
     * Push a file to device
     *
     * @param localFile the local file to push
     * @param deviceFilePath the remote destination absolute file path
     * @return <code>true</code> if file was pushed successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pushFile(File localFile, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Push file created from a string to device
     *
     * @param contents the contents of the file to push
     * @param deviceFilePath the remote destination absolute file path
     * @return <code>true</code> if string was pushed successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pushString(String contents, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Recursively push directory contents to device.
     *
     * @param localDir the local directory to push
     * @param deviceFilePath the absolute file path of the remote destination
     * @return <code>true</code> if file was pushed successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pushDir(File localDir, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Recursively pull directory contents from device.
     *
     * @param deviceFilePath the absolute file path of the remote source
     * @param localDir the local directory to pull files into
     * @return <code>true</code> if file was pulled successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean pullDir(String deviceFilePath, File localDir)
            throws DeviceNotAvailableException;

    /**
     * Incrementally syncs the contents of a local file directory to device.
     * <p/>
     * Decides which files to push by comparing timestamps of local files with their remote
     * equivalents. Only 'newer' or non-existent files will be pushed to device. Thus overhead
     * should be relatively small if file set on device is already up to date.
     * <p/>
     * Hidden files (with names starting with ".") will be ignored.
     * <p/>
     * Example usage: syncFiles("/tmp/files", "/sdcard") will created a /sdcard/files directory if
     * it doesn't already exist, and recursively push the /tmp/files contents to /sdcard/files.
     *
     * @param localFileDir the local file directory containing files to recursively push.
     * @param deviceFilePath the remote destination absolute file path root. All directories in thos
     *            file path must be readable. ie pushing to /data/local/tmp when adb is not root
     *            will fail
     * @return <code>true</code> if files were synced successfully. <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean syncFiles(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException;

    /**
     * Helper method to determine if file on device exists.
     *
     * @param deviceFilePath the absolute path of file on device to check
     * @return <code>true</code> if file exists, <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public boolean doesFileExist(String deviceFilePath) throws DeviceNotAvailableException;

    /**
     * Helper method to determine amount of free space on device external storage.
     *
     * @return the amount of free space in KB
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    public long getExternalStoreFreeSpace() throws DeviceNotAvailableException;

    /**
     * Retrieve a reference to a remote file on device.
     *
     * @param path the file path to retrieve. Can be an absolute path or path relative to '/'. (ie
     *            both "/system" and "system" syntax is supported)
     * @return the {@link IFileEntry} or <code>null</code> if file at given <var>path</var> cannot
     *         be found
     * @throws DeviceNotAvailableException
     */
    public IFileEntry getFileEntry(String path) throws DeviceNotAvailableException;

    /**
     * Return True if the path on the device is a directory, false otherwise.
     *
     * @throws DeviceNotAvailableException
     */
    public boolean isDirectory(String deviceFilePath) throws DeviceNotAvailableException;

    /**
     * Alternative to using {@link IFileEntry} that sometimes won't work because of permissions.
     *
     * @param deviceFilePath is the path on the device where to do the search
     * @return Array of string containing all the file in a path on the device.
     * @throws DeviceNotAvailableException
     */
    public String[] getChildren(String deviceFilePath) throws DeviceNotAvailableException;

    /**
     * Start capturing logcat output from device in the background.
     * <p/>
     * Will have no effect if logcat output is already being captured.
     * Data can be later retrieved via getLogcat.
     * <p/>
     * When the device is no longer in use, {@link #stopLogcat()} must be called.
     * <p/>
     * {@link #startLogcat()} and {@link #stopLogcat()} do not normally need to be called when
     * within a TF invocation context, as the TF framework will start and stop logcat.
     */
    public void startLogcat();

    /**
     * Stop capturing logcat output from device, and discard currently saved logcat data.
     * <p/>
     * Will have no effect if logcat output is not being captured.
     */
    public void stopLogcat();

    /**
     * Deletes any accumulated logcat data.
     * <p/>
     * This is useful for cases when you want to ensure {@link ITestDevice#getLogcat()} only returns
     * log data produced after a certain point (such as after flashing a new device build, etc).
     */
    public void clearLogcat();

    /**
     * Grabs a snapshot stream of the logcat data.
     * <p/>
     * Works in two modes:
     * <li>If the logcat is currently being captured in the background, will return up to
     * {@link TestDeviceOptions#getMaxLogcatDataSize()} bytes of the current
     * contents of the background logcat capture
     * <li>Otherwise, will return a static dump of the logcat data if device is currently responding
     */
    public InputStreamSource getLogcat();

    /**
     * Grabs a snapshot stream of the last <code>maxBytes</code> of captured logcat data.
     * <p/>
     * Useful for cases when you want to capture frequent snapshots of the captured logcat data
     * without incurring the potentially big disk space penalty of getting the entire
     * {@link #getLogcat()} snapshot.
     *
     * @param maxBytes the maximum amount of data to return. Should be an amount that can
     *            comfortably fit in memory
     */
    public InputStreamSource getLogcat(int maxBytes);

    /**
     * Grabs a snapshot stream of captured logcat data starting the date provided.
     * The time on the device should be used {@link #getDeviceDate}.
     * <p/>
     * @param date in epoch format of when to start the snapshot until present. (can be
     *        be obtained using 'date +%s')
     */
    public InputStreamSource getLogcatSince(long date);

    /**
    * Get a dump of the current logcat for device. Unlike {@link #getLogcat()}, this method will
    * always return a static dump of the logcat.
    * <p/>
    * Has the disadvantage that nothing will be returned if device is not reachable.
    *
    * @return a {@link InputStreamSource} of the logcat data. An empty stream is returned if fail to
    *         capture logcat data.
    */
    public InputStreamSource getLogcatDump();

    /**
     * Grabs a screenshot from the device.
     *
     * @return a {@link InputStreamSource} of the screenshot in png format, or <code>null</code> if
     *         the screenshot was not successful.
     * @throws DeviceNotAvailableException
     */
    public InputStreamSource getScreenshot() throws DeviceNotAvailableException;

    /**
     * Grabs a screenshot from the device.
     * Recommended to use getScreenshot(format) instead with JPEG encoding for smaller size
     * @param format supported PNG, JPEG
     * @return a {@link InputStreamSource} of the screenshot in format, or <code>null</code> if
     *         the screenshot was not successful.
     * @throws DeviceNotAvailableException
     */
    public InputStreamSource getScreenshot(String format) throws DeviceNotAvailableException;

    /**
     * Clears the last connected wifi network. This should be called when starting a new invocation
     * to avoid connecting to the wifi network used in the previous test after device reboots.
     */
    public void clearLastConnectedWifiNetwork();

    /**
     * Connects to a wifi network.
     * <p/>
     * Turns on wifi and blocks until a successful connection is made to the specified wifi network.
     * Once a connection is made, the instance will try to restore the connection after every reboot
     * until {@link ITestDevice#disconnectFromWifi()} or
     * {@link ITestDevice#clearLastConnectedWifiNetwork()} is called.
     *
     * @param wifiSsid the wifi ssid to connect to
     * @param wifiPsk PSK passphrase or null if unencrypted
     * @return <code>true</code> if connected to wifi network successfully. <code>false</code>
     *         otherwise
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean connectToWifiNetwork(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException;

    /**
     * A variant of {@link #connectToWifiNetwork(String, String)} that only connects if device
     * currently does not have network connectivity.
     *
     * @param wifiSsid
     * @param wifiPsk
     * @return <code>true</code> if connected to wifi network successfully. <code>false</code>
     *         otherwise
     * @throws DeviceNotAvailableException
     */
    public boolean connectToWifiNetworkIfNeeded(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException;

    /**
     * Disconnects from a wifi network.
     * <p/>
     * Removes all networks from known networks list and disables wifi.
     *
     * @return <code>true</code> if disconnected from wifi network successfully. <code>false</code>
     *         if disconnect failed.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean disconnectFromWifi() throws DeviceNotAvailableException;

    /**
     * Test if wifi is enabled.
     * <p/>
     * Checks if wifi is enabled on device. Useful for asserting wifi status before tests that
     * shouldn't run with wifi, e.g. mobile data tests.
     *
     * @return <code>true</code> if wifi is enabled. <code>false</code> if disabled
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean isWifiEnabled() throws DeviceNotAvailableException;

    /**
     * Gets the device's IP address.
     *
     * @return the device's IP address, or <code>null</code> if device has no IP address
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public String getIpAddress() throws DeviceNotAvailableException;

    /**
     * Enables network monitoring on device.
     *
     * @return <code>true</code> if monitoring is enabled successfully. <code>false</code>
     *         if it failed.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean enableNetworkMonitor() throws DeviceNotAvailableException;

    /**
     * Disables network monitoring on device.
     *
     * @return <code>true</code> if monitoring is disabled successfully. <code>false</code>
     *         if it failed.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean disableNetworkMonitor() throws DeviceNotAvailableException;

    /**
     * Check that device has network connectivity.
     *
     * @return <code>true</code> if device has a working network connection,
     *          <code>false</code> overwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *          recovered.
     */
    public boolean checkConnectivity() throws DeviceNotAvailableException;

    /**
     * Attempt to dismiss any error dialogs currently displayed on device UI.
     *
     * @return <code>true</code> if no dialogs were present or dialogs were successfully cleared.
     *         <code>false</code> otherwise.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *             recovered.
     */
    public boolean clearErrorDialogs() throws DeviceNotAvailableException;

    /**
     * Fetch the test options for the device.
     *
     * @return {@link TestDeviceOptions} related to the device under test.
     */
    public TestDeviceOptions getOptions();

    /**
     * Fetch the application package names present on the device.
     *
     * @return {@link Set} of {@link String} package names currently installed on the device.
     * @throws DeviceNotAvailableException
     */
    public Set<String> getInstalledPackageNames() throws DeviceNotAvailableException;

    /**
     * Fetch the application package names that can be uninstalled. This is presently defined as
     * non-system packages, and updated system packages.
     *
     * @return {@link Set} of uninstallable {@link String} package names currently installed on the
     *         device.
     * @throws DeviceNotAvailableException
     */
    public Set<String> getUninstallablePackageNames() throws DeviceNotAvailableException;

    /**
     * Fetch information about a package installed on device.
     *
     * @return the {@link PackageInfo} or <code>null</code> if information could not be retrieved
     * @throws DeviceNotAvailableException
     */
    public PackageInfo getAppPackageInfo(String packageName) throws DeviceNotAvailableException;

    /**
     * Determines if multi user is supported.
     *
     * @return true if multi user is supported, false otherwise
     * @throws DeviceNotAvailableException
     */
    public boolean isMultiUserSupported() throws DeviceNotAvailableException;

    /**
     * Create a user with a given name and default flags 0.
     *
     * @param name of the user to create on the device
     * @return the integer for the user id created
     * @throws DeviceNotAvailableException
     */
    public int createUser(String name) throws DeviceNotAvailableException, IllegalStateException;

    /**
     * Create a user with a given name and the provided flags
     *
     * @param name of the user to create on the device
     * @param guest enable the user flag --guest during creation
     * @param ephemeral enable the user flag --ephemeral during creation
     * @return id of the created user
     * @throws DeviceNotAvailableException
     */
    public int createUser(String name, boolean guest, boolean ephemeral)
            throws DeviceNotAvailableException, IllegalStateException;

    /**
     * Remove a given user from the device.
     *
     * @param userId of the user to remove
     * @return true if we were succesful in removing the user, false otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean removeUser(int userId) throws DeviceNotAvailableException;

    /**
     * Gets the list of users on the device. Defaults to null.
     *
     * @return the list of user ids or null if there was an error.
     * @throws DeviceNotAvailableException
     */
    ArrayList<Integer> listUsers() throws DeviceNotAvailableException;

    /**
     * Get the maximum number of supported users. Defaults to 0.
     *
     * @return an integer indicating the number of supported users
     * @throws DeviceNotAvailableException
     */
    public int getMaxNumberOfUsersSupported() throws DeviceNotAvailableException;

    /**
     * Starts a given user in the background if it is currently stopped. If the user is already
     * running in the background, this method is a NOOP.
     * @param userId of the user to start in the background
     * @return true if the user was successfully started in the background.
     * @throws DeviceNotAvailableException
     */
    public boolean startUser(int userId) throws DeviceNotAvailableException;

    /**
     * Stops a given user. If the user is already stopped, this method is a NOOP.
     * Cannot stop current and system user.
     *
     * @param userId of the user to stop.
     * @return true if the user was successfully stopped.
     * @throws DeviceNotAvailableException
     */
    public boolean stopUser(int userId) throws DeviceNotAvailableException;

    /**
     * Stop a given user. Possible to provide extra flags to wait for the operation to have effect,
     * and force terminate the user. Cannot stop current and system user.
     *
     * @param userId of the user to stop.
     * @param waitFlag will make the command wait until user is stopped.
     * @param forceFlag will force stop the user.
     * @return true if the user was successfully stopped.
     * @throws DeviceNotAvailableException
     */
    public boolean stopUser(int userId, boolean waitFlag, boolean forceFlag)
            throws DeviceNotAvailableException;

    /**
     * Returns the primary user id.
     * @return the userId of the primary user if there is one, and null if there is no primary user.
     * @throws DeviceNotAvailableException
     */
    public Integer getPrimaryUserId() throws DeviceNotAvailableException;

    /**
     * Return the id of the current running user.
     *
     * @throws DeviceNotAvailableException
     */
    public int getCurrentUser() throws DeviceNotAvailableException;

    /**
     * Find and return the flags of a given user.
     * Flags are defined in {@link android.content.pm.UserInfo} in Android Open Source Project.
     *
     * @return the flags associated with the userId provided if found, -10000 in any other cases.
     * @throws DeviceNotAvailableException
     */
    @SuppressWarnings("javadoc")
    public int getUserFlags(int userId) throws DeviceNotAvailableException;

    /**
     * Return the serial number associated to the userId if found, -10000 in any other cases.
     *
     * @throws DeviceNotAvailableException
     */
    public int getUserSerialNumber(int userId) throws DeviceNotAvailableException;

    /**
     * Switch to another userId with a default timeout. {@link #switchUser(int, long)}.
     *
     * @return True if the new userId matches the userId provider. False otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean switchUser(int userId) throws DeviceNotAvailableException;

    /**
     * Switch to another userId with the provided timeout as deadline.
     * Attempt to disable keyguard after user change is successful.
     *
     * @param timeout to wait before returning false for switch-user failed.
     * @return True if the new userId matches the userId provider. False otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean switchUser(int userId, long timeout) throws DeviceNotAvailableException;

    /**
     * Check if a given user is running.
     *
     * @return True if the user is running, false in every other cases.
     * @throws DeviceNotAvailableException
     */
    public boolean isUserRunning(int userId) throws DeviceNotAvailableException;

    /**
     * Check if a feature is available on a device.
     *
     * @param feature which format should be "feature:<name>".
     * @return True if feature is found, false otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean hasFeature(String feature) throws DeviceNotAvailableException;

    /**
     * See {@link #getSetting(int, String, String)} and performed on system user.
     *
     * @throws DeviceNotAvailableException
     */
    public String getSetting(String namespace, String key) throws DeviceNotAvailableException;

    /**
     * Return the value of the requested setting.
     * namespace must be one of: {"system", "secure", "global"}
     *
     * @return the value associated with the namespace:key of a user. Null if not found.
     * @throws DeviceNotAvailableException
     */
    public String getSetting(int userId, String namespace, String key)
            throws DeviceNotAvailableException;

    /**
     * See {@link #setSetting(int, String, String, String)} and performed on system user.
     *
     * @throws DeviceNotAvailableException
     */
    public void setSetting(String namespace, String key, String value)
            throws DeviceNotAvailableException;

    /**
     * Add a setting value to the namespace of a given user. Some settings will only be available
     * after a reboot.
     * namespace must be one of: {"system", "secure", "global"}
     *
     * @throws DeviceNotAvailableException
     */
    public void setSetting(int userId, String namespace, String key, String value)
            throws DeviceNotAvailableException;

    /**
     * Find and return the android-id associated to a userId, null if not found.
     *
     * @throws DeviceNotAvailableException
     */
    public String getAndroidId(int userId) throws DeviceNotAvailableException;

    /**
     * Create a Map of android ids found matching user ids. There is no insurance that each user
     * id will found an android id associated in this function so some user ids may match null.
     *
     * @return Map of android ids found matching user ids.
     * @throws DeviceNotAvailableException
     */
    public Map<Integer, String> getAndroidIds() throws DeviceNotAvailableException;
}
