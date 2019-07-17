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

"""Unittest for atest_updatedb."""

import os
import platform
import subprocess
import sys
import unittest

import atest_updatedb
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
# pylint: disable=wrong-import-position
import unittest_constants

SEARCH_ROOT = unittest_constants.TEST_DATA_DIR
PRUNEPATH = unittest_constants.TEST_CONFIG_DATA_DIR
_CACHE = '/tmp/locate.database'


class AtestUpdatedbUnittests(unittest.TestCase):
    """"Unittest Class for atest_updatedb.py."""

    def test_atest_updatedb(self):
        """Test method run_updatedb."""
        atest_updatedb.run_updatedb(search_root=SEARCH_ROOT,
                                    prunepaths=PRUNEPATH,
                                    output_cache=_CACHE)
        # test_config/ is excluded so that a.xml won't be found.
        locate_cmd1 = ['locate', '-d', _CACHE, '/a.xml']
        # locate always return 0 when not found in Darwin, therefore,
        # check null return in Darwin and return value in Linux.
        if platform.system() == 'Darwin':
            self.assertEqual(subprocess.check_output(locate_cmd1), "")
        else:
            self.assertEqual(subprocess.call(locate_cmd1), 1)
        # module-info.json can be found in the search_root.
        locate_cmd2 = ['locate', '-d', _CACHE, 'module-info.json']
        self.assertEqual(subprocess.call(locate_cmd2), 0)

if __name__ == "__main__":
    unittest.main()
