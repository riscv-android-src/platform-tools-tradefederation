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

"""Unittests for cli_translator."""

import unittest
import mock

import cli_translator as cli_t

MODULE_NAME = 'libgltest'
RefType = cli_t.TestReferenceType
TEST_INFO = cli_t.TestInfo(RefType.MODULE, MODULE_NAME)
TARGETS = [MODULE_NAME, 'tradefed-all']
RUN_CMD = cli_t.RUN_CMD % MODULE_NAME

#pylint: disable=protected-access
class CLITranslatorUnittests(unittest.TestCase):
    """Unit tests for cli_t.py"""

    def setUp(self):
        """Run before execution of every test"""
        self.ctr = cli_t.CLITranslator()

    def test_get_test_reference_types(self):
        """Test _get_test_reference_types parses reference types correctly."""
        self.assertEquals(
            self.ctr._get_test_reference_types('moduleOrClassName'),
            # TODO(b/64484081): Add RefType.CLASS when we support class.
            [RefType.MODULE]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('module_or_class_name'),
            # TODO(b/64484081): Add RefType.CLASS when we support class.
            [RefType.MODULE]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('class.name.or.package'),
            [RefType.CLASS, RefType.PACKAGE]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('module:class'),
            [RefType.MODULE_CLASS]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('module:class.or.package'),
            [RefType.MODULE_CLASS, RefType.MODULE_PACKAGE]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('.'),
            [RefType.FILE_PATH]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('..'),
            [RefType.FILE_PATH]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('./rel/path/to/test'),
            [RefType.FILE_PATH]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('rel/path/to/test'),
            [RefType.FILE_PATH, RefType.INTEGRATION, RefType.SUITE]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('/abs/path/to/test'),
            [RefType.FILE_PATH, RefType.INTEGRATION, RefType.SUITE]
        )

    def test_generate_build_targets(self):
        """Test _generate_build_targets method."""
        self.assertEquals(self.ctr._generate_build_targets(TEST_INFO), TARGETS)

    def test_generate_run_command(self):
        """Test _generate_run_command method."""
        self.assertEquals(self.ctr._generate_run_command(TEST_INFO), RUN_CMD)

    def test_get_test_info(self):
        """Test _find_test method."""
        self.assertEquals(self.ctr._get_test_info(MODULE_NAME, [RefType.CLASS]),
                          None)
        # Because of __init__ logic need to mock before instantiation,
        # so mock and instantiate a new translator.
        with mock.patch.object(cli_t.CLITranslator,
                               '_get_test_info_by_module_name') as mock_find:
            ctr = cli_t.CLITranslator()
            mock_find.return_value = TEST_INFO
            self.assertEquals(ctr._get_test_info(MODULE_NAME, [RefType.MODULE]),
                              TEST_INFO)
            mock_find.return_value = None
            self.assertEquals(ctr._get_test_info(MODULE_NAME, [RefType.MODULE]),
                              None)

    def test_translate(self):
        """Test translate method."""
        with mock.patch.object(self.ctr, '_get_test_info') as mock_find_test:
            mock_find_test.return_value = TEST_INFO
            self.assertEquals(self.ctr.translate([MODULE_NAME]),
                              (TARGETS, [RUN_CMD]))
            mock_find_test.return_value = None
            self.assertRaises(cli_t.NoTestFoundError,
                              self.ctr.translate, [MODULE_NAME])

if __name__ == '__main__':
    unittest.main()
