package com.android.gmscore.backup.tests;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * These are the E2E tests for Backup and Restore API.
 *
 *  The tests are using a backup test app (com.google.android.gms.testapp.backupdollytestapp) to
 * generate a test data for the backup. Please see go/bhihy for more details.
 */
public class BackupRestoreHostTests extends DeviceTestCase {

    /**
     * Backup test app ID.
     */
    private final static String TEST_APP_ID = "com.google.android.gms.testapp.backupdollytestapp";

    /**
     * Backup test app activity that is used to create test data files.
     */
    private static final String
            ADD_FILE_ACTIVITY = "com.google.android.gms.testapp.backupdollytestapp.AddFileActivity";

    /**
     * Adb command template for creating test data files.
     */
    private static final String ADD_FILE_COMMAND_TEMPLATE =
            "am start -a android.intent.action.MAIN " + "-c android.intent.category.LAUNCHER "
            + "-n " + TEST_APP_ID + "/" + ADD_FILE_ACTIVITY + " -e file_name %s "
            + " -e file_size_in_bytes %d";

    /**
     * Max time to wait for a backup result message in the logcat.
     */
    private static final int BACKUP_TIMEOUT_SECONDS = 60;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Clean up test app data.
        getDevice().enableAdbRoot();
        getDevice().executeShellCommand(
                "rm /data/data/com.google.android.gms.testapp.backupdollytestapp/files/f*.txt");
        // Increase backup log level.
        getDevice().executeShellCommand("setprop log.tag.GmsBackupTransport VERBOSE");
    }

    /**
     * Try to back-up a blob that is larger than the max allowed blob size, and verify through
     * logcat that it doesn’t get backed-up
     */
    public void testBackup_NotExceedingMaxBlobSize_Succeeds() throws Exception {
        // Clear the logcat log and start capturing.
        getDevice().clearLogcat();

        // GIVEN: a test app has a file not exceeding the max Blob size.
        createTestDataFileForBackup("file123.txt", 10); // 10b file

        // WHEN: I request to perform a full backup.
        requestFullBackup(TEST_APP_ID);

        // THEN: The backup should be successful.
        String key = "BackupManagerService: Full package backup success: " + TEST_APP_ID;
        assertLogcatContains("Backup of files not exceeding max blob size must succeed.", key);
    }

    /**
     * Try to back-up a blob that is larger than the max allowed blob size, and verify through
     * logcat that it doesn’t get backed-up
     */
    public void testBackup_ExceedingMaxBlobSize_IsRejected() throws Exception {
        // Clear the logcat log and start capturing.
        getDevice().clearLogcat();

        // GIVEN: a test app has a file exceeding max Blob size.
        createTestDataFileForBackup("file123.txt", 6291456); // 6Mb file

        // WHEN: I request to perform a full backup.
        requestFullBackup(TEST_APP_ID);

        // THEN: The backup should be rejected.
        String key = "PFTBT   : Transport rejected backup of " + TEST_APP_ID;
        assertLogcatContains("Backup of files exceeding max blob size must be rejected.", key);
    }

    /**
     * Parses the logcat and searches for a line containing a key
     *
     * @param key
     *            a string to search for.
     */
    public void assertLogcatContains(String errorMsg, String key) throws IOException {
        boolean keyMatchFound = waitForLogcatString(key, BACKUP_TIMEOUT_SECONDS);
        assertTrue(errorMsg, keyMatchFound);
    }

    /**
     * Scans a logcat for a string with a key until the string is found or until the mat timeout is
     * reached.
     *
     * @param key
     *            a string to search for.
     * @param maxTimeoutInSeconds
     *            the max time to wait for the string.
     */
    public boolean waitForLogcatString(String key, int maxTimeoutInSeconds) throws IOException {
        long timeout = System.currentTimeMillis() + maxTimeoutInSeconds * 1000;
        boolean keyMatchFound = false;
        while (timeout >= System.currentTimeMillis() && !keyMatchFound) {
            BufferedReader log = new BufferedReader(
                    new InputStreamReader(getDevice().getLogcat().createInputStream()));
            String line;
            while ((line = log.readLine()) != null) {
                if (line.contains(key)) {
                    keyMatchFound = true;
                    break;
                }
            }
            log.close();
            // In case the key has not been found, wait for the log to update before
            // performing the next search.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        return keyMatchFound;
    }

    /**
     * Performs a full backup of the app data.
     *
     * @param appId
     *            the application id.
     * @throws DeviceNotAvailableException
     */
    public void requestFullBackup(String appId) throws DeviceNotAvailableException {
        String testAppBackupRequest = "bmgr fullbackup " + appId;
        getDevice().executeShellCommand(testAppBackupRequest);
        String forceFullBackupCommand = "bmgr run";
        getDevice().executeShellCommand(forceFullBackupCommand);
    }

    private void createTestDataFileForBackup(String fileName, int sizeInBytes)
            throws DeviceNotAvailableException {
        // Start the backup app.
        String startBackupAppCommand = "am start -a android.intent.action.MAIN " + "-n "
                + TEST_APP_ID + "/.MainActivity";
        getDevice().executeShellCommand(startBackupAppCommand);

        // Create a backup data
        String createBackupDataExceedingMaxBlobSizeCommand = String.format(
                ADD_FILE_COMMAND_TEMPLATE, fileName, sizeInBytes);
        getDevice().executeShellCommand(createBackupDataExceedingMaxBlobSizeCommand);
    }

}
