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

"""Unittests for atest_tf_test_runner."""

import unittest
import mock

# pylint: disable=import-error
import unittest_utils
import atest_tf_test_runner as atf_tr

#pylint: disable=protected-access
#pylint: disable=no-self-use
#pylint: disable=invalid-name
TEST_INFO_DIR = '/tmp/atest_run_1510085893_pi_Nbi'
TEST_INFO_FILE = '%s/test_info.json' % TEST_INFO_DIR
RUN_CMD_ARGS = '--test-info-file %s --log-level WARN' % TEST_INFO_FILE
RUN_CMD = atf_tr.AtestTradefedTestRunner._RUN_CMD.format(
    exe=atf_tr.AtestTradefedTestRunner.EXECUTABLE,
    template=atf_tr.AtestTradefedTestRunner._TF_TEMPLATE,
    args=RUN_CMD_ARGS)

class AtestTradefedTestRunnerUnittests(unittest.TestCase):
    """Unit tests for atest_tf_test_runner.py"""

    def setUp(self):
        self.tr = atf_tr.AtestTradefedTestRunner(results_dir=TEST_INFO_DIR)

    def tearDown(self):
        mock.patch.stopall()

    @mock.patch('atest_utils.get_result_server_args')
    def test_generate_run_commands(self, mock_resultargs):
        """Test _generate_run_command method."""
        # Basic Run Cmd
        mock_resultargs.return_value = []
        unittest_utils.assert_strict_equal(
            self,
            self.tr._generate_run_commands(TEST_INFO_FILE, {}),
            RUN_CMD)
        # Run cmd with result server args.
        result_arg = '--result_arg'
        mock_resultargs.return_value = [result_arg]
        unittest_utils.assert_strict_equal(
            self,
            self.tr._generate_run_commands(TEST_INFO_FILE, {}),
            RUN_CMD + ' ' + result_arg)


if __name__ == '__main__':
    unittest.main()
