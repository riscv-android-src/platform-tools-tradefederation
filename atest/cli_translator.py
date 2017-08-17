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
Command Line Translator for atest.
"""

# TODO(b/64273625): Implement logging with proper levels for --verbose.
import enum
import logging
from collections import namedtuple


RUN_CMD = ('tradefed.sh run commandAndExit template/local_min '
           '--template:map test=%s')
TestInfo = namedtuple('TestInfo', ['ref_type', 'module_name'])

class NoTestFoundError(Exception):
    """Raised when no tests are found."""

class TestReferenceType(enum.Enum):
    """Test Reference Type Enums"""
    # MODULE: LOCAL_MODULE or LOCAL_PACKAGE_NAME value in
    # Android.mk/Android.bp file.
    MODULE = 1
    # CLASS: name of java file and class in which test is defined.
    CLASS = 2
    # MODULE_CLASS: Combo of MODULE and CLASS as "module:class".
    MODULE_CLASS = 3
    # PACKAGE: package in java file. Same as file path to java file.
    PACKAGE = 4
    # MODULE_PACKAGE: Combo of MODULE and PACKAGE as "module:package".
    MODULE_PACKAGE = 5
    # FILE_PATH: file path to dir of tests or test itself.
    FILE_PATH = 6
    # INTEGRATION: xml file name in one of the 4 integration config directories.
    INTEGRATION = 7
    # SUITE: Value of the "run-suite-tag" in xml config file in 4 config dirs.
    # Same as value of "test-suite-tag" in AndroidTest.xml files.
    SUITE = 8


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

    def __init__(self):
        self.ref_type_to_func_map = {
            TestReferenceType.MODULE: self._get_test_info_by_module_name
        }

    def _get_test_reference_types(self, test_reference):
        """Determine type of test reference based on the content of string.

        Examples:
            The string 'SequentialRWTest' could be a reference to
            a Module or a Class name.

            The string 'cts/tests/filesystem' could be a Path, Integration
            or Suite reference.

        Args:
            test_reference: A string referencing a test.

        Returns:
            A list of possible TestReferenceTypes for test_reference string.
        """
        if test_reference.startswith('.'):
            return [TestReferenceType.FILE_PATH]
        if '/' in test_reference:
            return [TestReferenceType.FILE_PATH, TestReferenceType.INTEGRATION,
                    TestReferenceType.SUITE]
        if ':' in test_reference:
            if '.' in test_reference:
                return [TestReferenceType.MODULE_CLASS,
                        TestReferenceType.MODULE_PACKAGE]
            return [TestReferenceType.MODULE_CLASS]
        if '.'  in test_reference:
            return [TestReferenceType.CLASS, TestReferenceType.PACKAGE]
        # TODO(b/64484081): When we support CLASS references, return
        # [TestReferenceType.MODULE, TestReferenceType.CLASS] instead of
        # just [TestReferenceType.MODULE] below.
        return [TestReferenceType.MODULE]

    def _get_test_info_by_module_name(self, module_name):
        """Find test files given a module name.

        Args:
            module_name: A string of the test's module name.

        Returns:
            A populated TestInfo namedtuple if found, else None.
        """
        # TODO(b/64484081): When we support CLASS references, we will need
        # to use module-info.json to confirm here that this is indeed a module
        # and determine it's path.  For now we only support MODULE, so we can
        # assume we're given a valid module_name.
        return TestInfo(TestReferenceType.MODULE, module_name)

    def  _generate_build_targets(self, test_info):
        """Generate a list of build targets for a test.

        Args:
            test_info: A TestInfo namedtuple.

        Returns:
            A list of strings of the build targets.
        """
        targets = []
        if test_info.ref_type == TestReferenceType.MODULE:
            targets.append(test_info.module_name)
            targets.append('tradefed-all')
        return targets

    def _generate_run_command(self, test_info, filters=None, annotations=None):
        """Generate a list of run commands for a test.

        Args:
            test_info: A TestInfo namedtuple.
            filters: A set of filters.
            annotations: A set of annotations.

        Returns:
            A string of the TradeFederation run command.
        """
        if filters or annotations:
            logging.warn('Filters and Annotations are currently not supported.')
        return RUN_CMD % test_info.module_name

    def _get_test_info(self, test_name, reference_types):
        """Tries to find directory containing test files else returns None

        Args:
            test_name: A string referencing a test.
            reference_types: A list of the possible reference types.

        Returns:
            TestInfo namedtuple, else None if test files not found.
        """
        for ref_type in reference_types:
            try:
                test_info = self.ref_type_to_func_map[ref_type](test_name)
                if test_info:
                    return test_info
                logging.warn('Failed to find %s: %s', ref_type.name, test_name)
            except KeyError:
                supported = ', '.join(k.name for k in self.ref_type_to_func_map)
                logging.warn('"%s" as %s reference is unsupported. atest only '
                             'supports identifying a test by its: %s',
                             test_name, ref_type.name, supported)


    def translate(self, tests):
        """Translate atest command line into build targets and run commands.

        Args:
            tests: A list of strings referencing the tests to run.

        Returns:
            A tuple with list of build_target strings and list of run command
            strings.
        """
        logging.info('Finding tests: %s', tests)
        build_targets = []
        run_commands = []
        for test in tests:
            possible_reference_types = self._get_test_reference_types(test)
            test_info = self._get_test_info(test, possible_reference_types)
            if not test_info:
                raise NoTestFoundError('No test found for: %s' % test)
            logging.debug('Resolved input "%s" to: %s', test, test_info)
            build_targets.extend(self._generate_build_targets(test_info))
            run_commands.append(self._generate_run_command(test_info))
        return build_targets, run_commands
