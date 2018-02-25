# Copyright 2018, The Android Open Source Project
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
Module Finder class.
"""

import logging
import os
import re

# pylint: disable=import-error
import atest_error
import constants
import test_info
import test_finder_base
import test_finder_utils
from test_runners import atest_tf_test_runner
from test_runners import vts_tf_test_runner

# Parse package name from the package declaration line of a java file.
# Group matches "foo.bar" of line "package foo.bar;"
_PACKAGE_RE = re.compile(r'\s*package\s+(?P<package>[^;]+)\s*;\s*', re.I)

_MODULES_IN = 'MODULES-IN-%s'
_ANDROID_MK = 'Android.mk'

# These are suites in LOCAL_COMPATIBILITY_SUITE that aren't really suites so
# we can ignore them.
_SUITES_TO_IGNORE = frozenset({'general-tests', 'device-tests', 'tests'})


class ModuleFinder(test_finder_base.TestFinderBase):
    """Module finder class."""
    NAME = 'MODULE'
    _TEST_RUNNER = atest_tf_test_runner.AtestTradefedTestRunner.NAME
    _VTS_TEST_RUNNER = vts_tf_test_runner.VtsTradefedTestRunner.NAME

    def __init__(self, module_info=None):
        super(ModuleFinder, self).__init__()
        self.root_dir = os.environ.get(constants.ANDROID_BUILD_TOP)
        self.module_info = module_info

    def _is_vts_module(self, module_name):
        """Returns True if the module is a vts module, else False."""
        mod_info = self.module_info.get_module_info(module_name)
        suites = mod_info.get('compatibility_suites', [])
        # Pull out all *ts (cts, tvts, etc) suites.
        suites = [suite for suite in suites if suite not in _SUITES_TO_IGNORE]
        return len(suites) == 1 and 'vts' in suites

    def _update_to_vts_test_info(self, test):
        """Fill in the fields with vts specific info.

        We need to update the runner to use the vts runner and also find the
        test specific depedencies

        Args:
            test: TestInfo to update with vts specific details.

        Return:
            TestInfo that is ready for the vts test runner.
        """
        test.test_runner = self._VTS_TEST_RUNNER
        config_file = os.path.join(self.root_dir,
                                   test.data[constants.TI_REL_CONFIG])
        # Need to get out dir (special logic is to account for custom out dirs).
        # The out dir is used to construct the build targets for the test deps.
        out_dir = os.environ.get(constants.ANDROID_HOST_OUT)
        custom_out_dir = os.environ.get(constants.ANDROID_OUT_DIR)
        # If we're not an absolute custom out dir, get relative out dir path.
        if custom_out_dir is None or not os.path.isabs(custom_out_dir):
            out_dir = os.path.relpath(out_dir, self.root_dir)
        vts_out_dir = os.path.join(out_dir, 'vts', 'android-vts', 'testcases')

        # Add in vts test build targets.
        test.build_targets = test_finder_utils.get_targets_from_vts_xml(
            config_file, vts_out_dir, self.module_info)
        test.build_targets.add('vts-test-core')
        return test

    def _process_test_info(self, test):
        """Process the test info and return some fields updated/changed.

        We need to check if the test found is a special module (like vts) and
        update the test_info fields (like test_runner) appropriately.

        Args:
            test: TestInfo that has been filled out by a find method.

        Return:
            TestInfo that has been modified as needed.
        """
        # Check if this is only a vts module.
        if self._is_vts_module(test.test_name):
            return self._update_to_vts_test_info(test)
        return test

    def _is_auto_gen_test_config(self, module_name):
        """Check if the test config file will be generated automatically.

        Args:
            module_name: A string of the module name.

        Returns:
            True if the test config file will be generated automatically.
        """
        if self.module_info.is_module(module_name):
            mod_info = self.module_info.get_module_info(module_name)
            auto_test_config = mod_info.get('auto_test_config', [])
            return auto_test_config and auto_test_config[0]
        return False

    def _get_build_targets(self, module_name, rel_config):
        """Get the test deps.

        Args:
            module_name: name of the test.
            rel_config: XML for the given test.

        Returns:
            Set of build targets.
        """
        targets = set()
        if not self._is_auto_gen_test_config(module_name):
            config_file = os.path.join(self.root_dir, rel_config)
            targets = test_finder_utils.get_targets_from_xml(config_file,
                                                             self.module_info)
        mod_dir = os.path.dirname(rel_config).replace('/', '-')
        targets.add(_MODULES_IN % mod_dir)
        return targets

    def find_test_by_module_name(self, module_name):
        """Find test for the given module name.

        Args:
            module_name: A string of the test's module name.

        Returns:
            A populated TestInfo namedtuple if found, else None.
        """
        info = self.module_info.get_module_info(module_name)
        if info and info.get('installed'):
            # path is a list with only 1 element.
            rel_config = os.path.join(info['path'][0], constants.MODULE_CONFIG)
            return self._process_test_info(test_info.TestInfo(
                test_name=module_name,
                test_runner=self._TEST_RUNNER,
                build_targets=self._get_build_targets(module_name, rel_config),
                data={constants.TI_REL_CONFIG: rel_config,
                      constants.TI_FILTER: frozenset()}))
        return None

    def find_test_by_class_name(self, class_name, module_name=None,
                                rel_config=None):
        """Find test files given a class name.

        If module_name and rel_config not given it will calculate it determine
        it by looking up the tree from the class file.

        Args:
            class_name: A string of the test's class name.
            module_name: Optional. A string of the module name to use.
            rel_config: Optional. A string of module dir relative to repo root.

        Returns:
            A populated TestInfo namedtuple if test found, else None.
        """
        class_name, methods = test_finder_utils.split_methods(class_name)
        if rel_config:
            search_dir = os.path.join(self.root_dir,
                                      os.path.dirname(rel_config))
        else:
            search_dir = self.root_dir
        test_path = test_finder_utils.find_class_file(search_dir, class_name)
        if not test_path and rel_config:
            logging.info('Did not find class (%s) under module path (%s), '
                         'researching from repo root.', class_name, rel_config)
            test_path = test_finder_utils.find_class_file(self.root_dir,
                                                          class_name)
        if not test_path:
            return None
        full_class_name = test_finder_utils.get_fully_qualified_class_name(
            test_path)
        test_filter = frozenset([test_info.TestFilter(full_class_name,
                                                      methods)])
        if not rel_config:
            test_dir = os.path.dirname(test_path)
            rel_module_dir = test_finder_utils.find_parent_module_dir(
                self.root_dir, test_dir)
            rel_config = os.path.join(rel_module_dir, constants.MODULE_CONFIG)
        if not module_name:
            module_name = self.module_info.get_module_name(os.path.dirname(
                rel_config))
        return self._process_test_info(test_info.TestInfo(
            test_name=module_name,
            test_runner=self._TEST_RUNNER,
            build_targets=self._get_build_targets(module_name, rel_config),
            data={constants.TI_FILTER: test_filter,
                  constants.TI_REL_CONFIG: rel_config}))

    def find_test_by_module_and_class(self, module_class):
        """Find the test info given a MODULE:CLASS string.

        Args:
            module_class: A string of form MODULE:CLASS or MODULE:CLASS#METHOD.

        Returns:
            A populated TestInfo namedtuple if found, else None.
        """
        if ':' not in module_class:
            return None
        module_name, class_name = module_class.split(':')
        module_info = self.find_test_by_module_name(module_name)
        if not module_info:
            return None
        return self.find_test_by_class_name(
            class_name, module_info.test_name,
            module_info.data.get(constants.TI_REL_CONFIG))

    def find_test_by_package_name(self, package, module_name=None,
                                  rel_config=None):
        """Find the test info given a PACKAGE string.

        Args:
            package: A string of the package name.
            module_name: Optional. A string of the module name.
            ref_config: Optional. A string of rel path of config.

        Returns:
            A populated TestInfo namedtuple if found, else None.
        """
        _, methods = test_finder_utils.split_methods(package)
        if methods:
            raise atest_error.MethodWithoutClassError('Method filtering '
                                                      'requires class')
        # Confirm that packages exists and get user input for multiples.
        if rel_config:
            search_dir = os.path.join(self.root_dir,
                                      os.path.dirname(rel_config))
        else:
            search_dir = self.root_dir
        package_path = test_finder_utils.run_find_cmd(
            test_finder_utils.FIND_REFERENCE_TYPE.PACKAGE, search_dir,
            package.replace('.', '/'))
        # package path will be the full path to the dir represented by package
        if not package_path:
            return None
        test_filter = frozenset([test_info.TestFilter(package, frozenset())])
        if not rel_config:
            rel_module_dir = test_finder_utils.find_parent_module_dir(
                self.root_dir, package_path)
            rel_config = os.path.join(rel_module_dir, constants.MODULE_CONFIG)
        if not module_name:
            module_name = self.module_info.get_module_name(
                os.path.dirname(rel_config))
        return self._process_test_info(test_info.TestInfo(
            test_name=module_name,
            test_runner=self._TEST_RUNNER,
            build_targets=self._get_build_targets(module_name, rel_config),
            data={constants.TI_FILTER: test_filter,
                  constants.TI_REL_CONFIG: rel_config}))

    def find_test_by_module_and_package(self, module_package):
        """Find the test info given a MODULE:PACKAGE string.

        Args:
            module_package: A string of form MODULE:PACKAGE

        Returns:
            A populated TestInfo namedtuple if found, else None.
        """
        module_name, package = module_package.split(':')
        module_info = self.find_test_by_module_name(module_name)
        if not module_info:
            return None
        return self.find_test_by_package_name(
            package, module_info.test_name,
            module_info.data.get(constants.TI_REL_CONFIG))

    def find_test_by_path(self, path):
        """Find the first test info matching the given path.

        Strategy:
            path_to_java_file --> Resolve to CLASS
            path_to_module_dir -> Resolve to MODULE
            path_to_class_dir --> Resolve to MODULE (TODO: Maybe all classes)
            path_to_random_dir --> try to resolve to MODULE

        Args:
            path: A string of the test's path.

        Returns:
            A populated TestInfo namedtuple if test found, else None
        """
        path, methods = test_finder_utils.split_methods(path)
        # TODO: See if this can be generalized and shared with methods above
        # create absolute path from cwd and remove symbolic links
        path = os.path.realpath(path)
        if not os.path.exists(path):
            return None
        dir_path, file_name = test_finder_utils.get_dir_path_and_filename(path)

        # Module/Class
        try:
            rel_module_dir = test_finder_utils.find_parent_module_dir(
                self.root_dir, dir_path)
            if not rel_module_dir:
                return None
        except atest_error.TestWithNoModuleError:
            # If TF test config will be auto generated, _find_parent_module_dir
            # fails with TestWithNoModuleError as there is no AndroidTest.xml.
            # In that case, try again to locate parent module directory by
            # searching for Android.mk. If that works and the module does have
            # test config auto generated, create TestInfo based on the located
            # module.
            rel_module_dir = test_finder_utils.find_parent_module_dir(
                self.root_dir, dir_path, _ANDROID_MK)
            module_name = self.module_info.get_module_name(rel_module_dir)
            if not self._is_auto_gen_test_config(module_name):
                raise
        module_name = self.module_info.get_module_name(rel_module_dir)
        rel_config = os.path.join(rel_module_dir, constants.MODULE_CONFIG)
        data = {constants.TI_REL_CONFIG: rel_config,
                constants.TI_FILTER: frozenset()}
        if file_name and file_name.endswith('.java'):
            full_class_name = test_finder_utils.get_fully_qualified_class_name(
                path)
            data[constants.TI_FILTER] = frozenset(
                [test_info.TestFilter(full_class_name, methods)])
        return self._process_test_info(test_info.TestInfo(
            test_name=module_name,
            test_runner=self._TEST_RUNNER,
            build_targets=self._get_build_targets(module_name, rel_config),
            data=data))
