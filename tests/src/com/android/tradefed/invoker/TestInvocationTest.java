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
package com.android.tradefed.invoker;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.command.FatalHostError;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.ILogRegistry;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IResumableTest;
import com.android.tradefed.testtype.IRetriableTest;
import com.android.tradefed.testtype.IStrictShardableTest;

import junit.framework.Test;
import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link TestInvocation}.
 */
public class TestInvocationTest extends TestCase {

    private static final String SERIAL = "serial";
    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();
    private static final String PATH = "path";
    private static final String URL = "url";
    private static final TestSummary mSummary = new TestSummary("http://www.url.com/report.txt");

    /** The {@link TestInvocation} under test, with all dependencies mocked out */
    private TestInvocation mTestInvocation;

    private IConfiguration mStubConfiguration;
    private IConfiguration mStubMultiConfiguration;

    private IInvocationContext mStubInvocationMetadata;

    // The mock objects.
    private ITestDevice mMockDevice;
    private ITargetPreparer mMockPreparer;
    private IBuildProvider mMockBuildProvider;
    private IBuildInfo mMockBuildInfo;
    private ITestInvocationListener mMockTestListener;
    private ITestSummaryListener mMockSummaryListener;
    private ILeveledLogOutput mMockLogger;
    private ILogSaver mMockLogSaver;
    private IDeviceRecovery mMockRecovery;
    private Capture<List<TestSummary>> mUriCapture;
    private ILogRegistry mMockLogRegistry;
    private IConfigurationFactory mMockConfigFactory;
    private IRescheduler mockRescheduler;
    private DeviceDescriptor mFakeDescriptor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mStubConfiguration = new Configuration("foo", "bar");
        mStubMultiConfiguration = new Configuration("foo", "bar");

        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockRecovery = EasyMock.createMock(IDeviceRecovery.class);
        mMockPreparer = EasyMock.createMock(ITargetPreparer.class);
        mMockBuildProvider = EasyMock.createMock(IBuildProvider.class);

        // Use strict mocks here since the order of Listener calls is important
        mMockTestListener = EasyMock.createStrictMock(ITestInvocationListener.class);
        mMockSummaryListener = EasyMock.createStrictMock(ITestSummaryListener.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockLogger = EasyMock.createMock(ILeveledLogOutput.class);
        mMockLogRegistry = EasyMock.createMock(ILogRegistry.class);
        mMockLogSaver = EasyMock.createMock(ILogSaver.class);
        mMockConfigFactory = EasyMock.createMock(IConfigurationFactory.class);
        mockRescheduler = EasyMock.createMock(IRescheduler.class);

        mStubConfiguration.setDeviceRecovery(mMockRecovery);
        mStubConfiguration.setTargetPreparer(mMockPreparer);
        mStubConfiguration.setBuildProvider(mMockBuildProvider);

        List<IDeviceConfiguration> deviceConfigs = new ArrayList<IDeviceConfiguration>();
        IDeviceConfiguration device1 =
                new DeviceConfigurationHolder(ConfigurationDef.DEFAULT_DEVICE_NAME);
        device1.addSpecificConfig(mMockRecovery);
        device1.addSpecificConfig(mMockPreparer);
        device1.addSpecificConfig(mMockBuildProvider);
        deviceConfigs.add(device1);
        mStubMultiConfiguration.setDeviceConfigList(deviceConfigs);

        mStubConfiguration.setLogSaver(mMockLogSaver);
        mStubMultiConfiguration.setLogSaver(mMockLogSaver);

        List<ITestInvocationListener> listenerList = new ArrayList<ITestInvocationListener>(1);
        listenerList.add(mMockTestListener);
        listenerList.add(mMockSummaryListener);
        mStubConfiguration.setTestInvocationListeners(listenerList);
        mStubMultiConfiguration.setTestInvocationListeners(listenerList);

        mStubConfiguration.setLogOutput(mMockLogger);
        mStubMultiConfiguration.setLogOutput(mMockLogger);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(SERIAL);
        EasyMock.expect(mMockDevice.getIDevice()).andStubReturn(null);
        mMockDevice.setRecovery(mMockRecovery);
        mMockDevice.preInvocationSetup((IBuildInfo)EasyMock.anyObject());
        EasyMock.expectLastCall().anyTimes();
        mMockDevice.postInvocationTearDown();
        EasyMock.expectLastCall().anyTimes();
        mFakeDescriptor = new DeviceDescriptor(SERIAL, false, DeviceAllocationState.Available,
                "unknown", "unknown", "unknown", "unknown", "unknown");
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(mFakeDescriptor);

        EasyMock.expect(mMockBuildInfo.getBuildId()).andStubReturn("1");
        EasyMock.expect(mMockBuildInfo.getBuildAttributes()).andStubReturn(EMPTY_MAP);
        EasyMock.expect(mMockBuildInfo.getBuildBranch()).andStubReturn("branch");
        EasyMock.expect(mMockBuildInfo.getBuildFlavor()).andStubReturn("flavor");

        // always expect logger initialization and cleanup calls
        mMockLogRegistry.registerLogger(mMockLogger);
        mMockLogger.init();
        mMockLogger.closeLog();
        mMockLogRegistry.unregisterLogger();
        mUriCapture = new Capture<List<TestSummary>>();

        mStubInvocationMetadata = new InvocationContext();
        mStubInvocationMetadata.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME,
                mMockDevice);
        mStubInvocationMetadata.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME,
                mMockBuildInfo);

        // create the BaseTestInvocation to test
        mTestInvocation = new TestInvocation() {
            @Override
            ILogRegistry getLogRegistry() {
                return mMockLogRegistry;
            }

            @Override
            protected IConfigurationFactory getConfigFactory() {
                return mMockConfigFactory;
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();

    }

    /**
     * Test the normal case invoke scenario with a {@link IRemoteTest}.
     * <p/>
     * Verifies that all external interfaces get notified as expected.
     */
    public void testInvoke_RemoteTest() throws Throwable {
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupMockSuccessListeners();

        test.run((ITestInvocationListener)EasyMock.anyObject());
        setupNormalInvoke(test);
        EasyMock.replay(mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the normal case for multi invoke scenario with a {@link IRemoteTest}.
     * <p/>
     * Verifies that all external interfaces get notified as expected.
     */
    public void testInvokeMulti_RemoteTest() throws Throwable {
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupMockSuccessListeners();

        test.run((ITestInvocationListener)EasyMock.anyObject());
        setupNormalInvoke(test);
        EasyMock.replay(mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubMultiConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the normal case invoke scenario with an {@link ITestSummaryListener} masquerading as
     * an {@link ITestInvocationListener}.
     * <p/>
     * Verifies that all external interfaces get notified as expected.
     */
    public void testInvoke_twoSummary() throws Throwable {

        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupMockSuccessListeners();

        test.run((ITestInvocationListener)EasyMock.anyObject());
        setupNormalInvoke(test);
        EasyMock.replay(mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where build retrieve fails.
     * <p/>
     * An invocation will be started in this scenario.
     */
    public void testInvoke_buildFailed() throws Throwable  {
        BuildRetrievalError exception = new BuildRetrievalError("error", null, mMockBuildInfo);
        EasyMock.expect(mMockBuildProvider.getBuild()).andThrow(exception);
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn("");
        setupMockFailureListeners(exception);
        setupInvoke();
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        mStubConfiguration.setTest(test);
        EasyMock.expect(mMockLogger.getLog())
        .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        EasyMock.expect(mMockDevice.getLogcat())
        .andReturn(new ByteArrayInputStreamSource(new byte[0])).times(2);
        replayMocks(test, mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler);
    }

    /**
     * Test the invoke scenario where there is no build to test.
     */
    public void testInvoke_noBuild() throws Throwable  {
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(null);
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        mStubConfiguration.setTest(test);
        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        setupInvoke();
        replayMocks(test, mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test);
    }

    /**
     * Test the invoke scenario where there is no build to test for a {@link IRetriableTest}.
     */
    public void testInvoke_noBuildRetry() throws Throwable  {
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(null);

        IRetriableTest test = EasyMock.createMock(IRetriableTest.class);
        EasyMock.expect(test.isRetriable()).andReturn(Boolean.TRUE);

        EasyMock.expect(mockRescheduler.rescheduleCommand()).andReturn(EasyMock.anyBoolean());

        mStubConfiguration.setTest(test);
        mStubConfiguration.getCommandOptions().setLoopMode(false);
        mMockLogRegistry.dumpToGlobalLog(mMockLogger);
        setupInvoke();
        replayMocks(test);
        EasyMock.replay(mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        EasyMock.verify(mockRescheduler);
        verifyMocks(test);
    }

    /**
     * Test the{@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario
     * where the test is a {@link IDeviceTest}
     */
    public void testInvoke_deviceTest() throws Throwable {
         DeviceConfigTest mockDeviceTest = EasyMock.createMock(DeviceConfigTest.class);
         mStubConfiguration.setTest(mockDeviceTest);
         mockDeviceTest.setDevice(mMockDevice);
         mockDeviceTest.run((ITestInvocationListener)EasyMock.anyObject());
         setupMockSuccessListeners();
         setupNormalInvoke(mockDeviceTest);
         EasyMock.replay(mockRescheduler);
         mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
         verifyMocks(mockDeviceTest, mockRescheduler);
         verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link IllegalArgumentException}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_testFail() throws Throwable {
        IllegalArgumentException exception = new IllegalArgumentException();
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(exception);
        setupMockFailureListeners(exception);
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        setupNormalInvoke(test);
        EasyMock.replay(mockRescheduler);
        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("IllegalArgumentException was not rethrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link FatalHostError}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_fatalError() throws Throwable {
        FatalHostError exception = new FatalHostError("error");
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(exception);
        setupMockFailureListeners(exception);
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        setupNormalInvoke(test);
        EasyMock.replay(mockRescheduler);
        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("FatalHostError was not rethrown");
        } catch (FatalHostError e)  {
            // expected
        }
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where test run throws {@link DeviceNotAvailableException}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_deviceNotAvail() throws Throwable {
        DeviceNotAvailableException exception = new DeviceNotAvailableException("ERROR", SERIAL);
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(exception);
        mMockDevice.setRecoveryMode(RecoveryMode.NONE);
        EasyMock.expectLastCall();
        setupMockFailureListeners(exception);
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        setupNormalInvoke(test);
        EasyMock.replay(mockRescheduler);
        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario where preparer throws {@link BuildError}
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_buildError() throws Throwable {
        BuildError exception = new BuildError("error", mFakeDescriptor);
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        mStubConfiguration.setTest(test);
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);

        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.expectLastCall().andThrow(exception);
        setupMockFailureListeners(exception);
        EasyMock.expect(mMockDevice.getBugreport())
            .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        setupInvokeWithBuild();
        replayMocks(test);
        EasyMock.replay(mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the invoke scenario for a {@link IResumableTest}.
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_resume() throws Throwable {
        IResumableTest resumableTest = EasyMock.createMock(IResumableTest.class);
        mStubConfiguration.setTest(resumableTest);
        ITestInvocationListener resumeListener = EasyMock.createStrictMock(
                ITestInvocationListener.class);
        mStubConfiguration.setTestInvocationListener(resumeListener);

        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        resumeListener.invocationStarted(mStubInvocationMetadata);
        mMockDevice.clearLastConnectedWifiNetwork();
        mMockDevice.setOptions((TestDeviceOptions)EasyMock.anyObject());
        mMockBuildInfo.setDeviceSerial(SERIAL);
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn("");
        mMockDevice.startLogcat();
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);

        resumableTest.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException("ERROR", SERIAL));
        EasyMock.expect(resumableTest.isResumable()).andReturn(Boolean.TRUE);
        mMockDevice.setRecoveryMode(RecoveryMode.NONE);
        EasyMock.expectLastCall();
        EasyMock.expect(mMockDevice.getLogcat())
                .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        EasyMock.expect(mMockLogger.getLog())
                .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        EasyMock.expect(mMockLogSaver.saveLogData(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStream)EasyMock.anyObject())
                ).andReturn(new LogFile(PATH, URL, false /* compressed */, true /* text */));
        EasyMock.expect(mMockLogSaver.saveLogData(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStream)EasyMock.anyObject())
                ).andReturn(new LogFile(PATH, URL, false /* compressed */, true /* text */));
        resumeListener.testLog(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStreamSource)EasyMock.anyObject());
        resumeListener.testLog(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStreamSource)EasyMock.anyObject());

        // just return same build and logger for simplicity
        EasyMock.expect(mMockBuildInfo.clone()).andReturn(mMockBuildInfo);
        EasyMock.expect(mMockLogger.clone()).andReturn(mMockLogger);
        IRescheduler mockRescheduler = EasyMock.createMock(IRescheduler.class);
        Capture<IConfiguration> capturedConfig = new Capture<IConfiguration>();
        EasyMock.expect(mockRescheduler.scheduleConfig(EasyMock.capture(capturedConfig)))
                .andReturn(Boolean.TRUE);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        mMockDevice.clearLastConnectedWifiNetwork();
        mMockDevice.stopLogcat();

        mMockLogger.init();
        mMockLogSaver.invocationStarted(mStubInvocationMetadata);
        // now set resumed invocation expectations
        mMockDevice.clearLastConnectedWifiNetwork();
        mMockDevice.setOptions((TestDeviceOptions)EasyMock.anyObject());
        mMockBuildInfo.setDeviceSerial(SERIAL);
        mMockBuildInfo.setTestTag(EasyMock.eq("stub"));
        EasyMock.expectLastCall().times(2);
        mMockDevice.startLogcat();
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        mMockLogSaver.invocationStarted(mStubInvocationMetadata);
        mMockDevice.setRecovery(mMockRecovery);
        resumableTest.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expect(mMockDevice.getLogcat())
                .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        EasyMock.expect(mMockLogger.getLog())
                .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        EasyMock.expect(mMockLogSaver.saveLogData(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStream)EasyMock.anyObject())
                ).andReturn(new LogFile(PATH, URL, false /* compressed */, true /* text */));
        EasyMock.expect(mMockLogSaver.saveLogData(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStream)EasyMock.anyObject())
                ).andReturn(new LogFile(PATH, URL, false /* compressed */, true /* text */));
        resumeListener.testLog(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStreamSource)EasyMock.anyObject());
        resumeListener.testLog(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStreamSource)EasyMock.anyObject());
        resumeListener.invocationEnded(EasyMock.anyLong());
        mMockLogSaver.invocationEnded(EasyMock.anyLong());
        EasyMock.expect(resumeListener.getSummary()).andReturn(null);
        mMockBuildInfo.cleanUp();
        mMockLogger.closeLog();
        mMockDevice.clearLastConnectedWifiNetwork();
        mMockDevice.stopLogcat();

        EasyMock.replay(mockRescheduler, resumeListener, resumableTest, mMockPreparer,
                mMockBuildProvider, mMockLogger, mMockLogSaver, mMockDevice, mMockBuildInfo);

        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expect
        }
        // now call again, and expect invocation to be resumed properly
        mTestInvocation.invoke(mStubInvocationMetadata, capturedConfig.getValue(), mockRescheduler);

        EasyMock.verify(mockRescheduler, resumeListener, resumableTest, mMockPreparer,
                mMockBuildProvider, mMockLogger, mMockLogSaver, mMockDevice, mMockBuildInfo);
    }

    /**
     * Test the invoke scenario for a {@link IRetriableTest}.
     *
     * @throws Exception if unexpected error occurs
     */
    public void testInvoke_retry() throws Throwable {
        AssertionError exception = new AssertionError();
        IRetriableTest test = EasyMock.createMock(IRetriableTest.class);
        test.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(exception);
        EasyMock.expect(test.isRetriable()).andReturn(Boolean.TRUE);
        mStubConfiguration.getCommandOptions().setLoopMode(false);
        IRescheduler mockRescheduler = EasyMock.createMock(IRescheduler.class);
        EasyMock.expect(mockRescheduler.rescheduleCommand()).andReturn(EasyMock.anyBoolean());
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        setupMockFailureListeners(exception);
        setupNormalInvoke(test);
        EasyMock.replay(mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario
     * when a {@link ITargetCleaner} is part of the config.
     */
    public void testInvoke_tearDown() throws Throwable {
         IRemoteTest test = EasyMock.createNiceMock(IRemoteTest.class);
         ITargetCleaner mockCleaner = EasyMock.createMock(ITargetCleaner.class);
         mockCleaner.setUp(mMockDevice, mMockBuildInfo);
         mockCleaner.tearDown(mMockDevice, mMockBuildInfo, null);
         mStubConfiguration.getTargetPreparers().add(mockCleaner);
         setupMockSuccessListeners();
         setupNormalInvoke(test);
         EasyMock.replay(mockCleaner, mockRescheduler);
         mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
         verifyMocks(mockCleaner, mockRescheduler);
         verifySummaryListener();
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario
     * when a {@link ITargetCleaner} is part of the config, and the test throws a
     * {@link DeviceNotAvailableException}.
     */
    public void testInvoke_tearDown_deviceNotAvail() throws Throwable {
        DeviceNotAvailableException exception = new DeviceNotAvailableException("ERROR", SERIAL);
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(exception);
        ITargetCleaner mockCleaner = EasyMock.createMock(ITargetCleaner.class);
        mockCleaner.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.expectLastCall();
        mockCleaner.tearDown(mMockDevice, mMockBuildInfo, exception);
        EasyMock.expectLastCall();
        mMockDevice.setRecoveryMode(RecoveryMode.NONE);
        EasyMock.expectLastCall();
        EasyMock.replay(mockCleaner, mockRescheduler);
        mStubConfiguration.getTargetPreparers().add(mockCleaner);
        setupMockFailureListeners(exception);
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        setupNormalInvoke(test);
        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        verifyMocks(mockCleaner, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario
     * when a {@link ITargetCleaner} is part of the config, and the test throws a
     * {@link RuntimeException}.
     */
    public void testInvoke_tearDown_runtime() throws Throwable {
        RuntimeException exception = new RuntimeException();
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        test.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall().andThrow(exception);
        ITargetCleaner mockCleaner = EasyMock.createMock(ITargetCleaner.class);
        mockCleaner.setUp(mMockDevice, mMockBuildInfo);
        // tearDown should be called
        mockCleaner.tearDown(mMockDevice, mMockBuildInfo, exception);
        mStubConfiguration.getTargetPreparers().add(mockCleaner);
        setupMockFailureListeners(exception);
        mMockBuildProvider.buildNotTested(mMockBuildInfo);
        setupNormalInvoke(test);
        EasyMock.replay(mockCleaner, mockRescheduler);
        try {
            mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
            fail("RuntimeException not thrown");
        } catch (RuntimeException e) {
            // expected
        }
        verifyMocks(mockCleaner, mockRescheduler);
        verifySummaryListener();
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])} scenario
     * when there is {@link ITestInvocationListener} which implements the {@link ILogSaverListener}
     * interface.
     */
    public void testInvoke_logFileSaved() throws Throwable {
        List<ITestInvocationListener> listenerList =
                mStubConfiguration.getTestInvocationListeners();
        ILogSaverListener logSaverListener = EasyMock.createMock(ILogSaverListener.class);
        listenerList.add(logSaverListener);
        mStubConfiguration.setTestInvocationListeners(listenerList);

        logSaverListener.setLogSaver(mMockLogSaver);
        logSaverListener.invocationStarted(mStubInvocationMetadata);
        logSaverListener.testLog(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStreamSource)EasyMock.anyObject());
        logSaverListener.testLogSaved(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStreamSource)EasyMock.anyObject(),
                (LogFile)EasyMock.anyObject());
        logSaverListener.testLog(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStreamSource)EasyMock.anyObject());
        logSaverListener.testLogSaved(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStreamSource)EasyMock.anyObject(),
                (LogFile)EasyMock.anyObject());
        logSaverListener.invocationEnded(EasyMock.anyLong());
        EasyMock.expect(logSaverListener.getSummary()).andReturn(mSummary);

        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        setupMockSuccessListeners();
        test.run((ITestInvocationListener)EasyMock.anyObject());
        setupNormalInvoke(test);
        EasyMock.replay(logSaverListener, mockRescheduler);
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks(test, logSaverListener, mockRescheduler);
        assertEquals(2, mUriCapture.getValue().size());
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])}
     * scenario with {@link IStrictShardableTest}.
     */
    public void testInvoke_strictShardableTest() throws Throwable {
        String[] commandLine = {"config", "arg"};
        int shardCount = 10;
        IStrictShardableTest test = EasyMock.createMock(IStrictShardableTest.class);
        mStubConfiguration.setTest(test);
        mStubConfiguration.setCommandLine(commandLine);
        mStubConfiguration.getCommandOptions().setShardCount(shardCount);
        mMockBuildInfo.setTestTag(EasyMock.eq("stub"));
        EasyMock.expectLastCall();

        setupInvoke();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn("");
        mMockBuildInfo.addBuildAttribute("command_line_args", "config arg");
        mMockBuildInfo.addBuildAttribute("shard_count", "10");
        IConfiguration shardConfig = new Configuration("foo", "bar");
        for (int i = 1; i <= shardCount; i++) {
            EasyMock.expect(
                    mMockConfigFactory.createConfigurationFromArgs((String[])EasyMock.anyObject()))
                    .andReturn(shardConfig);
            EasyMock.expect(mMockBuildInfo.clone()).andReturn(mMockBuildInfo);
            EasyMock.expect(mockRescheduler.scheduleConfig(shardConfig)).andReturn(true);
        }
        mMockBuildInfo.setDeviceSerial(SERIAL);
        EasyMock.expectLastCall();
        replayMocks(test, mockRescheduler);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verifyMocks(test, mockRescheduler);
        assertEquals(shardCount, (int)shardConfig.getCommandOptions().getShardCount());
        assertEquals(shardCount - 1, (int)shardConfig.getCommandOptions().getShardIndex());
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])}
     * scenario with {@link IStrictShardableTest} when a shard index is given.
     */
    public void testInvoke_strictShardableTest_withShardIndex() throws Throwable {
        String[] commandLine = {"config", "arg"};
        int shardCount = 10;
        int shardIndex = 5;
        IStrictShardableTest test = EasyMock.createMock(IStrictShardableTest.class);
        IRemoteTest testShard = EasyMock.createMock(IRemoteTest.class);
        mStubConfiguration.setTest(test);
        mStubConfiguration.setCommandLine(commandLine);
        mStubConfiguration.getCommandOptions().setShardCount(shardCount);
        mStubConfiguration.getCommandOptions().setShardIndex(shardIndex);

        setupInvokeWithBuild();
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "config arg");
        mMockBuildInfo.addBuildAttribute("shard_count", "10");
        mMockBuildInfo.addBuildAttribute("shard_index", "5");
        EasyMock.expect(test.getTestShard(shardCount, shardIndex)).andReturn(testShard);
        testShard.run((ITestInvocationListener)EasyMock.anyObject());
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        replayMocks(test, testShard);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verifyMocks(test, testShard);
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])}
     * scenario with non-{@link IStrictShardableTest} when shard index 0 is given.
     */
    public void testInvoke_nonStrictShardableTest_withShardIndexZero() throws Throwable {
        String[] commandLine = {"config", "arg"};
        int shardCount = 10;
        int shardIndex = 0;
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        mStubConfiguration.setTest(test);
        mStubConfiguration.setCommandLine(commandLine);
        mStubConfiguration.getCommandOptions().setShardCount(shardCount);
        mStubConfiguration.getCommandOptions().setShardIndex(shardIndex);

        setupInvokeWithBuild();
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "config arg");
        mMockBuildInfo.addBuildAttribute("shard_count", "10");
        mMockBuildInfo.addBuildAttribute("shard_index", "0");
        test.run((ITestInvocationListener)EasyMock.anyObject());
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        replayMocks(test);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verifyMocks(test);
    }

    /**
     * Test the {@link TestInvocation#invoke(IInvocationContext, IConfiguration, IRescheduler,
     * ITestInvocationListener[])}
     * scenario with non-{@link IStrictShardableTest} when a shard index non-0 is given.
     */
    public void testInvoke_nonStrictShardableTest_withShardIndexNonZero() throws Throwable {
        String[] commandLine = {"config", "arg"};
        int shardCount = 10;
        int shardIndex = 1;
        IStrictShardableTest shardableTest = EasyMock.createMock(IStrictShardableTest.class);
        IRemoteTest test = EasyMock.createMock(IRemoteTest.class);
        EasyMock.expect(shardableTest.getTestShard(10, 1)).andReturn(test);
        test.run((ITestInvocationListener)EasyMock.anyObject());
        EasyMock.expectLastCall();
        mStubConfiguration.setTest(shardableTest);
        mStubConfiguration.setCommandLine(commandLine);
        mStubConfiguration.getCommandOptions().setShardCount(shardCount);
        mStubConfiguration.getCommandOptions().setShardIndex(shardIndex);

        setupInvokeWithBuild();
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "config arg");
        mMockBuildInfo.addBuildAttribute("shard_count", "10");
        mMockBuildInfo.addBuildAttribute("shard_index", "1");
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        replayMocks(shardableTest, test);

        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);

        verifyMocks(shardableTest, test);
    }

    /**
     * Test the test-tag is set when the IBuildInfo's test-tag is not.
     */
    public void testInvoke_testtag() throws Throwable {
        String[] commandLine = {"run", "empty"};
        mStubConfiguration.setCommandLine(commandLine);
        mStubConfiguration.getCommandOptions().setTestTag("not-default");

        setupInvoke();
        EasyMock.expect(mMockDevice.getLogcat())
                .andReturn(new ByteArrayInputStreamSource(new byte[0])).times(2);
        EasyMock.expect(mMockLogger.getLog())
                .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        mMockBuildInfo.setDeviceSerial(SERIAL);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "run empty");
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        // Default build is "stub" so we set the test-tag
        mMockBuildInfo.setTestTag(EasyMock.eq("not-default"));
        EasyMock.expectLastCall();
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn("stub");
        replayMocks();
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks();
    }

    /**
     * Test the test-tag of the IBuildInfo is not modified when the CommandOption default test-tag
     * is not modified.
     */
    public void testInvoke_testtag_notset() throws Throwable {
        String[] commandLine = {"run", "empty"};
        mStubConfiguration.setCommandLine(commandLine);
        setupInvoke();
        EasyMock.expect(mMockDevice.getLogcat())
                .andReturn(new ByteArrayInputStreamSource(new byte[0])).times(2);
        EasyMock.expect(mMockLogger.getLog())
                .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        mMockBuildInfo.setDeviceSerial(SERIAL);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "run empty");
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn("buildprovidertesttag");

        replayMocks();
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks();
    }

    /**
     * Test the test-tag of the IBuildInfo is not set and Command Option is not set either.
     * A default 'stub' test-tag is set to ensure reporting is done.
     */
    public void testInvoke_notesttag() throws Throwable {
        String[] commandLine = {"run", "empty"};
        mStubConfiguration.setCommandLine(commandLine);
        setupInvoke();
        EasyMock.expect(mMockDevice.getLogcat())
                .andReturn(new ByteArrayInputStreamSource(new byte[0])).times(2);
        EasyMock.expect(mMockLogger.getLog())
                .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        mMockBuildInfo.setDeviceSerial(SERIAL);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        setupMockSuccessListeners();
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);
        mMockBuildInfo.addBuildAttribute("command_line_args", "run empty");
        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn(null);
        mMockBuildInfo.setTestTag(EasyMock.eq("stub"));
        EasyMock.expectLastCall();
        replayMocks();
        mTestInvocation.invoke(mStubInvocationMetadata, mStubConfiguration, mockRescheduler);
        verifyMocks();
    }
    /**
     * Set up expected conditions for normal run up to the part where tests are run.
     *
     * @param test the {@link Test} to use.
     */
    private void setupNormalInvoke(IRemoteTest test) throws Throwable {
        setupInvokeWithBuild();
        mStubConfiguration.setTest(test);
        mStubMultiConfiguration.setTest(test);
        EasyMock.expect(mMockBuildProvider.getBuild()).andReturn(mMockBuildInfo);

        mMockPreparer.setUp(mMockDevice, mMockBuildInfo);

        replayMocks(test);
    }

    /**
     * Set up expected calls that occur on every invoke, regardless of result
     */
    private void setupInvoke() {
        mMockDevice.clearLastConnectedWifiNetwork();
        mMockDevice.setOptions((TestDeviceOptions)EasyMock.anyObject());
        mMockDevice.startLogcat();
        mMockDevice.clearLastConnectedWifiNetwork();
        mMockDevice.stopLogcat();
    }

    /**
     * Set up expected calls that occur on every invoke that gets a valid build
     */
    private void setupInvokeWithBuild() {
        setupInvoke();
        EasyMock.expect(mMockDevice.getLogcat())
                .andReturn(new ByteArrayInputStreamSource(new byte[0])).times(2);

        EasyMock.expect(mMockLogger.getLog())
                .andReturn(new ByteArrayInputStreamSource(new byte[0]));
        mMockBuildInfo.setDeviceSerial(SERIAL);
        mMockBuildProvider.cleanUp(mMockBuildInfo);
        mMockBuildInfo.setTestTag(EasyMock.eq("stub"));
        EasyMock.expectLastCall();
        EasyMock.expect(mMockBuildInfo.getTestTag()).andStubReturn("");
    }

    /**
     * Set up expected conditions for the test InvocationListener and SummaryListener
     * <p/>
     * The order of calls for a single listener should be:
     * <ol>
     *   <li>invocationStarted</li>
     *   <li>invocationFailed (if run failed)</li>
     *   <li>testLog(DEVICE_LOG_NAME, ...)</li>
     *   <li>testLog(TRADEFED_LOG_NAME, ...)</li>
     *   <li>putSummary (for an ITestSummaryListener)</li>
     *   <li>invocationEnded</li>
     *   <li>getSummary (for an ITestInvocationListener)</li>
     * </ol>
     * However note that, across all listeners, any getSummary call will precede all putSummary
     * calls.
     */
    private void setupMockListeners(InvocationStatus status, Throwable throwable)
            throws IOException {
        // invocationStarted
        mMockLogSaver.invocationStarted(mStubInvocationMetadata);
        mMockTestListener.invocationStarted(mStubInvocationMetadata);
        mMockSummaryListener.invocationStarted(mStubInvocationMetadata);

        // invocationFailed
        if (!status.equals(InvocationStatus.SUCCESS)) {
            mMockTestListener.invocationFailed(EasyMock.eq(throwable));
            mMockSummaryListener.invocationFailed(EasyMock.eq(throwable));
        }

        if (throwable instanceof BuildError) {
            EasyMock.expect(mMockLogSaver.saveLogData(
                    EasyMock.eq(TestInvocation.BUILD_ERROR_BUGREPORT_NAME + "_" + SERIAL),
                    EasyMock.eq(LogDataType.BUGREPORT), (InputStream)EasyMock.anyObject()))
                    .andReturn(new LogFile(PATH, URL, false /* compressed */, true /* text */));
            mMockTestListener.testLog(
                    EasyMock.eq(TestInvocation.BUILD_ERROR_BUGREPORT_NAME + "_" + SERIAL),
                    EasyMock.eq(LogDataType.BUGREPORT), (InputStreamSource)EasyMock.anyObject());
            mMockSummaryListener.testLog(
                    EasyMock.eq(TestInvocation.BUILD_ERROR_BUGREPORT_NAME + "_" + SERIAL),
                    EasyMock.eq(LogDataType.BUGREPORT), (InputStreamSource)EasyMock.anyObject());
        }

        // saveAndZipLogData (mMockLogFileSaver)
        EasyMock.expect(mMockLogSaver.saveLogData( EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStream)EasyMock.anyObject())
                ).andReturn(new LogFile(PATH, URL, false /* compressed */, true /* text */));
        // testLog (mMockTestListener)
        mMockTestListener.testLog(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStreamSource)EasyMock.anyObject());
        // testLog (mMockSummaryListener)
        mMockSummaryListener.testLog(EasyMock.eq(TestInvocation.DEVICE_LOG_NAME),
                EasyMock.eq(LogDataType.LOGCAT), (InputStreamSource)EasyMock.anyObject());

        EasyMock.expect(mMockLogSaver.saveLogData(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStream)EasyMock.anyObject())
                ).andReturn(new LogFile(PATH, URL, false /* compressed */, true /* text */));
        mMockTestListener.testLog(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStreamSource)EasyMock.anyObject());
        mMockSummaryListener.testLog(EasyMock.eq(TestInvocation.TRADEFED_LOG_NAME),
                EasyMock.eq(LogDataType.TEXT), (InputStreamSource)EasyMock.anyObject());

        // invocationEnded, getSummary (mMockTestListener)
        mMockTestListener.invocationEnded(EasyMock.anyLong());
        EasyMock.expect(mMockTestListener.getSummary()).andReturn(mSummary);

        // putSummary, invocationEnded (mMockSummaryListener)
        mMockSummaryListener.putSummary(EasyMock.capture(mUriCapture));
        mMockSummaryListener.invocationEnded(EasyMock.anyLong());
        mMockLogSaver.invocationEnded(EasyMock.anyLong());
    }

    private void setupMockSuccessListeners() throws IOException {
        setupMockListeners(InvocationStatus.SUCCESS, null);
    }

    private void setupMockFailureListeners(Throwable throwable) throws IOException {
        setupMockListeners(InvocationStatus.FAILED, throwable);
    }

    private void verifySummaryListener() {
        // Check that we captured the expected uris List
        List<TestSummary> summaries = mUriCapture.getValue();
        assertEquals(1, summaries.size());
        assertEquals(mSummary, summaries.get(0));
    }

    /**
     * Verify all mock objects received expected calls
     */
    private void verifyMocks(Object... mocks) {
        // note: intentionally exclude configuration from verification - don't care
        // what methods are called
        EasyMock.verify(mMockTestListener, mMockSummaryListener, mMockPreparer,
                mMockBuildProvider, mMockLogger, mMockBuildInfo, mMockLogRegistry,
                mMockLogSaver);
        if (mocks.length > 0) {
            EasyMock.verify(mocks);
        }
    }

    /**
     * Switch all mock objects into replay mode.
     */
    private void replayMocks(Object... mocks) {
        EasyMock.replay(mMockTestListener, mMockSummaryListener, mMockPreparer,
                mMockBuildProvider, mMockLogger, mMockBuildInfo, mMockLogRegistry,
                mMockLogSaver, mMockDevice, mMockConfigFactory);
        if (mocks.length > 0) {
            EasyMock.replay(mocks);
        }
    }

    /**
     * Interface for testing device config pass through.
     */
    private interface DeviceConfigTest extends IRemoteTest, IDeviceTest {

    }
}
