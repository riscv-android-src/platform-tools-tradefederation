#!/usr/bin/env python
#
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

"""Unittests for module_finder."""

import re
import unittest
import os
import mock

# pylint: disable=import-error
import atest_error
import constants
import module_info
import unittest_constants as uc
import unittest_utils
from test_finders import module_finder
from test_finders import test_finder_utils
from test_finders import test_info
from test_runners import atest_tf_test_runner as atf_tr

MODULE_CLASS = '%s:%s' % (uc.MODULE_NAME, uc.CLASS_NAME)
MODULE_PACKAGE = '%s:%s' % (uc.MODULE_NAME, uc.PACKAGE)
FLAT_METHOD_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    uc.MODULE_BUILD_TARGETS,
    data={constants.TI_FILTER: frozenset([uc.FLAT_METHOD_FILTER]),
          constants.TI_REL_CONFIG: uc.CONFIG_FILE})
MODULE_CLASS_METHOD = '%s#%s' % (MODULE_CLASS, uc.METHOD_NAME)
CLASS_INFO_MODULE_2 = test_info.TestInfo(
    uc.MODULE2_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    uc.CLASS_BUILD_TARGETS,
    data={constants.TI_FILTER: frozenset([uc.CLASS_FILTER]),
          constants.TI_REL_CONFIG: uc.CONFIG2_FILE})

SEARCH_DIR_RE = re.compile(r'^find ([^ ]*).*$')


#pylint: disable=unused-argument
def classoutside_side_effect(find_cmd, shell=False):
    """Mock the check output of a find cmd where class outside module path."""
    search_dir = SEARCH_DIR_RE.match(find_cmd).group(1).strip()
    if search_dir == uc.ROOT:
        return uc.FIND_ONE
    return None


#pylint: disable=protected-access
class ModuleFinderUnittests(unittest.TestCase):
    """Unit tests for module_finder.py"""

    def setUp(self):
        """Set up stuff for testing."""
        self.mod_finder = module_finder.ModuleFinder()
        self.mod_finder.module_info = mock.Mock(spec=module_info.ModuleInfo)
        self.mod_finder.root_dir = uc.ROOT

    def test_is_vts_module(self):
        """Test _load_module_info_file regular operation."""
        mod_name = 'mod'
        is_vts_module_info = {'compatibility_suites': ['vts', 'tests']}
        self.mod_finder.module_info.get_module_info.return_value = is_vts_module_info
        self.assertTrue(self.mod_finder._is_vts_module(mod_name))

        is_not_vts_module = {'compatibility_suites': ['vts', 'cts']}
        self.mod_finder.module_info.get_module_info.return_value = is_not_vts_module
        self.assertFalse(self.mod_finder._is_vts_module(mod_name))

    def test_is_auto_gen_test_config(self):
        """Test _is_auto_gen_test_config correctly detects the module."""
        mod_name = 'mod'
        self.mod_finder.module_info.is_module.return_value = True
        is_auto_test_config = {'auto_test_config': [True]}
        is_not_auto_test_config = {'auto_test_config': [False]}
        is_not_auto_test_config_again = {'auto_test_config': []}

        self.mod_finder.module_info.get_module_info.return_value = is_auto_test_config
        self.assertTrue(self.mod_finder._is_auto_gen_test_config(mod_name))
        self.mod_finder.module_info.get_module_info.return_value = is_not_auto_test_config
        self.assertFalse(self.mod_finder._is_auto_gen_test_config(mod_name))
        self.mod_finder.module_info.get_module_info.return_value = is_not_auto_test_config_again
        self.assertFalse(self.mod_finder._is_auto_gen_test_config(mod_name))
        self.mod_finder.module_info.get_module_info.return_value = {}
        self.assertFalse(self.mod_finder._is_auto_gen_test_config(mod_name))

    # pylint: disable=unused-argument
    @mock.patch.object(module_finder.ModuleFinder, '_get_build_targets',
                       return_value=uc.MODULE_BUILD_TARGETS)
    def test_find_test_by_module_name(self, _get_targ):
        """Test find_test_by_module_name."""
        mod_info = {'installed': ['/path/to/install'],
                    'path': [uc.MODULE_DIR]}
        self.mod_finder.module_info.get_module_info.return_value = mod_info
        unittest_utils.assert_equal_testinfos(
            self,
            self.mod_finder.find_test_by_module_name(uc.MODULE_NAME),
            uc.MODULE_INFO)
        self.mod_finder.module_info.get_module_info.return_value = None
        self.assertIsNone(self.mod_finder.find_test_by_module_name('Not_Module'))

    @mock.patch.object(module_finder.ModuleFinder, '_is_vts_module',
                       return_value=False)
    @mock.patch.object(module_finder.ModuleFinder, '_get_build_targets')
    @mock.patch.object(module_finder.ModuleFinder, '_is_auto_gen_test_config',
                       return_value=False)
    @mock.patch('subprocess.check_output', return_value=uc.FIND_ONE)
    @mock.patch.object(test_finder_utils, 'get_fully_qualified_class_name',
                       return_value=uc.FULL_CLASS_NAME)
    @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
    @mock.patch('os.path.isdir', return_value=True)
    #pylint: disable=unused-argument
    def test_find_test_by_class_name(self, _isdir, _isfile, _fqcn,
                                     mock_checkoutput, _auto, mock_build, _vts):
        """Test find_test_by_class_name."""
        mock_build.return_value = uc.CLASS_BUILD_TARGETS
        self.mod_finder.module_info.get_module_name.return_value = uc.MODULE_NAME
        unittest_utils.assert_equal_testinfos(
            self, self.mod_finder.find_test_by_class_name(uc.CLASS_NAME), uc.CLASS_INFO)

        # with method
        mock_build.return_value = uc.MODULE_BUILD_TARGETS
        class_with_method = '%s#%s' % (uc.CLASS_NAME, uc.METHOD_NAME)
        unittest_utils.assert_equal_testinfos(
            self,
            self.mod_finder.find_test_by_class_name(class_with_method),
            uc.METHOD_INFO)
        mock_build.return_value = uc.MODULE_BUILD_TARGETS
        class_methods = '%s,%s' % (class_with_method, uc.METHOD2_NAME)
        unittest_utils.assert_equal_testinfos(
            self, self.mod_finder.find_test_by_class_name(class_methods),
            FLAT_METHOD_INFO)
        # module and rel_config passed in
        mock_build.return_value = uc.CLASS_BUILD_TARGETS
        unittest_utils.assert_equal_testinfos(
            self, self.mod_finder.find_test_by_class_name(
                uc.CLASS_NAME, uc.MODULE_NAME, uc.CONFIG_FILE), uc.CLASS_INFO)
        # find output fails to find class file
        mock_checkoutput.return_value = ''
        self.assertIsNone(self.mod_finder.find_test_by_class_name('Not class'))
        # class is outside given module path
        mock_checkoutput.side_effect = classoutside_side_effect
        unittest_utils.assert_equal_testinfos(
            self, self.mod_finder.find_test_by_class_name(uc.CLASS_NAME,
                                                          uc.MODULE2_NAME,
                                                          uc.CONFIG2_FILE),
            CLASS_INFO_MODULE_2)

    @mock.patch.object(module_finder.ModuleFinder, '_is_vts_module',
                       return_value=False)
    @mock.patch.object(module_finder.ModuleFinder, '_get_build_targets')
    @mock.patch.object(module_finder.ModuleFinder, '_is_auto_gen_test_config',
                       return_value=False)
    @mock.patch('subprocess.check_output', return_value=uc.FIND_ONE)
    @mock.patch.object(test_finder_utils, 'get_fully_qualified_class_name',
                       return_value=uc.FULL_CLASS_NAME)
    @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
    #pylint: disable=unused-argument
    def test_find_test_by_module_and_class(self, _isfile, _fqcn,
                                           mock_checkoutput, _auto, mock_build,
                                           _vts):
        """Test find_test_by_module_and_class."""
        mock_build.return_value = uc.CLASS_BUILD_TARGETS
        mod_info = {'installed': ['/path/to/install'],
                    'path': [uc.MODULE_DIR]}
        self.mod_finder.module_info.get_module_info.return_value = mod_info
        t_info = self.mod_finder.find_test_by_module_and_class(MODULE_CLASS)
        unittest_utils.assert_equal_testinfos(self, t_info, uc.CLASS_INFO)
        # with method
        mock_build.return_value = uc.MODULE_BUILD_TARGETS
        t_info = self.mod_finder.find_test_by_module_and_class(MODULE_CLASS_METHOD)
        unittest_utils.assert_equal_testinfos(self, t_info, uc.METHOD_INFO)
        # bad module, good class, returns None
        bad_module = '%s:%s' % ('BadMod', uc.CLASS_NAME)
        self.mod_finder.module_info.get_module_info.return_value = None
        self.assertIsNone(self.mod_finder.find_test_by_module_and_class(bad_module))
        # find output fails to find class file
        mock_checkoutput.return_value = ''
        bad_class = '%s:%s' % (uc.MODULE_NAME, 'Anything')
        self.mod_finder.module_info.get_module_info.return_value = mod_info
        self.assertIsNone(self.mod_finder.find_test_by_module_and_class(bad_class))

    @mock.patch.object(module_finder.ModuleFinder, '_is_vts_module',
                       return_value=False)
    @mock.patch.object(module_finder.ModuleFinder, '_get_build_targets')
    @mock.patch.object(module_finder.ModuleFinder, '_is_auto_gen_test_config',
                       return_value=False)
    @mock.patch('subprocess.check_output', return_value=uc.FIND_PKG)
    @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
    @mock.patch('os.path.isdir', return_value=True)
    #pylint: disable=unused-argument
    def test_find_test_by_package_name(self, _isdir, _isfile, mock_checkoutput,
                                       _auto, mock_build, _vts):
        """Test find_test_by_package_name."""
        mock_build.return_value = uc.CLASS_BUILD_TARGETS
        self.mod_finder.module_info.get_module_name.return_value = uc.MODULE_NAME
        unittest_utils.assert_equal_testinfos(
            self, self.mod_finder.find_test_by_package_name(uc.PACKAGE),
            uc.PACKAGE_INFO)
        # with method, should raise
        pkg_with_method = '%s#%s' % (uc.PACKAGE, uc.METHOD_NAME)
        self.assertRaises(atest_error.MethodWithoutClassError,
                          self.mod_finder.find_test_by_package_name,
                          pkg_with_method)
        # module and rel_config passed in
        unittest_utils.assert_equal_testinfos(
            self, self.mod_finder.find_test_by_package_name(
                uc.PACKAGE, uc.MODULE_NAME, uc.CONFIG_FILE), uc.PACKAGE_INFO)
        # find output fails to find class file
        mock_checkoutput.return_value = ''
        self.assertIsNone(self.mod_finder.find_test_by_package_name('Not pkg'))

    @mock.patch.object(module_finder.ModuleFinder, '_is_vts_module',
                       return_value=False)
    @mock.patch.object(module_finder.ModuleFinder, '_get_build_targets')
    @mock.patch.object(module_finder.ModuleFinder, '_is_auto_gen_test_config',
                       return_value=False)
    @mock.patch('subprocess.check_output', return_value=uc.FIND_PKG)
    @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
    #pylint: disable=unused-argument
    def test_find_test_by_module_and_package(self, _isfile, mock_checkoutput,
                                             _auto, mock_build, _vts):
        """Test find_test_by_module_and_package."""
        mock_build.return_value = uc.CLASS_BUILD_TARGETS
        mod_info = {'installed': ['/path/to/install'],
                    'path': [uc.MODULE_DIR]}
        self.mod_finder.module_info.get_module_info.return_value = mod_info
        t_info = self.mod_finder.find_test_by_module_and_package(MODULE_PACKAGE)
        unittest_utils.assert_equal_testinfos(self, t_info, uc.PACKAGE_INFO)
        # with method, raises
        module_pkg_with_method = '%s:%s#%s' % (uc.MODULE2_NAME, uc.PACKAGE,
                                               uc.METHOD_NAME)
        self.assertRaises(atest_error.MethodWithoutClassError,
                          self.mod_finder.find_test_by_module_and_package,
                          module_pkg_with_method)
        # bad module, good pkg, returns None
        bad_module = '%s:%s' % ('BadMod', uc.PACKAGE)
        self.mod_finder.module_info.get_module_info.return_value = None
        self.assertIsNone(self.mod_finder.find_test_by_module_and_package(bad_module))
        # find output fails to find package path
        mock_checkoutput.return_value = ''
        bad_pkg = '%s:%s' % (uc.MODULE_NAME, 'Anything')
        self.mod_finder.module_info.get_module_info.return_value = mod_info
        self.assertIsNone(self.mod_finder.find_test_by_module_and_package(bad_pkg))

    @mock.patch.object(module_finder.ModuleFinder, '_get_build_targets')
    @mock.patch.object(module_finder.ModuleFinder, '_is_vts_module',
                       return_value=False)
    @mock.patch.object(module_finder.ModuleFinder, '_is_auto_gen_test_config')
    @mock.patch.object(test_finder_utils, 'get_fully_qualified_class_name',
                       return_value=uc.FULL_CLASS_NAME)
    @mock.patch('os.path.realpath',
                side_effect=unittest_utils.realpath_side_effect)
    @mock.patch('os.path.isfile', side_effect=unittest_utils.isfile_side_effect)
    @mock.patch.object(test_finder_utils, 'find_parent_module_dir')
    @mock.patch('os.path.exists')
    #pylint: disable=unused-argument
    def test_find_test_by_path(self, mock_pathexists, mock_dir, _isfile, _real,
                               _fqcn, mock_is_auto_gen, _vts, mock_build):
        """Test find_test_by_path."""
        mock_build.return_value = set()
        # Check that we don't return anything with invalid test references.
        mock_pathexists.return_value = False
        unittest_utils.assert_equal_testinfos(
            self, None, self.mod_finder.find_test_by_path('bad/path'))
        mock_pathexists.return_value = True
        mock_dir.return_value = None
        unittest_utils.assert_equal_testinfos(
            self, None, self.mod_finder.find_test_by_path('no/module'))

        # Check that we handle auto gen configs.
        mock_build.return_value = uc.MODULE_BUILD_TARGETS
        self.mod_finder.module_info.get_module_name.return_value = uc.MODULE_NAME
        mock_dir.side_effect = [atest_error.TestWithNoModuleError(),
                                uc.MODULE_DIR]
        mock_is_auto_gen.return_value = True
        unittest_utils.assert_equal_testinfos(
            self, uc.MODULE_INFO, self.mod_finder.find_test_by_path('no/test/config'))
        mock_dir.side_effect = None


        # Happy path testing.
        mock_is_auto_gen.return_value = False
        mock_dir.return_value = uc.MODULE_DIR
        class_path = '%s.java' % uc.CLASS_NAME
        mock_build.return_value = uc.CLASS_BUILD_TARGETS
        unittest_utils.assert_equal_testinfos(
            self, uc.CLASS_INFO, self.mod_finder.find_test_by_path(class_path))

        class_with_method = '%s#%s' % (class_path, uc.METHOD_NAME)
        mock_build.return_value = uc.MODULE_BUILD_TARGETS
        unittest_utils.assert_equal_testinfos(
            self, self.mod_finder.find_test_by_path(class_with_method), uc.METHOD_INFO)

        class_with_methods = '%s,%s' % (class_with_method, uc.METHOD2_NAME)
        mock_build.return_value = uc.MODULE_BUILD_TARGETS
        unittest_utils.assert_equal_testinfos(
            self, self.mod_finder.find_test_by_path(class_with_methods),
            FLAT_METHOD_INFO)


    @mock.patch.object(module_finder.ModuleFinder, '_get_build_targets',
                       return_value=uc.MODULE_BUILD_TARGETS)
    @mock.patch.object(module_finder.ModuleFinder, '_is_vts_module',
                       return_value=False)
    @mock.patch.object(module_finder.ModuleFinder, '_is_auto_gen_test_config',
                       return_value=False)
    #pylint: disable=unused-argument
    def test_find_test_by_path_part_2(self, _is_auto_gen, _is_vts, _get_build):
        """Test find_test_by_path for directories."""
        # Dir with java files in it, should run as package
        class_dir = os.path.join(uc.TEST_DATA_DIR, 'path_testing')
        self.mod_finder.module_info.get_module_name.return_value = uc.MODULE_NAME
        unittest_utils.assert_equal_testinfos(
            self, uc.PATH_INFO, self.mod_finder.find_test_by_path(class_dir))
        # Dir with no java files in it, should run whole module
        empty_dir = os.path.join(uc.TEST_DATA_DIR, 'path_testing_empty')
        unittest_utils.assert_equal_testinfos(
            self, uc.EMPTY_PATH_INFO,
            self.mod_finder.find_test_by_path(empty_dir))

if __name__ == '__main__':
    unittest.main()
