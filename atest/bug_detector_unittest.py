#!/usr/bin/env python
#
# Copyright 2019, The Android Open Source Project
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

"""Unittests for bug_detector."""

import json
import os
import unittest
import mock

import bug_detector
import unittest_constants as uc

TEST_DICT = {
    'test1': {
        'latest_exit_code': 5,
        'updated_at': ''
    },
    'test2': {
        'latest_exit_code': 0,
        'updated_at': ''
    }
}

class BugDetectorUnittest(unittest.TestCase):
    """Unit test for bug_detector.py"""

    def setUp(self):
        """Set up stuff for testing."""
        self.history_file = os.path.join(uc.TEST_DATA_DIR, 'bug_detector.json')
        self.detector = bug_detector.BugDetector(['test1'], 5, self.history_file)
        self._reset_history_file()

    def tearDown(self):
        """Run after execution of every test"""
        if os.path.isfile(self.history_file):
            os.remove(self.history_file)

    def _reset_history_file(self):
        """Reset test history file."""
        with open(self.history_file, 'w') as outfile:
            json.dump(TEST_DICT, outfile)

    @mock.patch.object(bug_detector.BugDetector, 'update_history')
    def test_get_detect_key(self, _):
        """Test get_detect_key."""
        # argv without -v
        argv = ['test2', 'test1']
        want_key = 'test1 test2'
        dtr = bug_detector.BugDetector(argv, 0)
        self.assertEqual(dtr.get_detect_key(argv), want_key)

        # argv with -v
        argv = ['-v', 'test2', 'test1']
        want_key = 'test1 test2'
        dtr = bug_detector.BugDetector(argv, 0)
        self.assertEqual(dtr.get_detect_key(argv), want_key)

        # argv with --verbose
        argv = ['--verbose', 'test2', 'test3', 'test1']
        want_key = 'test1 test2 test3'
        dtr = bug_detector.BugDetector(argv, 0)
        self.assertEqual(dtr.get_detect_key(argv), want_key)

    def test_get_history(self):
        """Test get_history."""
        self.assertEqual(self.detector.get_history(), TEST_DICT)

    @mock.patch.object(bug_detector.BugDetector, 'update_history')
    def test_detect_bug_caught(self, _):
        """Test detect_bug_caught."""
        self._reset_history_file()
        dtr = bug_detector.BugDetector(['test1'], 0, self.history_file)
        success = 1
        self.assertEqual(dtr.detect_bug_caught(), success)


if __name__ == '__main__':
    unittest.main()
