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
"""Unittests for test_suite_finder."""

import unittest

# pylint: disable=import-error
import unittest_utils
from test_finders import test_info
from test_finders import test_suite_finder
from test_runners import test_suite_test_runner


# pylint: disable=protected-access
class TestSuiteFinderUnittests(unittest.TestCase):
    """Unit tests for test_suite_finder.py"""

    def setUp(self):
        """Set up stuff for testing."""
        self.test_suite_finder = test_suite_finder.TestSuiteFinder()

    def test_find_test_by_suite_name(self):
        """Test find_test_by_suite_name.
        Strategy:
            suite_name: cts --> test_info: test_name=cts,
                                           test_runner=TestSuiteTestRunner,
                                           build_target=set(['cts'])
            suite_name: CTS --> test_info: None
        """
        suite_name = 'cts'
        t_info = self.test_suite_finder.find_test_by_suite_name(suite_name)
        want_info = test_info.TestInfo(suite_name,
                                       test_suite_test_runner.TestSuiteTestRunner.NAME,
                                       {suite_name})
        unittest_utils.assert_equal_testinfos(self, t_info, want_info)

        suite_name = 'CTS'
        t_info = self.test_suite_finder.find_test_by_suite_name(suite_name)
        want_info = None
        unittest_utils.assert_equal_testinfos(self, t_info, want_info)


if __name__ == '__main__':
    unittest.main()
