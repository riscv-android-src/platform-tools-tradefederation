package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.io.File;

/** @deprecated Delete after July 29th week deployment */
@Deprecated
public class PreloadedClassesPreparer extends BaseTargetPreparer {

    private static final long DEFAULT_TIMEOUT_MS = 5 * 60 * 1000;

    @Option(
        name = "preload-file",
        description = "The new preloaded classes file to put on the device."
    )
    private File mNewClassesFile = null;

    @Option(name = "preload-tool", description = "Overridden location of the preload tool JAR.")
    private String mPreloadToolJarPath = "";

    @Option(name = "skip", description = "Skip this preparer entirely.")
    private boolean mSkip = false;

    @Option(
        name = "write-timeout",
        isTimeVal = true,
        description = "Maximum timeout for overwriting the file in milliseconds."
    )
    private long mWriteTimeout = DEFAULT_TIMEOUT_MS;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // Inop
    }
}
