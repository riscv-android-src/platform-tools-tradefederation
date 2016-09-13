// Copyright 2016 Google Inc. All Rights Reserved.
package com.android.tradefed.testtype;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Unit tests for {@link JackCodeCoverageTest}. */
public class JackCodeCoverageTestTest extends TestCase {

    private static final File COVERAGE_FILE1 = new File("/some/example/coverage.ec");
    private static final File COVERAGE_FILE2 = new File("/another/example/coverage.ec");
    private static final File COVERAGE_FILE3 = new File("/different/extension/example.exec");

    private static final File METADATA_FILE1 = new File("/some/example/coverage.em");
    private static final File METADATA_FILE2 = new File("/another/example/coverage.ec");
    private static final File METADATA_FILE3 = new File("/different/extension/example.exec");

    private static final String EMMA_METADATA_RESOURCE_PATH = "/testdata/emma_meta.zip";
    private static final String EMMA_METADATA_ARTIFACT_NAME = "emma_meta.zip";
    private static final String FOO_METADATA =
            "/out/target/common/obj/JAVA_LIBRARIES/foo_intermediates/coverage.em";
    private static final String BAR_METADATA =
            "/out/target/common/obj/APPS/bar_intermediates/coverage.em";
    private static final String BAZ_METADATA =
            "/out/target/common/obj/APPS/baz_intermediates/coverage.em";

    public void testGetCoverageReporter() throws IOException {
        // Try to get the coverage reporter
        JackCodeCoverageTest coverageTest = new JackCodeCoverageTest();
        File coverageReporter = coverageTest.getCoverageReporter();

        // Verify that we were able to find the coverage reporter tool and that the tool exists
        assertNotNull(coverageReporter);
        assertTrue(coverageReporter.exists());
    }

    public void testGetMetadataFiles() throws IOException {
        // Prepare test data
        InputStream metadataZipStream = getClass().getResourceAsStream(EMMA_METADATA_RESOURCE_PATH);
        File metadataZipFile = FileUtil.createTempFile("emma_meta", ".zip");
        FileUtil.writeToFile(metadataZipStream, metadataZipFile);
        File metadataFolder = ZipUtil.extractZipToTemp(metadataZipFile, "metadata");

        // Set up mocks
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(metadataZipFile).when(mockBuild).getFile(EMMA_METADATA_ARTIFACT_NAME);
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(EMMA_METADATA_ARTIFACT_NAME).when(coverageTest).getMetadataZipArtifact();

        // Get the metadata files
        Set<File> metadataFiles = null;
        try {
            metadataFiles = coverageTest.getMetadataFiles(metadataFolder);
        } finally {
            // Cleanup
            FileUtil.deleteFile(metadataZipFile);
            FileUtil.recursiveDelete(metadataFolder);
        }

        // Verify that we got all of the metdata files
        assertNotNull(metadataFiles);
        assertEquals(3, metadataFiles.size());
        Set<String> expectedFiles = Sets.newHashSet(FOO_METADATA, BAR_METADATA, BAZ_METADATA);
        for (File metadata : metadataFiles) {
            for (String expected : expectedFiles) {
                if (metadata.getAbsolutePath().endsWith(expected)) {
                    expectedFiles.remove(expected);
                    break;
                }
            }
        }
        assertTrue(expectedFiles.isEmpty());
    }

    public void testGetMetadataFiles_withFilter() throws IOException {
        // Prepare test data
        InputStream metadataZipStream = getClass().getResourceAsStream(EMMA_METADATA_RESOURCE_PATH);
        File metadataZipFile = FileUtil.createTempFile("emma_meta", ".zip");
        FileUtil.writeToFile(metadataZipStream, metadataZipFile);
        File metadataFolder = ZipUtil.extractZipToTemp(metadataZipFile, "metadata");

        // Set up mocks
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(metadataZipFile).when(mockBuild).getFile(EMMA_METADATA_ARTIFACT_NAME);
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(EMMA_METADATA_ARTIFACT_NAME).when(coverageTest).getMetadataZipArtifact();
        doReturn(Arrays.asList("glob:**/foo_intermediates/coverage.em")).when(coverageTest)
                .getMetadataFilesFilter();

        // Get the metadata files
        Set<File> metadataFiles = null;
        try {
            metadataFiles = coverageTest.getMetadataFiles(metadataFolder);
        } finally {
            // Cleanup
            FileUtil.deleteFile(metadataZipFile);
            FileUtil.recursiveDelete(metadataFolder);
        }

        // Verify that only the framework metadata file was returned
        assertNotNull(metadataFiles);
        assertEquals(1, metadataFiles.size());
        File metadataFile = Iterables.getOnlyElement(metadataFiles);
        assertTrue(metadataFile.getAbsolutePath().endsWith(FOO_METADATA));
    }

    public void testGenerateCoverageReport() throws IOException {
        // Prepare some test data
        Set<File> coverageFiles = Sets.newHashSet(COVERAGE_FILE1, COVERAGE_FILE2, COVERAGE_FILE3);
        Set<File> metadataFiles = Sets.newHashSet(METADATA_FILE1, METADATA_FILE2, METADATA_FILE3);
        File dest = new File("/some/destination/directory");
        File fakeReportTool = new File("/the/path/to/the/jack-jacoco-reporter.jar");
        InputStream metadataZipStream = getClass().getResourceAsStream(EMMA_METADATA_RESOURCE_PATH);
        File metadataZipFile = FileUtil.createTempFile("emma_meta", ".zip");
        FileUtil.writeToFile(metadataZipStream, metadataZipFile);

        // Set up mocks
        CommandResult success = new CommandResult(CommandStatus.SUCCESS);
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(metadataZipFile).when(mockBuild).getFile(EMMA_METADATA_ARTIFACT_NAME);
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(EMMA_METADATA_ARTIFACT_NAME).when(coverageTest).getMetadataZipArtifact();
        doReturn(metadataFiles).when(coverageTest).getMetadataFiles(any(File.class));
        doReturn(fakeReportTool).when(coverageTest).getCoverageReporter();
        doReturn(success).when(coverageTest).runTimedCmd(anyLong(), any(String[].class));

        try {
            // Generate a coverage report
            coverageTest.generateCoverageReport(coverageFiles, dest);

            // Verify that the command was called with the right arguments
            ArgumentCaptor<String[]> cmdLineCaptor = ArgumentCaptor.forClass(String[].class);
            Mockito.verify(coverageTest).runTimedCmd(anyLong(), cmdLineCaptor.capture());
            List<String> cmdLine = Arrays.asList(cmdLineCaptor.getValue());
            assertEquals("java", cmdLine.get(0));
            assertEquals("-jar", cmdLine.get(1));
            assertEquals(fakeReportTool.getAbsolutePath(), cmdLine.get(2));
            assertTrue(cmdLine.contains("--report-dir"));

            // Verify the rest of the command line arguments
            for (int i = 3; i < cmdLine.size() - 1; i++) {
                switch (cmdLine.get(i)) {
                  case "--coverage-file":
                      File coverageFile = new File(cmdLine.get(++i));
                      assertTrue(String.format("Unexpected coverage file: %s", coverageFile),
                              coverageFiles.remove(coverageFile));
                      break;
                  case "--metadata-file":
                      File metadataFile = new File(cmdLine.get(++i));
                      assertTrue(String.format("Unexpected metadata file: %s", metadataFile),
                              metadataFiles.remove(metadataFile));
                      break;
                  case "--report-dir":
                      assertEquals(dest.getAbsolutePath(), cmdLine.get(++i));
                      break;
                }
            }
            assertTrue(coverageFiles.isEmpty());
            assertTrue(metadataFiles.isEmpty());
        } finally {
            FileUtil.deleteFile(metadataZipFile);
        }
    }

    public void testGenerateCoverageReport_error() throws IOException {
        // Prepare some test data
        Set<File> coverageFiles = Sets.newHashSet(COVERAGE_FILE1, COVERAGE_FILE2, COVERAGE_FILE3);
        Set<File> metadataFiles = Sets.newHashSet(METADATA_FILE1, METADATA_FILE2, METADATA_FILE3);
        File dest = new File("/some/destination/directory");
        File fakeReportTool = new File("/the/path/to/the/jack-jacoco-reporter.jar");
        String stderr = "some error message";
        InputStream metadataZipStream = getClass().getResourceAsStream(EMMA_METADATA_RESOURCE_PATH);
        File metadataZipFile = FileUtil.createTempFile("emma_meta", ".zip");
        FileUtil.writeToFile(metadataZipStream, metadataZipFile);

        // Set up mocks
        CommandResult failed = new CommandResult(CommandStatus.FAILED);
        failed.setStderr(stderr);
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(metadataZipFile).when(mockBuild).getFile(EMMA_METADATA_ARTIFACT_NAME);
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(EMMA_METADATA_ARTIFACT_NAME).when(coverageTest).getMetadataZipArtifact();
        doReturn(metadataFiles).when(coverageTest).getMetadataFiles(any(File.class));
        doReturn(fakeReportTool).when(coverageTest).getCoverageReporter();
        doReturn(failed).when(coverageTest).runTimedCmd(anyLong(), any(String[].class));

        // Generate a coverage report
        try {
            coverageTest.generateCoverageReport(coverageFiles, dest);
            fail("IOException not thrown");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains(stderr));
        } finally {
            FileUtil.deleteFile(metadataZipFile);
        }
    }
}
