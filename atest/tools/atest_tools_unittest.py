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

"""Unittest for atest_tools."""

import os
import pickle
import platform
import subprocess
import sys
import unittest
import mock

import atest_tools
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
# pylint: disable=wrong-import-position
import unittest_constants as uc

SEARCH_ROOT = uc.TEST_DATA_DIR
PRUNEPATH = uc.TEST_CONFIG_DATA_DIR


class AtestToolsUnittests(unittest.TestCase):
    """"Unittest Class for atest_tools.py."""

    @mock.patch('module_info.ModuleInfo.get_testable_modules')
    @mock.patch('module_info.ModuleInfo.__init__')
    def test_index_targets(self, mock_mod_info, mock_testable_mod):
        """Test method index_targets."""
        mock_mod_info.return_value = None
        mock_testable_mod.return_value = set()
        if atest_tools.has_command('updatedb'):
            atest_tools.run_updatedb(SEARCH_ROOT, uc.LOCATE_CACHE,
                                     prunepaths=PRUNEPATH)
            # test_config/ is excluded so that a.xml won't be found.
            locate_cmd1 = ['locate', '-d', uc.LOCATE_CACHE, '/a.xml']
            # locate always return 0 when not found in Darwin, therefore,
            # check null return in Darwin and return value in Linux.
            if platform.system() == 'Darwin':
                self.assertEqual(subprocess.check_output(locate_cmd1), "")
            else:
                self.assertEqual(subprocess.call(locate_cmd1), 1)
            # module-info.json can be found in the search_root.
            locate_cmd2 = ['locate', '-d', uc.LOCATE_CACHE, 'module-info.json']
            self.assertEqual(subprocess.call(locate_cmd2), 0)
        else:
            self.assertEqual(atest_tools.has_command('updatedb'), False)

        if atest_tools.has_command('locate'):
            atest_tools.index_targets(uc.LOCATE_CACHE,
                                      class_index=uc.CLASS_INDEX,
                                      qclass_index=uc.QCLASS_INDEX,
                                      cc_class_index=uc.CC_CLASS_INDEX,
                                      package_index=uc.PACKAGE_INDEX,
                                      module_index=uc.MODULE_INDEX)
            _dict = {}
            # Test finding a Java class
            with open(uc.CLASS_INDEX, 'rb') as _cache:
                _dict = pickle.load(_cache)
            self.assertIsNotNone(_dict.get('PathTesting'))
            # Test finding a CC class
            with open(uc.CC_CLASS_INDEX, 'rb') as _cache:
                _dict = pickle.load(_cache)
            self.assertIsNotNone(_dict.get('HelloWorldTest'))
            # Test finding a package
            with open(uc.PACKAGE_INDEX, 'rb') as _cache:
                _dict = pickle.load(_cache)
            self.assertIsNotNone(_dict.get('android.jank.cts.ui'))
            # Test finding a fully qualified class name
            with open(uc.QCLASS_INDEX, 'rb') as _cache:
                _dict = pickle.load(_cache)
            self.assertIsNotNone(_dict.get('android.jank.cts.ui.PathTesting'))
            # Clean up.
            targets_to_delete = (uc.LOCATE_CACHE,
                                 uc.CLASS_INDEX,
                                 uc.QCLASS_INDEX,
                                 uc.CC_CLASS_INDEX,
                                 uc.PACKAGE_INDEX,
                                 uc.MODULE_INDEX)
            for idx in targets_to_delete:
                os.remove(idx)
        else:
            self.assertEqual(atest_tools.has_command('locate'), False)

if __name__ == "__main__":
    unittest.main()
