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

"""Unittests for test_finder_utils."""

import os
import unittest
import mock

# pylint: disable=import-error
import atest_error
import constants
import module_info
import unittest_constants as uc
import unittest_utils
from test_finders import test_finder_utils

CLASS_DIR = 'foo/bar/jank/src/android/jank/cts/ui'
FIND_TWO = uc.ROOT + 'other/dir/test.java\n' + uc.FIND_ONE
XML_TARGETS = {'CtsJankDeviceTestCases', 'perf-setup.sh', 'cts-tradefed'}


#pylint: disable=protected-access
class TestFinderUtilsUnittests(unittest.TestCase):
    """Unit tests for test_finder_utils.py"""

    def test_split_methods(self):
        """Test _split_methods method."""
        # Class
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.split_methods('Class.Name'),
            ('Class.Name', set()))
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.split_methods('Class.Name#Method'),
            ('Class.Name', {'Method'}))
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.split_methods('Class.Name#Method,Method2'),
            ('Class.Name', {'Method', 'Method2'}))
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.split_methods('Class.Name#Method,Method2'),
            ('Class.Name', {'Method', 'Method2'}))
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.split_methods('Class.Name#Method,Method2'),
            ('Class.Name', {'Method', 'Method2'}))
        self.assertRaises(
            atest_error.TooManyMethodsError, test_finder_utils.split_methods,
            'class.name#Method,class.name.2#method')
        # Path
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.split_methods('foo/bar/class.java'),
            ('foo/bar/class.java', set()))
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.split_methods('foo/bar/class.java#Method'),
            ('foo/bar/class.java', {'Method'}))

    @mock.patch('__builtin__.raw_input', return_value='1')
    def test_extract_test_path(self, _):
        """Test extract_test_dir method."""
        path = os.path.join(uc.ROOT, CLASS_DIR, uc.CLASS_NAME + '.java')
        unittest_utils.assert_strict_equal(
            self, test_finder_utils.extract_test_path(uc.FIND_ONE), path)
        path = os.path.join(uc.ROOT, CLASS_DIR, uc.CLASS_NAME + '.java')
        unittest_utils.assert_strict_equal(
            self, test_finder_utils.extract_test_path(FIND_TWO), path)

    @mock.patch('os.path.isdir')
    def test_is_equal_or_sub_dir(self, mock_isdir):
        """Test is_equal_or_sub_dir method."""
        self.assertTrue(test_finder_utils.is_equal_or_sub_dir('/a/b/c', '/'))
        self.assertTrue(test_finder_utils.is_equal_or_sub_dir('/a/b/c', '/a'))
        self.assertTrue(test_finder_utils.is_equal_or_sub_dir('/a/b/c',
                                                              '/a/b/c'))
        self.assertFalse(test_finder_utils.is_equal_or_sub_dir('/a/b',
                                                               '/a/b/c'))
        self.assertFalse(test_finder_utils.is_equal_or_sub_dir('/a', '/f'))
        mock_isdir.return_value = False
        self.assertFalse(test_finder_utils.is_equal_or_sub_dir('/a/b', '/a'))

    @mock.patch('os.path.isdir', return_value=True)
    @mock.patch('os.path.isfile',
                side_effect=unittest_utils.isfile_side_effect)
    def test_find_parent_module_dir(self, _isfile, _isdir):
        """Test _find_parent_module_dir method."""
        abs_class_dir = '/%s' % CLASS_DIR
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.find_parent_module_dir(uc.ROOT, abs_class_dir),
            uc.MODULE_DIR)

    def test_get_targets_from_xml(self):
        """Test get_targets_from_xml method."""
        # Mocking Etree is near impossible, so use a real file, but mocking
        # ModuleInfo is still fine. Just have it return False when it finds a
        # module that states it's not a module.
        mock_module_info = mock.Mock(spec=module_info.ModuleInfo)
        mock_module_info.is_module.side_effect = lambda module: (
            not module == 'is_not_module')
        xml_file = os.path.join(
            os.path.dirname(os.path.realpath(__file__)), '..', uc.TEST_DATA_DIR,
            constants.MODULE_CONFIG)
        unittest_utils.assert_strict_equal(
            self,
            test_finder_utils.get_targets_from_xml(xml_file, mock_module_info),
            XML_TARGETS)


if __name__ == '__main__':
    unittest.main()
