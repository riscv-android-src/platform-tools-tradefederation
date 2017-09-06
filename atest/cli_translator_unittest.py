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

import json
import unittest
import os
import mock

import cli_translator as cli_t

ROOT = '/'
MODULE_NAME = 'CtsJankDeviceTestCases'
CLASS_NAME = 'CtsDeviceJankUi'
MODULE_DIR = 'cts/tests/jank'
CLASS_DIR = 'cts/tests/jank/src/android/jank/cts/ui'
QUALIFIED_CLASS_NAME = 'android.jank.cts.ui.CtsDeviceJankUi'
REF_TYPE = cli_t.REFERENCE_TYPE
MODULE_INFO = cli_t.TestInfo(MODULE_NAME, MODULE_DIR, None)
CLASS_INFO = cli_t.TestInfo(MODULE_NAME, MODULE_DIR, CLASS_NAME)
TARGETS = {'tradefed-all', 'MODULES-IN-%s' % MODULE_DIR.replace('/', '-')}
RUN_CMD = cli_t.RUN_CMD % MODULE_NAME
PRODUCT = 'bullhead'
OUT = '/android/master/out/target/product/%s' % PRODUCT
INFO_JSON = {
    'AmSlam':{
        'class': ['APPS'],
        'path': ['frameworks/base/tests/AmSlam'],
        'tags': ['tests'],
        'installed': ['out/target/product/bullhead/data/app/AmSlam/'
                      'AmSlam.apk']},
    "CtsJankDeviceTestCases": {
        "class": ["APPS"],
        "path": ["cts/tests/jank"],
        "tags": ["optional"],
        "installed": ["out/target/product/bullhead/data/app/"
                      "CtsJankDeviceTestCases/CtsJankDeviceTestCases.apk"]}
}
FIND_ONE = ROOT + 'cts/tests/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java\n'
FIND_TWO = ROOT + 'other/dir/test.java\n' + FIND_ONE
FIND_OVER_MAX = FIND_ONE * (cli_t.MAX_TEST_CHOICES_FOR_USER_INPUT + 1)
XML_TARGETS = {'CtsUiDeviceTestCases', 'CtsJankDeviceTestCases'}

def isfile_side_effect(value):
    """Mock return values for os.path.isfile"""
    if value == '/%s/%s' % (MODULE_DIR, cli_t.TEST_CONFIG):
        return True
    if value.endswith('.java'):
        return True
    return False

def findtest_side_effect(test_name, _):
    """Mock return values for _get_test_info"""
    if test_name == MODULE_NAME:
        return MODULE_INFO
    if test_name == CLASS_NAME:
        return CLASS_INFO

def realpath_side_effect(path):
    """Mock return values for os.path.realpath."""
    return path

#pylint: disable=protected-access
#pylint: disable=no-self-use
class CLITranslatorUnittests(unittest.TestCase):
    """Unit tests for cli_t.py"""

    def setUp(self):
        """Run before execution of every test"""
        self.patches = {
            'isdir': mock.patch('os.path.isdir', return_value=True),
            'load_module_info': mock.patch.object(cli_t.CLITranslator,
                                                  '_load_module_info',
                                                  return_value=INFO_JSON),
        }
        self.mocks = {k: v.start() for k, v in self.patches.iteritems()}
        self.ctr = cli_t.CLITranslator()

    def tearDown(self):
        for _, patch in self.patches.iteritems():
            patch.stop()

    @mock.patch('os.environ.get', side_effects=[OUT, PRODUCT])
    @mock.patch('os.path.isfile', return_value=True)
    @mock.patch('__builtin__.open', new_callable=mock.mock_open,
                read_data=json.dumps(INFO_JSON))
    @mock.patch('subprocess.check_output')
    def test_load_module_info(self, _checkout, _open, mock_isfile, _envget):
        """Test _load_module_info loads module_info correctly"""
        # stop patch and instantiate new cli_t, because this is mocked in setup.
        self.patches['load_module_info'].stop()
        del self.patches['load_module_info']
        new_ctr = cli_t.CLITranslator()
        # called in __init__ so just check self.module_info
        self.assertEquals(new_ctr.module_info, INFO_JSON)
        # test logic when module-info.json file doesn't exist yet.
        mock_isfile.return_value = False
        self.assertEquals(new_ctr._load_module_info(), INFO_JSON)

    def test_get_test_reference_types(self):
        """Test _get_test_reference_types parses reference types correctly."""
        self.assertEquals(
            self.ctr._get_test_reference_types('moduleOrClassName'),
            [REF_TYPE.MODULE, REF_TYPE.CLASS]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('module_or_class_name'),
            [REF_TYPE.MODULE, REF_TYPE.CLASS]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('class.name.or.package'),
            [REF_TYPE.CLASS, REF_TYPE.PACKAGE]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('module:class'),
            [REF_TYPE.MODULE_CLASS]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('module:class.or.package'),
            [REF_TYPE.MODULE_CLASS, REF_TYPE.MODULE_PACKAGE]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('.'),
            [REF_TYPE.FILE_PATH]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('..'),
            [REF_TYPE.FILE_PATH]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('./rel/path/to/test'),
            [REF_TYPE.FILE_PATH]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('rel/path/to/test'),
            [REF_TYPE.FILE_PATH, REF_TYPE.INTEGRATION, REF_TYPE.SUITE]
        )
        self.assertEquals(
            self.ctr._get_test_reference_types('/abs/path/to/test'),
            [REF_TYPE.FILE_PATH, REF_TYPE.INTEGRATION, REF_TYPE.SUITE]
        )

    def test_is_equal_or_sub_dir(self):
        """Test _is_equal_or_sub_dir method."""
        self.assertTrue(self.ctr._is_equal_or_sub_dir('/a/b/c', '/'))
        self.assertTrue(self.ctr._is_equal_or_sub_dir('/a/b/c', '/a'))
        self.assertTrue(self.ctr._is_equal_or_sub_dir('/a/b/c', '/a/b/c'))
        self.assertFalse(self.ctr._is_equal_or_sub_dir('/a/b', '/a/b/c'))
        self.assertFalse(self.ctr._is_equal_or_sub_dir('/a', '/f'))
        self.mocks['isdir'].return_value = False
        self.assertFalse(self.ctr._is_equal_or_sub_dir('/a/b', '/a'))

    @mock.patch('os.path.isfile', side_effect=isfile_side_effect)
    def test_find_parent_module_dir(self, _):
        """Test _find_parent_module_dir method."""
        abs_class_dir = '/%s' % CLASS_DIR
        self.assertEquals(self.ctr._find_parent_module_dir(abs_class_dir),
                          MODULE_DIR)

    @mock.patch('__builtin__.raw_input', return_value='1')
    def test_extract_test_dir(self, _):
        """Test _extract_test_dir method."""
        self.assertEquals(self.ctr._extract_test_dir(FIND_ONE),
                          ROOT + CLASS_DIR)
        self.assertEquals(self.ctr._extract_test_dir(FIND_TWO),
                          ROOT + CLASS_DIR)
        self.assertRaises(cli_t.TooManyTestsFoundError,
                          self.ctr._extract_test_dir, FIND_OVER_MAX)

    def test_get_module_name(self):
        """Test _get_module_name method."""
        self.assertEquals(self.ctr._get_module_name(MODULE_DIR), MODULE_NAME)
        self.assertRaises(cli_t.UnregisteredModuleError,
                          self.ctr._get_module_name, 'bad/path')

    def test_get_targets_from_xml(self):
        """Test _get_targets_from_xml method."""
        # Mocking Etree is near impossible, so use a real file.
        xml_dir = os.path.join(os.path.dirname(os.path.realpath(__file__)),
                               'unittest_data')
        self.assertEquals(self.ctr._get_targets_from_xml(xml_dir), XML_TARGETS)


    def test_find_test_by_module_name(self):
        """Test _find_test_by_module_name method."""
        self.assertEquals(self.ctr._find_test_by_module_name(MODULE_NAME),
                          MODULE_INFO)
        self.assertIsNone(self.ctr._find_test_by_module_name('Not_Module'))

    @mock.patch('subprocess.check_output', return_value=FIND_ONE)
    @mock.patch('os.path.isfile', side_effect=isfile_side_effect)
    def test_find_test_by_class_name(self, _, mock_checkoutput):
        """Test _find_test_by_class_name method."""
        self.assertEquals(self.ctr._find_test_by_class_name(CLASS_NAME),
                          CLASS_INFO)
        mock_checkoutput.return_value = ''
        self.assertIsNone(self.ctr._find_test_by_class_name('Not class'))

    @mock.patch('os.path.realpath', side_effect=realpath_side_effect)
    @mock.patch('os.path.isfile', side_effect=isfile_side_effect)
    @mock.patch.object(cli_t.CLITranslator, '_get_module_name',
                       return_value=MODULE_NAME)
    @mock.patch.object(cli_t.CLITranslator, '_find_parent_module_dir')
    @mock.patch('os.path.exists')
    def test_find_test_by_path(self, mock_pathexists, mock_dir, _name,
                               _isfile, _real):
        """Test _find_test_by_path method."""
        mock_pathexists.return_value = False
        self.assertEquals(None, self.ctr._find_test_by_path('some/bad/path'))
        mock_pathexists.return_value = True
        mock_dir.return_value = None
        self.assertEquals(None, self.ctr._find_test_by_path('no/module/found'))
        mock_dir.return_value = MODULE_DIR
        self.assertEquals(CLASS_INFO,
                          self.ctr._find_test_by_path('%s.java' % CLASS_NAME))
        self.assertEquals(MODULE_INFO,
                          self.ctr._find_test_by_path('/some/dir'))

    @mock.patch.object(cli_t.CLITranslator, '_get_targets_from_xml',
                       return_value=set())
    def test_generate_build_targets(self, _):
        """Test _generate_build_targets method."""
        self.assertEquals(self.ctr._generate_build_targets(MODULE_INFO),
                          TARGETS)

    def test_generate_run_command(self):
        """Test _generate_run_command method."""
        self.assertEquals(self.ctr._generate_run_command(MODULE_INFO), RUN_CMD)

    @mock.patch.object(cli_t.CLITranslator, '_find_test_by_module_name')
    @mock.patch.object(cli_t.CLITranslator, '_find_test_by_class_name')
    def test_get_test_info(self, mock_findbyclass, mock_findbymodule):
        """Test _find_test method."""
        ctr = cli_t.CLITranslator()
        mock_findbymodule.return_value = MODULE_INFO
        mock_findbyclass.return_value = CLASS_INFO
        refs = [REF_TYPE.MODULE, REF_TYPE.CLASS]
        self.assertEquals(ctr._get_test_info(MODULE_NAME, refs), MODULE_INFO)
        mock_findbymodule.return_value = None
        self.assertEquals(ctr._get_test_info(CLASS_NAME, refs), CLASS_INFO)
        mock_findbyclass.return_value = None
        self.assertIsNone(ctr._get_test_info(CLASS_NAME, refs))

    @mock.patch.object(cli_t.CLITranslator, '_get_targets_from_xml',
                       return_value=set())
    @mock.patch.object(cli_t.CLITranslator, '_get_test_info',
                       side_effect=findtest_side_effect)
    def test_translate(self, _info, _xml):
        """Test translate method."""
        targets, run_cmds = self.ctr.translate([MODULE_NAME, CLASS_NAME])
        self.assertEquals(targets, set(TARGETS))
        self.assertEquals(run_cmds, [RUN_CMD, RUN_CMD])
        targets, run_cmds = self.ctr.translate([CLASS_NAME])
        self.assertEquals(targets, set(TARGETS))
        self.assertEquals(run_cmds, [RUN_CMD])
        self.assertRaises(cli_t.NoTestFoundError, self.ctr.translate,
                          ['NonExistentClassOrModule'])

if __name__ == '__main__':
    unittest.main()
