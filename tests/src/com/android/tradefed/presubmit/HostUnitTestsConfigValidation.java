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
package com.android.tradefed.presubmit;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.ConfigurationUtil;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.PushFilePreparer;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.ValidateSuiteConfigHelper;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.testmapping.TestInfo;
import com.android.tradefed.util.testmapping.TestMapping;

import com.google.common.base.Joiner;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validation tests to run against the configuration in host-unit-tests.zip to ensure they can all
 * parse.
 *
 * <p>Do not add to UnitTests.java. This is meant to run standalone.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class HostUnitTestsConfigValidation implements IBuildReceiver {

    private IBuildInfo mBuild;

    /**
     * List of the officially supported runners in general-tests. Any new addition should go through
     * a review to ensure all runners have a high quality bar.
     */
    private static final Set<String> SUPPORTED_TEST_RUNNERS =
            new HashSet<>(
                    Arrays.asList(
                            // Only accept runners that can be pure host-tests.
                            "com.android.compatibility.common.tradefed.testtype.JarHostTest",
                            "com.android.tradefed.testtype.HostTest",
                            "com.android.tradefed.testtype.HostGTest",
                            "com.android.tradefed.testtype.IsolatedHostTest",
                            "com.android.tradefed.testtype.python.PythonBinaryHostTest",
                            "com.android.tradefed.testtype.binary.ExecutableHostTest",
                            "com.android.tradefed.testtype.rust.RustBinaryHostTest"));

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    /** Get all the configuration copied to the build tests dir and check if they load. */
    @Test
    public void testConfigsLoad() throws Exception {
        List<String> errors = new ArrayList<>();
        Assume.assumeTrue(mBuild instanceof IDeviceBuildInfo);

        IConfigurationFactory configFactory = ConfigurationFactory.getInstance();
        List<String> configs = new ArrayList<>();
        IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) mBuild;
        File testsDir = deviceBuildInfo.getTestsDir();
        List<File> extraTestCasesDirs = Arrays.asList(testsDir);
        configs.addAll(ConfigurationUtil.getConfigNamesFromDirs(null, extraTestCasesDirs));
        for (String configName : configs) {
            try {
                IConfiguration c =
                        configFactory.createConfigurationFromArgs(new String[] {configName});
                // All configurations in host-unit-tests.zip should be module since they are
                // generated from AndroidTest.xml
                ValidateSuiteConfigHelper.validateConfig(c);

                checkPreparers(c.getTargetPreparers(), "host-unit-tests");
                // Check that all the tests runners are well supported.
                checkRunners(c.getTests(), "host-unit-tests");

                // Add more checks if necessary
            } catch (ConfigurationException e) {
                errors.add(String.format("\t%s: %s", configName, e.getMessage()));
            }
        }

        // If any errors report them in a final exception.
        if (!errors.isEmpty()) {
            throw new ConfigurationException(
                    String.format("Fail configuration check:\n%s", Joiner.on("\n").join(errors)));
        }
    }

    private static void checkPreparers(List<ITargetPreparer> preparers, String name)
            throws ConfigurationException {
        for (ITargetPreparer preparer : preparers) {
            // Check that all preparers are supported.
            if (preparer instanceof PushFilePreparer) {
                throw new ConfigurationException(
                        String.format(
                                "preparer %s is not supported in %s.",
                                preparer.getClass().getCanonicalName(), name));
            }
        }
    }

    private static void checkRunners(List<IRemoteTest> tests, String name)
            throws ConfigurationException {
        for (IRemoteTest test : tests) {
            // Check that all the tests runners are well supported.
            if (!SUPPORTED_TEST_RUNNERS.contains(test.getClass().getCanonicalName())) {
                throw new ConfigurationException(
                        String.format(
                                "testtype %s is not officially supported in %s. "
                                        + "The supported ones are: %s",
                                test.getClass().getCanonicalName(), name, SUPPORTED_TEST_RUNNERS));
            }
        }
    }

    // This list contains exemption to the duplication of host-unit-tests & TEST_MAPPING.
    // This will be used when migrating default and clean up as we clear the TEST_MAPPING files.
    private static final Set<String> EXEMPTION_LIST =
            new HashSet<>(
                    Arrays.asList(
                            "env_logger_host_test_src_lib",
                            "slab_host_test_src_lib",
                            "protobuf-codegen_host_test_src_lib",
                            "bencher_host_test_lib",
                            "url_host_test_src_lib",
                            "proc-macro2_host_test_tests_comments",
                            "tinyvec_macros_host_test_src_lib",
                            "futures-util_host_test_src_lib",
                            "linux_input_sys_host_test_src_lib",
                            "arch_host_test_src_lib",
                            "rand_xorshift_host_test_tests_mod",
                            "audio_streams_host_test_src_audio_streams",
                            "pin-project-internal_host_test_src_lib",
                            "fnv_host_test_lib",
                            "ring_host_test_tests_ecdsa_tests",
                            "lru-cache_host_test_src_lib",
                            "ppv-lite86_host_test_src_lib",
                            "pin-utils_host_test_tests_projection",
                            "base_host_test_src_lib",
                            "futures-sink_host_test_src_lib",
                            "vhost_host_test_src_lib",
                            "ring_host_test_tests_constant_time_tests",
                            "vfio_sys_host_test_src_lib",
                            "linked-hash-map_host_test_tests_heapsize",
                            "msg_socket_host_test_tests_tuple",
                            "fallible-streaming-iterator_host_test_src_lib",
                            "kernel_loader_host_test_src_lib",
                            "tempfile_host_test_src_lib",
                            "bit_field_derive_host_test_bit_field_derive",
                            "structopt-derive_host_test_src_lib",
                            "futures-io_host_test_src_lib",
                            "downcast-rs_host_test_tests_import_via_macro_use",
                            "ring_host_test_tests_digest_tests",
                            "fuse_host_test_src_lib",
                            "ring_host_test_tests_signature_tests",
                            "rustc-hash_host_test_src_lib",
                            "memoffset_host_test_src_lib",
                            "libsqlite3-sys_host_test_src_lib",
                            "sync_host_test_src_lib",
                            "p9_host_test_src_lib",
                            "releasetools_test",
                            "bitflags_host_test_src_lib",
                            "downcast-rs_host_test_src_lib",
                            "msg_socket_host_test_tests_struct",
                            "msg_on_socket_derive_host_test_msg_on_socket_derive",
                            "cfg-if_host_test_src_lib",
                            "enumn_host_test_src_lib",
                            "shlex_host_test_src_lib",
                            "tokio-macros_host_test_src_lib",
                            "crosvm_host_test_src_main",
                            "parking_lot_core_host_test_src_lib",
                            "ring_host_test_tests_aead_tests",
                            "ring_host_test_src_lib",
                            "ring_host_test_tests_pbkdf2_tests",
                            "libchromeos-rs_host_test_src_lib",
                            "bit_field_host_test_tests_test_tuple_struct",
                            "thread_local_host_test_src_lib",
                            "env_logger_host_test_src_lib",
                            "ring_host_test_tests_hmac_tests",
                            "slab_host_test_tests_slab",
                            "futures-task_host_test_src_lib",
                            "cras-sys_host_test_src_lib",
                            "syscall_defines_host_test_src_lib",
                            "vm_control_host_test_src_lib",
                            "ring_host_test_tests_agreement_tests",
                            "unicode-normalization_host_test_src_lib",
                            "proc-macro2_host_test_src_lib",
                            "poll_token_derive_host_test_poll_token_derive",
                            "peeking_take_while_host_test_src_lib",
                            "kernel_cmdline_host_test_src_kernel_cmdline",
                            "fallible-iterator_host_test_src_lib",
                            "usb_util_host_test_src_lib",
                            "rand_core_host_test_src_lib",
                            "syn-mid_host_test_src_lib",
                            "proc-macro2_host_test_tests_features",
                            "futures-core_host_test_src_lib",
                            "acpi_tables_host_test_src_lib",
                            "proc-macro2_host_test_tests_marker",
                            "assertions_host_test_src_lib",
                            "linked-hash-map_host_test_tests_serde",
                            "num-traits_host_test_src_lib",
                            "linked-hash-map_host_test_src_lib",
                            "ring_host_test_tests_rand_tests",
                            "resources_host_test_src_lib",
                            "libm_host_test_src_lib",
                            "bit_field_host_test_tests_test_enum",
                            "proc-macro2_host_test_tests_test",
                            "msg_socket_host_test_tests_enum",
                            "ring_host_test_tests_quic_tests",
                            "bit_field_host_test_src_lib",
                            "ring_host_test_tests_ed25519_tests",
                            "data_model_host_test_src_lib",
                            "unicode-xid_host_test_tests_exhaustive_tests",
                            "proc-macro-error-attr_host_test_src_lib",
                            "recovery_host_test",
                            "libcras_host_test_src_libcras",
                            "cfg-if_host_test_tests_xcrate",
                            "unicode-bidi_host_test_src_lib",
                            "vm_memory_host_test_src_lib",
                            "usb_sys_host_test_src_lib",
                            "scopeguard_host_test_src_lib",
                            "libz-sys_host_test_src_lib",
                            "num-traits_host_test_tests_cast",
                            "msg_socket_host_test_tests_unit",
                            "proc-macro-nested_host_test_src_lib",
                            "net_sys_host_test_src_lib",
                            "lock_api_host_test_src_lib",
                            "ring_host_test_tests_rsa_tests",
                            "rand_ish_host_test_src_lib",
                            "heck_host_test_src_lib",
                            "ring_host_test_tests_hkdf_tests",
                            "linked-hash-map_host_test_tests_test",
                            "msg_socket_host_test_src_lib",
                            "getrandom_host_test_src_lib",
                            "rand_xorshift_host_test_src_lib",
                            "unicode-width_host_test_src_lib",
                            "pin-utils_host_test_src_lib",
                            "downcast-rs_host_test_tests_use_via_namespace",
                            "unicode-xid_host_test_src_lib",
                            "crosvm_host_test_src_crosvm",
                            "virtio_sys_host_test_src_lib",
                            "pin-utils_host_test_tests_stack_pin",
                            "getrandom_host_test_tests_common",
                            "proc-macro2_host_test_tests_test_fmt",
                            "disk_host_test_src_disk"));

    /**
     * This test ensures that unit tests are not also running as part of test mapping to avoid
     * double running them.
     */
    @Test
    public void testNotInTestMapping() {
        // We need the test mapping files for this test.
        Assume.assumeNotNull(mBuild.getFile("test_mappings.zip"));

        Set<TestInfo> testInfosToRun =
                TestMapping.getTests(
                        mBuild, /* group */
                        "presubmit", /* host */
                        true, /* keywords */
                        new HashSet<>());

        List<String> errors = new ArrayList<>();
        List<String> configs = new ArrayList<>();
        IDeviceBuildInfo deviceBuildInfo = (IDeviceBuildInfo) mBuild;
        File testsDir = deviceBuildInfo.getTestsDir();
        List<File> extraTestCasesDirs = Arrays.asList(testsDir);
        configs.addAll(ConfigurationUtil.getConfigNamesFromDirs(null, extraTestCasesDirs));

        Map<String, Set<String>> infos = new HashMap<>();
        testInfosToRun.stream().forEach(e -> infos.put(e.getName(), e.getSources()));
        for (String configName : configs) {
            String moduleName = FileUtil.getBaseName(new File(configName).getName());
            if (infos.containsKey(moduleName) && !EXEMPTION_LIST.contains(moduleName)) {
                errors.add(
                        String.format(
                                "Target '%s' is already running in host-unit-tests, it doesn't "
                                        + "need the test mapping config: %s",
                                moduleName, infos.get(moduleName)));
            }
        }
        if (!errors.isEmpty()) {
            String message =
                    String.format("Fail configuration check:\n%s", Joiner.on("\n").join(errors));
            assertTrue(message, errors.isEmpty());
        }
    }
}
