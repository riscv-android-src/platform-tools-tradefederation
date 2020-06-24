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

package com.android.tradefed.testtype.mobly;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class MoblyBinaryHostTestTest {
    private static final String BINARY_PATH = "/binary/file/path/test.par";
    private static final String LOG_PATH = "/log/dir/abs/path";
    private static final String DEVICE_SERIAL = "X123SER";
    private static final long DEFAULT_TIME_OUT = 30 * 1000L;

    private MoblyBinaryHostTest mSpyTest;
    private ITestDevice mMockDevice;
    private IRunUtil mMockRunUtil;
    private MoblyYamlResultParser mMockParser;
    private InputStream mMockSummaryInputStream;

    @Before
    public void setUp() {
        mSpyTest = Mockito.spy(new MoblyBinaryHostTest());
        mMockDevice = Mockito.mock(ITestDevice.class);
        mMockRunUtil = Mockito.mock(IRunUtil.class);
        mSpyTest.setDevice(mMockDevice);
        Mockito.doNothing().when(mSpyTest).reportLogs(any(), any());
        Mockito.doReturn(mMockRunUtil).when(mSpyTest).getRunUtil();
        Mockito.doReturn(DEFAULT_TIME_OUT).when(mSpyTest).getTestTimeout();
        Mockito.doReturn(new CommandResult(CommandStatus.SUCCESS))
                .when(mMockRunUtil)
                .runTimedCmd(anyLong(), any());
    }

    @Test
    public void testBuildCommandLineArrayWithOutConfig() throws Exception {
        Mockito.doReturn(DEVICE_SERIAL).when(mMockDevice).getSerialNumber();
        Mockito.doReturn(LOG_PATH).when(mSpyTest).getLogDirAbsolutePath();
        List<String> expOptions = Arrays.asList("--option1", "--option2=test_option");
        Mockito.doReturn(expOptions).when(mSpyTest).getTestOptions();
        String[] cmdArray = mSpyTest.buildCommandLineArray(BINARY_PATH);
        assertThat(cmdArray[0], is(BINARY_PATH));
        assertThat(cmdArray[1], is("--"));
        assertThat(Arrays.asList(cmdArray), not(hasItem(startsWith("--config"))));
        assertThat(
                Arrays.asList(cmdArray),
                hasItems(
                        "--device_serial=" + DEVICE_SERIAL,
                        "--log_path=" + LOG_PATH,
                        "--option1",
                        "--option2=test_option"));
    }

    @Test
    public void testBuildCommandLineArrayWithConfig() throws Exception {
        Mockito.doReturn(DEVICE_SERIAL).when(mMockDevice).getSerialNumber();
        Mockito.doReturn(LOG_PATH).when(mSpyTest).getLogDirAbsolutePath();
        List<String> expOptions = Arrays.asList("--option1", "--option2=test_option");
        Mockito.doReturn(expOptions).when(mSpyTest).getTestOptions();
        String configFilePath = "/test/config/file/path.yaml";
        Mockito.doReturn(configFilePath).when(mSpyTest).getConfigPath();
        String[] cmdArray = mSpyTest.buildCommandLineArray(BINARY_PATH);
        assertThat(cmdArray[0], is(BINARY_PATH));
        assertThat(cmdArray[1], is("--"));
        assertThat(
                Arrays.asList(cmdArray),
                hasItems(
                        "--device_serial=" + DEVICE_SERIAL,
                        "--log_path=" + LOG_PATH,
                        "--config=" + configFilePath,
                        "--option1",
                        "--option2=test_option"));
    }

    @Test
    public void testProcessYamlTestResultsSuccess() throws Exception {
        mMockSummaryInputStream = Mockito.mock(InputStream.class);
        mMockParser = Mockito.mock(MoblyYamlResultParser.class);
        mSpyTest.processYamlTestResults(mMockSummaryInputStream, mMockParser);
        verify(mMockParser, times(1)).parse(mMockSummaryInputStream);
    }

    @Test
    public void testUpdateConfigFile() throws Exception {
        Mockito.doReturn("testBedName").when(mSpyTest).getTestBed();
        String configString =
                new StringBuilder()
                        .append("TestBeds:")
                        .append("\n")
                        .append("- TestParams:")
                        .append("\n")
                        .append("    dut_name: is_dut")
                        .append("\n")
                        .append("  Name: testBedName")
                        .append("\n")
                        .append("  Controllers:")
                        .append("\n")
                        .append("    AndroidDevice:")
                        .append("\n")
                        .append("    - dimensions: {mobile_type: 'dut_rear'}")
                        .append("\n")
                        .append("      serial: old123")
                        .append("\n")
                        .append("MoblyParams: {{LogPath: {log_path}}}")
                        .append("\n")
                        .toString();
        InputStream inputStream = new ByteArrayInputStream(configString.getBytes());
        Writer writer = new StringWriter();
        mSpyTest.updateConfigFile(inputStream, writer, DEVICE_SERIAL);
        String updatedConfigString = writer.toString();
        LogUtil.CLog.d("Updated config string: %s", updatedConfigString);
        // Check if serial injected.
        assertThat(updatedConfigString, containsString(DEVICE_SERIAL));
        // Check if original still exists.
        assertThat(updatedConfigString, containsString("mobile_type"));
    }
}
