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

import enum
import json
import logging
import os
import subprocess
import time
from collections import namedtuple


RUN_CMD = ('atest_tradefed.sh run commandAndExit template/local_min '
           '--template:map test=%s')
TestInfo = namedtuple('TestInfo', ['ref_type', 'module_name', 'rel_module_dir'])
MAX_TEST_CHOICES_FOR_USER_INPUT = 5
MODULES_IN = 'MODULES-IN-%s'
# Unix find commands for searching for test files based on test type input.
FIND_CMDS = {
    'class': r'find %s -type d -name .git -prune -o -type f '
             r'-name %s.java -print',
    'qualified_class': r'find %s -type d -name .git -prune -o -wholename '
                       r'*%s.java -print'
}


class TooManyTestsFoundError(Exception):
    """Raised when unix find command finds too many tests."""

class NoTestFoundError(Exception):
    """Raised when no tests are found."""

class TestWithNoModuleError(Exception):
    """Raised when test files have no parent module directory."""

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

    def __init__(self, root_dir='/'):
        if not os.path.isdir(root_dir):
            raise ValueError('%s is not valid dir.' % root_dir)
        self.root_dir = os.path.realpath(root_dir)
        self.out_dir = os.environ.get('OUT')
        self.ref_type_to_func_map = {
            TestReferenceType.MODULE: self._find_test_by_module_name,
            TestReferenceType.CLASS: self._find_test_by_class_name,
        }
        self.module_info = self._load_module_info()

    def _load_module_info(self):
        """Make (if not exists) and load into memory module-info.json file

        Returns:
             A dict of data about module names and dir locations.
        """
        file_path = os.path.join(self.out_dir, 'module-info.json')
        # Make target is simply file path relative to root.
        make_target = os.path.relpath(file_path, self.root_dir)
        if not os.path.isfile(file_path):
            logging.info('Generating module-info json - this is required for '
                         'initial runs.')
            cmd = ['make', '-j', '-C', self.root_dir, make_target]
            logging.debug('Executing: %s', cmd)
            subprocess.check_output(cmd, stderr=subprocess.STDOUT)
        with open(file_path) as json_file:
            return json.load(json_file)

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
        return [TestReferenceType.MODULE, TestReferenceType.CLASS]

    def _is_sub_dir(self, sub_dir, parent_dir):
        """Return True if both are dirs and sub_dir is sub of parent_dir.

        Args:
          sub_dir: A string of the sub directory path.
          parent_dir: A string of the parent directory path.

        Returns:
            A boolean of whether both are dirs and sub_dir is sub of parent_dir.
        """
        # avoid symlink issues with real path
        parent_dir = os.path.realpath(parent_dir)
        sub_dir = os.path.realpath(sub_dir)
        if not os.path.isdir(sub_dir) or not os.path.isdir(parent_dir):
            return False
        return (os.path.commonprefix([sub_dir, parent_dir]) == parent_dir and
                sub_dir != parent_dir)

    def _find_parent_module_dir(self, start_dir):
        """From current dir search up file tree until root dir for module dir.

        Args:
          start_dir: A string of the dir to start searching up from.

        Returns:
            A string of the module dir relative to root.

        Exceptions:
            ValueError: Raised if cur_dir not dir or not subdir of root dir.
            TestWithNoModuleError: Raised if no Module Dir found.
        """
        if not self._is_sub_dir(start_dir, self.root_dir):
            raise ValueError('%s not subdir of %s' % (start_dir, self.root_dir))
        current_dir = start_dir
        while current_dir != self.root_dir:
            if os.path.isfile(os.path.join(current_dir, 'AndroidTest.xml')):
                return os.path.relpath(current_dir, self.root_dir)
            current_dir = os.path.dirname(current_dir)
        raise TestWithNoModuleError('No Parent Module Dir for: %s' % start_dir)

    def _extract_test_dir(self, output):
        """Extract the test dir from the output of a unix 'find' command.

        Example of find output for CLASS find cmd:
        /<some_root>/cts/tests/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java

        Args:
            output: A string output of a unix 'find' command.

        Returns:
            A string of the test dir path.

        Exceptions:
            TooManyTestsFoundError.
        """
        tests = output.strip('\n').split('\n')
        count = len(tests)
        if count > MAX_TEST_CHOICES_FOR_USER_INPUT:
            raise TooManyTestsFoundError('%s tests:\n%s' % (count, output))
        if count == 1:
            test_index = 0
        else:
            numbered_list = ['%s: %s' % (i, t) for i, t in enumerate(tests)]
            print 'Multiple tests found:\n%s' % '\n'.join(numbered_list)
            test_index = int(raw_input("Please enter number of test to use:"))
        return os.path.dirname(tests[test_index])

    def _get_module_name(self, rel_module_path):
        """Get the name of a module given its dir relative to repo root.

        Example of module_info.json line:

        'AmSlam':
        {
        'class': ['APPS'],
        'path': ['frameworks/base/tests/AmSlam'],
        'tags': ['tests'],
        'installed': ['out/target/product/bullhead/data/app/AmSlam/AmSlam.apk']
        }

        Args:
            rel_module_path: A string of module's dir relative to repo root.

        Returns:
            A string of the module name, else None if not found.
        """
        for name, info in self.module_info.iteritems():
            if rel_module_path == info.get('path', [])[0]:
                return name

    def _find_test_by_module_name(self, module_name):
        """Find test files given a module name.

        Args:
            module_name: A string of the test's module name.

        Returns:
            A populated TestInfo namedtuple if found, else None.
        """
        info = self.module_info.get(module_name)
        if info:
            # path is a list with only 1 element.
            return TestInfo(TestReferenceType.MODULE, module_name,
                            info['path'][0])

    def _find_test_by_class_name(self, class_name):
        """Find test files given a class name.

        Args:
            class_name: A string of the test's class name.

        Returns:
            A populated TestInfo namedtuple if test found, else None.
        """
        if '.' in class_name:
            path = class_name.replace('.', '/')
            find_cmd = FIND_CMDS['qualified_class'] % (self.root_dir, path)
        else:
            find_cmd = FIND_CMDS['class'] % (self.root_dir, class_name)
        try:
            start = time.time()
            logging.debug('Executing: %s', find_cmd)
            out = subprocess.check_output(find_cmd, shell=True)
            end = time.time()
            logging.debug('Find completed in %ss', end - start)
            logging.debug('Find Cmd Out: %s', out)
            test_dir = self._extract_test_dir(out)
            if not test_dir:
                return None
            rel_module_dir = self._find_parent_module_dir(test_dir)
            if not rel_module_dir:
                return None
            module_name = self._get_module_name(rel_module_dir)
            if not module_name:
                return None
            return TestInfo(TestReferenceType.CLASS, module_name,
                            rel_module_dir)
        except subprocess.CalledProcessError:
            logging.info('Class (%s) not in %s', class_name, self.root_dir)

    def  _generate_build_targets(self, test_info):
        """Generate a list of build targets for a test.

        Args:
            test_info: A TestInfo namedtuple.

        Returns:
            A list of strings of the build targets.
        """
        if test_info.ref_type in (TestReferenceType.MODULE,
                                  TestReferenceType.CLASS):
            return ['tradefed-all',
                    MODULES_IN % test_info.rel_module_dir.replace('/', '-')]

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
                    logging.info('Found %s for: %s', ref_type.name, test_name)
                    logging.debug('Resolved "%s" to %s', test_name, test_info)
                    return test_info
                logging.debug('Failed to find %s as %s', test_name,
                              ref_type.name)
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
            A tuple with set of build_target strings and list of run command
            strings.
        """
        logging.info('Finding tests: %s', tests)
        start = time.time()
        build_targets = set()
        # TODO: Should we make this also a set to dedupe run cmds? What would a
        # user expect if they listed the same test twice?
        run_commands = []
        for test in tests:
            possible_reference_types = self._get_test_reference_types(test)
            test_info = self._get_test_info(test, possible_reference_types)
            if not test_info:
                raise NoTestFoundError('No test found for: %s' % test)
            build_targets.update(self._generate_build_targets(test_info))
            run_commands.append(self._generate_run_command(test_info))
        end = time.time()
        logging.info('Found tests in %ss', end - start)
        return build_targets, run_commands
