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
import logging
import enum


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
            TestReferenceType.MODULE: self._find_by_module_name
        }

    #pylint: disable=no-self-use
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
        raise NotImplementedError()

    #pylint: disable=no-self-use
    def _find_by_module_name(self, module_name):
        """Find test files given a module name.

        Args:
            module_name: A string of the test's module name.

        Returns:
            A string of the path to the test dir.
        """
        raise NotImplementedError()

    #pylint: disable=no-self-use
    def  _generate_build_targets(self, test_dir, reference_type):
        """Generate a list of build targets for a test.

        Args:
            test_dir: A string of the path to test dir.
            reference_type: A TestReferenceType of the test.

        Returns:
            A list of strings of the build targets.
        """
        raise NotImplementedError()

    #pylint: disable=no-self-use
    def _generate_run_command(self, test_name, filters=None, annotations=None):
        """Generate a list of run commands for a test.

        Args:
            test_name: A string of the test's name.
            filters: A set of filters.
            annotations: A set of annotations.

        Returns:
            A string of the TradeFederation run command.
        """
        raise NotImplementedError()

    #pylint: disable=no-self-use
    def translate(self, tests):
        """Translate atest command line into build targets and run commands.

        Args:
            tests: A list of strings referencing the tests to run.

        Returns:
            A tuple with list of build_target strings and list of run command
            strings.
        """
        logging.info('Translating: %s', tests)
        # TODO(b/64148562): Implement translator logic for a Module test.
        raise NotImplementedError()
