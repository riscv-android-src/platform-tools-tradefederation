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
"""Unittests for robolectric_test_runner."""

import unittest
import mock

# pylint: disable=import-error
from test_finders import test_info
from test_runners import robolectric_test_runner

# pylint: disable=protected-access
class RobolectricTestRunnerUnittests(unittest.TestCase):
    """Unit tests for robolectric_test_runner.py"""

    def setUp(self):
        self.suite_tr = robolectric_test_runner.RobolectricTestRunner(results_dir='')

    def tearDown(self):
        mock.patch.stopall()

    @mock.patch('atest_utils.build')
    def test_run_tests(self, mock_build):
        """Test run_tests method."""
        test_infos = [test_info.TestInfo("Robo1",
                                         "RobolectricTestRunner",
                                         ["RoboTest"])]
        extra_args = []
        mock_reporter = mock.Mock()
        # Test Build Pass
        mock_build.return_value = True
        self.assertEqual(
            0,
            self.suite_tr.run_tests(test_infos, extra_args, mock_reporter))
        # Test Build Fail
        mock_build.return_value = False
        self.assertNotEqual(
            0,
            self.suite_tr.run_tests(test_infos, extra_args, mock_reporter))


if __name__ == '__main__':
    unittest.main()
