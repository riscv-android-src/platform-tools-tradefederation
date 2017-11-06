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

import logging
import os
import subprocess
import sys

import cli_translator
ANDROID_BUILD_TOP = 'ANDROID_BUILD_TOP'
EXPECTED_VARS = frozenset([
    ANDROID_BUILD_TOP,
    'ANDROID_TARGET_OUT_TESTCASES',
    'OUT'])
EXIT_CODE_ENV_NOT_SETUP = 1
EXIT_CODE_BUILD_FAILURE = 2
BUILD_CMD = ['make', '-j', '-C', os.environ.get(ANDROID_BUILD_TOP)]
TESTS_HELP_TEXT = '''Tests to run.

Ways to identify a test:
MODULE NAME       Examples: CtsJankDeviceTestCases
CLASS NAME        Examples: CtsDeviceJankUi, android.jank.cts.ui.CtsDeviceJankUi
INTEGRATION NAME  Examples: example/reboot, native-benchmark
FILE PATH         Examples: ., <rel_or_abs_path>/jank, <rel_or_abs_path>/CtsDeviceJankUi.java
'''

def _parse_args(argv):
    """Parse command line arguments.

    Args:
        argv: A list of arguments.

    Returns:
        An argspace.Namespace class instance holding parsed args.
    """
    import argparse
    parser = argparse.ArgumentParser(
        description='Build and run Android tests locally.',
        formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument('tests', nargs='+', help=TESTS_HELP_TEXT)
    parser.add_argument('-v', '--verbose', action='store_true',
                        help='Display DEBUG level logging.')
    parser.add_argument('-s', '--skip-build', action='store_true',
                        help='Skip the build step.')
    parser.add_argument('-w', '--wait-for-debugger', action='store_true',
                        help='Only for instrumentation tests. Waits for '
                             'debugger prior to execution.')
    return parser.parse_args(argv)


def _configure_logging(verbose):
    """Configure the logger.

    Args:
        verbose: A boolean. If true display DEBUG level logs.
    """
    if verbose:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig(level=logging.INFO)


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


def _is_missing_adb(root_dir=''):
    """Check if system built adb is available.

    TF requires adb and we want to make sure we use the latest built adb (vs.
    system adb that might be too old).

    Args:
        root_dir: A String. Path to the root dir that adb should live in.

    Returns:
        True if adb is missing, False otherwise.
    """
    try:
        output = subprocess.check_output(['which', 'adb'])
    except subprocess.CalledProcessError:
        return True
    # TODO: Check if there is a clever way to determine if system adb is good
    # enough.
    return os.path.commonprefix([output, root_dir]) != root_dir


def build_tests(build_targets, verbose=False):
    """Shell out and make build_targets.

    Args:
        build_targets: A set of strings of build targets to make.

    Returns:
        Boolean of whether build command was successful.
    """
    logging.info('Building test targets: %s', ' '.join(build_targets))
    cmd = BUILD_CMD + list(build_targets)
    logging.debug('Executing command: %s', cmd)
    try:
        if verbose:
            subprocess.check_call(cmd, stderr=subprocess.STDOUT)
        else:
            # TODO: Save output to a log file.
            subprocess.check_output(cmd, stderr=subprocess.STDOUT)
        logging.info('Build successful')
        return True
    except subprocess.CalledProcessError as err:
        logging.error('Error building: %s', build_targets)
        if err.output:
            logging.error(err.output)
        return False


def run_tests(run_commands):
    """Shell out and execute tradefed run commands.

    Args:
        run_commands: A list of strings of Tradefed run commands.
    """
    logging.info('Running tests')
    # TODO: Build result parser for run command. Until then display raw stdout.
    for run_command in run_commands:
        logging.debug('Executing command: %s', run_command)
        subprocess.check_call(run_command, shell=True, stderr=subprocess.STDOUT)


def main(argv):
    """Entry point of atest script.

    Args:
        argv: A list of arguments.
    """
    args = _parse_args(argv)
    _configure_logging(args.verbose)
    if _missing_environment_variables():
        return EXIT_CODE_ENV_NOT_SETUP
    repo_root = os.environ.get(ANDROID_BUILD_TOP)
    translator = cli_translator.CLITranslator(root_dir=repo_root)
    build_targets, run_commands = translator.translate(args.tests)
    if args.wait_for_debugger:
        run_commands = [cmd + ' --wait-for-debugger' for cmd in run_commands]
    if _is_missing_adb(root_dir=repo_root):
        build_targets.add('adb')
    if not args.skip_build and not build_tests(build_targets, args.verbose):
        return EXIT_CODE_BUILD_FAILURE
    run_tests(run_commands)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
