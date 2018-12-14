#!/usr/bin/env python
#
# Copyright 2017, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Command line utility for running Android tests through TradeFederation.

atest helps automate the flow of building test modules across the Android
code base and executing the tests via the TradeFederation test harness.

atest is designed to support any test types that can be ran by TradeFederation.
"""

from __future__ import print_function

import logging
import os
import sys
import tempfile
import time
import platform

import atest_arg_parser
import atest_metrics
import atest_utils
import cli_translator
# pylint: disable=import-error
import constants
import module_info
import result_reporter
import test_runner_handler
from metrics import metrics
from metrics import metrics_utils
from test_runners import regression_test_runner

EXPECTED_VARS = frozenset([
    constants.ANDROID_BUILD_TOP,
    'ANDROID_TARGET_OUT_TESTCASES',
    constants.ANDROID_OUT])
TEST_RUN_DIR_PREFIX = 'atest_run_%s_'
CUSTOM_ARG_FLAG = '--'
OPTION_NOT_FOR_TEST_MAPPING = (
    'Option `%s` does not work for running tests in TEST_MAPPING files')


def _parse_args(argv):
    """Parse command line arguments.

    Args:
        argv: A list of arguments.

    Returns:
        An argspace.Namespace class instance holding parsed args.
    """
    # Store everything after '--' in custom_args.
    pruned_argv = argv
    custom_args_index = None
    if CUSTOM_ARG_FLAG in argv:
        custom_args_index = argv.index(CUSTOM_ARG_FLAG)
        pruned_argv = argv[:custom_args_index]
    parser = atest_arg_parser.AtestArgParser()
    parser.add_atest_args()
    args = parser.parse_args(pruned_argv)
    args.custom_args = []
    if custom_args_index is not None:
        args.custom_args = argv[custom_args_index+1:]
    return args


def _configure_logging(verbose):
    """Configure the logger.

    Args:
        verbose: A boolean. If true display DEBUG level logs.
    """
    log_format = '%(asctime)s %(filename)s:%(lineno)s:%(levelname)s: %(message)s'
    datefmt = '%Y-%m-%d %H:%M:%S'
    if verbose:
        logging.basicConfig(level=logging.DEBUG, format=log_format, datefmt=datefmt)
    else:
        logging.basicConfig(level=logging.INFO, format=log_format, datefmt=datefmt)


def _missing_environment_variables():
    """Verify the local environment has been set up to run atest.

    Returns:
        List of strings of any missing environment variables.
    """
    missing = filter(None, [x for x in EXPECTED_VARS if not os.environ.get(x)])
    if missing:
        logging.error('Local environment doesn\'t appear to have been '
                      'initialized. Did you remember to run lunch? Expected '
                      'Environment Variables: %s.', missing)
    return missing


def make_test_run_dir():
    """Make the test run dir in tmp.

    Returns:
        A string of the dir path.
    """
    utc_epoch_time = int(time.time())
    prefix = TEST_RUN_DIR_PREFIX % utc_epoch_time
    return tempfile.mkdtemp(prefix=prefix)


def get_extra_args(args):
    """Get extra args for test runners.

    Args:
        args: arg parsed object.

    Returns:
        Dict of extra args for test runners to utilize.
    """
    extra_args = {}
    if args.wait_for_debugger:
        extra_args[constants.WAIT_FOR_DEBUGGER] = None
    steps = args.steps or constants.ALL_STEPS
    if constants.INSTALL_STEP not in steps:
        extra_args[constants.DISABLE_INSTALL] = None
    if args.disable_teardown:
        extra_args[constants.DISABLE_TEARDOWN] = args.disable_teardown
    if args.generate_baseline:
        extra_args[constants.PRE_PATCH_ITERATIONS] = args.generate_baseline
    if args.serial:
        extra_args[constants.SERIAL] = args.serial
    if args.all_abi:
        extra_args[constants.ALL_ABI] = args.all_abi
    if args.generate_new_metrics:
        extra_args[constants.POST_PATCH_ITERATIONS] = args.generate_new_metrics
    if args.host:
        extra_args[constants.HOST] = args.host
    if args.custom_args:
        extra_args[constants.CUSTOM_ARGS] = args.custom_args
    return extra_args


def _get_regression_detection_args(args, results_dir):
    """Get args for regression detection test runners.

    Args:
        args: parsed args object.
        results_dir: string directory to store atest results.

    Returns:
        Dict of args for regression detection test runner to utilize.
    """
    regression_args = {}
    pre_patch_folder = (os.path.join(results_dir, 'baseline-metrics') if args.generate_baseline
                        else args.detect_regression.pop(0))
    post_patch_folder = (os.path.join(results_dir, 'new-metrics') if args.generate_new_metrics
                         else args.detect_regression.pop(0))
    regression_args[constants.PRE_PATCH_FOLDER] = pre_patch_folder
    regression_args[constants.POST_PATCH_FOLDER] = post_patch_folder
    return regression_args


def _validate_exec_mode(args, test_infos):
    """Validate all test execution modes are not in conflict.

    Exit the program with error code if have device-only and host-only.
    If no conflict and host side, add args.host=True.

    Args:
        args: parsed args object.
        test_info: TestInfo object.

    Returns:
        Updated args.
    """
    all_device_modes = [x.get_supported_exec_mode() for x in test_infos]
    # In the case of '$atest <device-only> --host', exit.
    if args.host and constants.DEVICE_TEST in all_device_modes:
        err_msg = ('Test side and option(--host) conflict. Please remove '
                   '--host if the test run on device side.')
        logging.error(err_msg)
        sys.exit(constants.EXIT_CODE_ERROR)
    # In the case of '$atest <host-only> <device-only> --host' or
    # '$atest <host-only> <device-only>', exit.
    if (constants.DEVICELESS_TEST in all_device_modes and
            constants.DEVICE_TEST in all_device_modes):
        err_msg = 'There are host-only and device-only tests in command.'
        logging.error(err_msg)
        sys.exit(constants.EXIT_CODE_ERROR)
    # In the case of '$atest <host-only>', we add --host to run on host-side.
    if not args.host:
        args.host = bool(constants.DEVICELESS_TEST in all_device_modes)
    return args

def _will_run_tests(args):
    """Determine if there are tests to run.

    Currently only used by detect_regression to skip the test if just running regression detection.

    Args:
        args: parsed args object.

    Returns:
        True if there are tests to run, false otherwise.
    """
    return not (args.detect_regression and len(args.detect_regression) == 2)


def _has_valid_regression_detection_args(args):
    """Validate regression detection args.

    Args:
        args: parsed args object.

    Returns:
        True if args are valid
    """
    if args.generate_baseline and args.generate_new_metrics:
        logging.error('Cannot collect both baseline and new metrics at the same time.')
        return False
    if args.detect_regression is not None:
        if not args.detect_regression:
            logging.error('Need to specify at least 1 arg for regression detection.')
            return False
        elif len(args.detect_regression) == 1:
            if args.generate_baseline or args.generate_new_metrics:
                return True
            logging.error('Need to specify --generate-baseline or --generate-new-metrics.')
            return False
        elif len(args.detect_regression) == 2:
            if args.generate_baseline:
                logging.error('Specified 2 metric paths and --generate-baseline, '
                              'either drop --generate-baseline or drop a path')
                return False
            if args.generate_new_metrics:
                logging.error('Specified 2 metric paths and --generate-new-metrics, '
                              'either drop --generate-new-metrics or drop a path')
                return False
            return True
        else:
            logging.error('Specified more than 2 metric paths.')
            return False
    return True


def _has_valid_test_mapping_args(args):
    """Validate test mapping args.

    Not all args work when running tests in TEST_MAPPING files. Validate the
    args before running the tests.

    Args:
        args: parsed args object.

    Returns:
        True if args are valid
    """
    is_test_mapping = atest_utils.is_test_mapping(args)
    if not is_test_mapping:
        return True
    options_to_validate = [
        (args.generate_baseline, '--generate-baseline'),
        (args.detect_regression, '--detect-regression'),
        (args.generate_new_metrics, '--generate-new-metrics'),
    ]
    for arg_value, arg in options_to_validate:
        if arg_value:
            logging.error(OPTION_NOT_FOR_TEST_MAPPING, arg)
            return False
    return True


def _validate_args(args):
    """Validate setups and args.

    Exit the program with error code if any setup or arg is invalid.

    Args:
        args: parsed args object.
    """
    if _missing_environment_variables():
        sys.exit(constants.EXIT_CODE_ENV_NOT_SETUP)
    if args.generate_baseline and args.generate_new_metrics:
        logging.error('Cannot collect both baseline and new metrics at the same time.')
        sys.exit(constants.EXIT_CODE_ERROR)
    if not _has_valid_regression_detection_args(args):
        sys.exit(constants.EXIT_CODE_ERROR)
    if not _has_valid_test_mapping_args(args):
        sys.exit(constants.EXIT_CODE_ERROR)


def _print_module_info_from_module_name(mod_info, module_name):
    """print out the related module_info for a module_name.

    Args:
        mod_info: ModuleInfo object.
        module_name: A string of module.

    Returns:
        True if the module_info is found.
    """
    title_mapping = {constants.MODULE_PATH: "Source code path",
                     constants.MODULE_INSTALLED: "Installed path",
                     constants.MODULE_COMPATIBILITY_SUITES: "Compatibility suite"}
    target_module_info = mod_info.get_module_info(module_name)
    is_module_found = False
    if target_module_info:
        atest_utils.colorful_print(module_name, constants.GREEN)
        for title_key in title_mapping.iterkeys():
            atest_utils.colorful_print("\t%s" % title_mapping[title_key],
                                       constants.CYAN)
            for info_value in target_module_info[title_key]:
                print("\t\t{}".format(info_value))
        is_module_found = True
    return is_module_found


def _print_test_info(mod_info, test_infos):
    """Print the module information from TestInfos.

    Args:
        mod_info: ModuleInfo object.
        test_infos: A list of TestInfos.

    Returns:
        Always return EXIT_CODE_SUCCESS
    """
    for test_info in test_infos:
        _print_module_info_from_module_name(mod_info, test_info.test_name)
        atest_utils.colorful_print("\tRelated build targets", constants.MAGENTA)
        print("\t\t{}".format(", ".join(test_info.build_targets)))
        for build_target in test_info.build_targets:
            if build_target != test_info.test_name:
                _print_module_info_from_module_name(mod_info, build_target)
        atest_utils.colorful_print("", constants.WHITE)
    return constants.EXIT_CODE_SUCCESS

def main(argv):
    """Entry point of atest script.

    Args:
        argv: A list of arguments.

    Returns:
        Exit code.
    """
    args = _parse_args(argv)
    _configure_logging(args.verbose)
    _validate_args(args)
    atest_metrics.log_start_event()
    start = time.time()
    metrics.AtestStartEvent(
        command_line=' '.join(argv),
        test_references=args.tests,
        cwd=os.getcwd(),
        os=platform.platform())
    results_dir = make_test_run_dir()
    mod_info = module_info.ModuleInfo(force_build=args.rebuild_module_info)
    translator = cli_translator.CLITranslator(module_info=mod_info)
    build_targets = set()
    test_infos = set()
    if _will_run_tests(args):
        build_targets, test_infos = translator.translate(args)
        if not test_infos:
            metrics_utils.send_exit_event(start, constants.EXIT_CODE_TEST_NOT_FOUND)
            return constants.EXIT_CODE_TEST_NOT_FOUND
        args = _validate_exec_mode(args, test_infos)
    if args.info:
        metrics_utils.send_exit_event(start, constants.EXIT_CODE_SUCCESS)
        return _print_test_info(mod_info, test_infos)
    build_targets |= test_runner_handler.get_test_runner_reqs(mod_info,
                                                              test_infos)
    extra_args = get_extra_args(args)
    if args.detect_regression:
        build_targets |= (regression_test_runner.RegressionTestRunner('')
                          .get_test_runner_build_reqs())
    # args.steps will be None if none of -bit set, else list of params set.
    steps = args.steps if args.steps else constants.ALL_STEPS
    if build_targets and constants.BUILD_STEP in steps:
        # Add module-info.json target to the list of build targets to keep the
        # file up to date.
        build_targets.add(mod_info.module_info_target)
        success = atest_utils.build(build_targets, args.verbose)
        if not success:
            metrics_utils.send_exit_event(start, constants.EXIT_CODE_BUILD_FAILURE)
            return constants.EXIT_CODE_BUILD_FAILURE
    elif constants.TEST_STEP not in steps:
        logging.warn('Install step without test step currently not '
                     'supported, installing AND testing instead.')
        steps.append(constants.TEST_STEP)
    tests_exit_code = constants.EXIT_CODE_SUCCESS
    if constants.TEST_STEP in steps:
        tests_exit_code = test_runner_handler.run_all_tests(
            results_dir, test_infos, extra_args)
    if args.detect_regression:
        regression_args = _get_regression_detection_args(args, results_dir)
        # TODO(b/110485713): Should not call run_tests here.
        reporter = result_reporter.ResultReporter()
        tests_exit_code |= regression_test_runner.RegressionTestRunner('').run_tests(
            None, regression_args, reporter)
    if tests_exit_code != constants.EXIT_CODE_SUCCESS:
        tests_exit_code = constants.EXIT_CODE_TEST_FAILURE
    metrics_utils.send_exit_event(start, tests_exit_code)
    return tests_exit_code

if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
