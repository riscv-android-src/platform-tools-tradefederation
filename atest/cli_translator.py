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

#pylint: disable=too-many-lines
"""
Command Line Translator for atest.
"""

import json
import logging
import os
import sys
import time

import atest_error
import constants
import test_finder_handler

TEST_MAPPING = 'TEST_MAPPING'


#pylint: disable=no-self-use
class CLITranslator(object):
    """
    CLITranslator class contains public method translate() and some private
    helper methods. The atest tool can call the translate() method with a list
    of strings, each string referencing a test to run. Translate() will
    "translate" this list of test strings into a list of build targets and a
    list of TradeFederation run commands.

    Translation steps for a test string reference:
        1. Narrow down the type of reference the test string could be, i.e.
           whether it could be referencing a Module, Class, Package, etc.
        2. Try to find the test files assuming the test string is one of these
           types of reference.
        3. If test files found, generate Build Targets and the Run Command.
    """

    def __init__(self, module_info=None):
        """CLITranslator constructor

        Args:
            module_info: ModuleInfo class that has cached module-info.json.
        """
        self.mod_info = module_info

    def _get_test_infos(self, tests):
        """Return set of TestInfos based on passed in tests.

        Args:
            tests: List of strings representing test references.

        Returns:
            Set of TestInfos based on the passed in tests.
        """
        test_infos = set()
        for test in tests:
            test_found = False
            for finder in test_finder_handler.get_find_methods_for_test(
                    self.mod_info, test):
                test_info = finder.find_method(finder.test_finder_instance,
                                               test)
                if test_info:
                    test_infos.add(test_info)
                    test_found = True
                    break
            if not test_found:
                raise atest_error.NoTestFoundError('No test found for: %s' %
                                                   test)
        return test_infos

    def _find_tests_by_test_mapping(
            self, path='', test_type=constants.TEST_TYPE_PRESUBMIT,
            file_name=TEST_MAPPING):
        """Find tests defined in TEST_MAPPING in the given path.

        Args:
            path: A string of path in source. Default is set to '', i.e., CWD.
            test_type: Type of tests to run. Default is set to `presubmit`.
            file_name: Name of TEST_MAPPING file. Default is set to
                    `TEST_MAPPING`. The argument is added for testing purpose.

        Returns:
            A tuple of (tests, all_tests), where,
            tests is a set of tests (string) defined in TEST_MAPPING file of
            the given path, and its parent directories, with matching test_type.
            all_tests is a dictionary of all tests in TEST_MAPPING files,
            grouped by test type.
        """
        path = os.path.realpath(path)
        if path == constants.ANDROID_BUILD_TOP or path == os.sep:
            return None, None
        tests = set()
        all_tests = {}
        test_mapping = None
        test_mapping_file = os.path.join(path, file_name)
        if os.path.exists(test_mapping_file):
            with open(test_mapping_file) as json_file:
                test_mapping = json.load(json_file)
            for test_type_name, test_list in test_mapping.items():
                grouped_tests = all_tests.setdefault(test_type_name, set())
                grouped_tests.update([test['name'] for test in test_list])
            for test in test_mapping.get(test_type, []):
                tests.add(test['name'])
        parent_dir_tests, parent_dir_all_tests = (
            self._find_tests_by_test_mapping(
                os.path.dirname(path), test_type, file_name))
        if parent_dir_tests:
            tests.update(parent_dir_tests)
        if parent_dir_all_tests:
            for test_type_name, test_list in parent_dir_all_tests.items():
                grouped_tests = all_tests.setdefault(test_type_name, set())
                grouped_tests.update(test_list)
        if test_type == constants.TEST_TYPE_POSTSUBMIT:
            tests.update(all_tests.get(
                constants.TEST_TYPE_PRESUBMIT, set()))
        return tests, all_tests

    def _gather_build_targets(self, test_infos):
        targets = set()
        for test_info in test_infos:
            targets |= test_info.build_targets
        return targets

    def translate(self, tests):
        """Translate atest command line into build targets and run commands.

        Args:
            tests: A list of strings referencing the tests to run.

        Returns:
            A tuple with set of build_target strings and list of TestInfos.
        """
        if not tests:
            # Pull out tests from test mapping
            # TODO(dshi): Support other types of tests in TEST_MAPPING files,
            # e.g., postsubmit.
            tests, all_tests = self._find_tests_by_test_mapping()
            if not tests:
                logging.warn(
                    'No tests of type %s found in TEST_MAPPING at %s or its '
                    'parent directories.\nYou might be missing atest arguments,'
                    ' try `atest --help` for more information',
                    constants.TEST_TYPE_PRESUBMIT, os.path.realpath(''))
                if all_tests:
                    tests = ''
                    for test_type, test_list in all_tests.items():
                        tests += '%s:\n' % test_type
                        for name in sorted(test_list):
                            tests += '\t%s\n' % name
                    logging.warn(
                        'All available tests in TEST_MAPPING files are:\n%s',
                        tests)
                sys.exit(constants.EXIT_CODE_TEST_NOT_FOUND)
        logging.info('Finding tests: %s', tests)
        start = time.time()
        test_infos = self._get_test_infos(tests)
        end = time.time()
        logging.debug('Found tests in %ss', end - start)
        for test_info in test_infos:
            logging.debug('%s\n', test_info)
        build_targets = self._gather_build_targets(test_infos)
        return build_targets, test_infos
