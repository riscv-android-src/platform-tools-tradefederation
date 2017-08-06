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

"""Unittests for atest."""

import unittest
import mock

import atest

class AtestUnittests(unittest.TestCase):
    """Unit tests for atest.py"""

    @mock.patch('os.environ.get', return_value=None)
    def test_has_environment_variables_uninitialized(self, _):
        """Test _has_environment_variables when no env vars."""
        #pylint: disable=protected-access
        self.assertFalse(atest._has_environment_variables())


    @mock.patch('os.environ.get', return_value='out/testcases/')
    def test_has_environment_variables_initialized(self, _):
        """Test _has_environment_variables when env vars."""
        #pylint: disable=protected-access
        self.assertTrue(atest._has_environment_variables())


    def test_parse_args(self):
        """Test _parse_args parses command line args."""
        args = ['test_name_one', 'test_name_two']
        #pylint: disable=protected-access
        arg1, arg2 = atest._parse_args(args).tests
        self.assertTrue(arg1, 'test_name_one')
        self.assertTrue(arg2, 'test_name_two')

if __name__ == '__main__':
    unittest.main()
