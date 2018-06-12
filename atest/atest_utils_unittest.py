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

"""Unittests for atest_utils."""

import unittest
import mock

import atest_utils


#pylint: disable=protected-access
class AtestUtilsUnittests(unittest.TestCase):
    """Unit tests for atest_utils.py"""

    def test_capture_fail_section_has_fail_section(self):
        """Test capture_fail_section when has fail section."""
        test_list = ['AAAAAA', 'FAILED: Error1', '^\n', 'Error2\n',
                     '[  6% 191/2997] BBBBBB\n', 'CCCCC',
                     '[  20% 322/2997] DDDDDD\n', 'EEEEE']
        want_list = ['FAILED: Error1', '^\n', 'Error2\n']
        self.assertEqual(want_list,
                         atest_utils._capture_fail_section(test_list))

    def test_capture_fail_section_no_fail_section(self):
        """Test capture_fail_section when no fail section."""
        test_list = ['[ 6% 191/2997] XXXXX', 'YYYYY: ZZZZZ']
        want_list = []
        self.assertEqual(want_list,
                         atest_utils._capture_fail_section(test_list))

    def test_is_test_mapping(self):
        """Test method is_test_mapping."""
        tm_option_attributes = [
            'test_mapping',
            'include_subdirs'
        ]
        for attr_to_test in tm_option_attributes:
            args = mock.Mock()
            for attr in tm_option_attributes:
                setattr(args, attr, attr == attr_to_test)
            args.tests = []
            self.assertTrue(
                atest_utils.is_test_mapping(args),
                'Failed to validate option %s' % attr_to_test)

        args = mock.Mock()
        for attr in tm_option_attributes:
            setattr(args, attr, False)
        args.tests = [':group_name']
        self.assertTrue(atest_utils.is_test_mapping(args))

        args = mock.Mock()
        for attr in tm_option_attributes:
            setattr(args, attr, False)
        args.tests = [':test1', 'test2']
        self.assertFalse(atest_utils.is_test_mapping(args))

        args = mock.Mock()
        for attr in tm_option_attributes:
            setattr(args, attr, False)
        args.tests = ['test2']
        self.assertFalse(atest_utils.is_test_mapping(args))


if __name__ == "__main__":
    unittest.main()
