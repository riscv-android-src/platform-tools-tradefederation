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
TEST_INFO_DIR = '/tmp/atest_run_1510085893_pi_Nbi'
TEST_INFO_FILE = '%s/test_info.json' % TEST_INFO_DIR
MODULE_NAME = 'CtsJankDeviceTestCases'
MODULE2_NAME = 'HelloWorldTests'
CLASS_NAME = 'CtsDeviceJankUi'
CLASS2_NAME = 'SomeOtherClass'
MODULE_CLASS = '%s:%s' % (MODULE_NAME, CLASS_NAME)
METHOD_NAME = 'method1'
METHOD2_NAME = 'method2'
MODULE_CLASS_METHOD = '%s#%s' % (MODULE_CLASS, METHOD_NAME)
MODULE_DIR = 'foo/bar/jank'
MODULE2_DIR = 'foo/bar/hello'
CLASS_DIR = 'foo/bar/jank/src/android/jank/cts/ui'
FULL_CLASS_NAME = 'android.jank.cts.ui.CtsDeviceJankUi'
FULL_CLASS2_NAME = 'android.jank.cts.ui.SomeOtherClass'
INT_NAME = 'example/reboot'
INT_DIR = 'tf/contrib/res/config'
GTF_INT_NAME = 'some/gtf_int_test'
GTF_INT_DIR = 'gtf/core/res/config'
REF_TYPE = cli_t.REFERENCE_TYPE
CONFIG_FILE = os.path.join(MODULE_DIR, cli_t.MODULE_CONFIG)
CONFIG2_FILE = os.path.join(MODULE2_DIR, cli_t.MODULE_CONFIG)
MODULE_INFO = cli_t.TestInfo(CONFIG_FILE, MODULE_NAME, None, frozenset())
MODULE2_INFO = cli_t.TestInfo(CONFIG2_FILE, MODULE2_NAME, None, frozenset())
CLASS_FILTER = cli_t.TestFilter(FULL_CLASS_NAME, frozenset())
CLASS_INFO = cli_t.TestInfo(CONFIG_FILE, MODULE_NAME, None,
                            frozenset([CLASS_FILTER]))
CLASS2_FILTER = cli_t.TestFilter(FULL_CLASS2_NAME, frozenset())
CLASS2_INFO = cli_t.TestInfo(CONFIG_FILE, MODULE_NAME, None,
                             frozenset([CLASS2_FILTER]))
FLAT_CLASS_INFO = cli_t.TestInfo(CONFIG_FILE, MODULE_NAME, None,
                                 frozenset([CLASS_FILTER, CLASS2_FILTER]))
METHOD_FILTER = cli_t.TestFilter(FULL_CLASS_NAME, frozenset([METHOD_NAME]))
METHOD_INFO = cli_t.TestInfo(CONFIG_FILE, MODULE_NAME, None,
                             frozenset([METHOD_FILTER]))
METHOD2_FILTER = cli_t.TestFilter(FULL_CLASS_NAME, frozenset([METHOD2_NAME]))
METHOD2_INFO = cli_t.TestInfo(CONFIG_FILE, MODULE_NAME, None,
                              frozenset([METHOD2_FILTER]))
FLAT_METHOD_FILTER = cli_t.TestFilter(FULL_CLASS_NAME,
                                      frozenset([METHOD_NAME, METHOD2_NAME]))
FLAT_METHOD_INFO = cli_t.TestInfo(CONFIG_FILE, MODULE_NAME, None,
                                  frozenset([FLAT_METHOD_FILTER]))
CLASS2_METHOD_FILTER = cli_t.TestFilter(FULL_CLASS2_NAME,
                                        frozenset([METHOD_NAME, METHOD2_NAME]))
CLASS2_METHOD_INFO = cli_t.TestInfo(
    CONFIG_FILE, MODULE_NAME, None, frozenset([
        cli_t.TestFilter(FULL_CLASS2_NAME,
                         frozenset([METHOD_NAME, METHOD2_NAME]))]))
METHOD_AND_CLASS2_METHOD = cli_t.TestInfo(CONFIG_FILE, MODULE_NAME, None,
                                          frozenset([METHOD_FILTER,
                                                     CLASS2_METHOD_FILTER]))
METHOD_METHOD2_AND_CLASS2_METHOD = cli_t.TestInfo(
    CONFIG_FILE, MODULE_NAME, None, frozenset([FLAT_METHOD_FILTER,
                                               CLASS2_METHOD_FILTER]))
INT_CONFIG = os.path.join(INT_DIR, INT_NAME + '.xml')
GTF_INT_CONFIG = os.path.join(GTF_INT_DIR, GTF_INT_NAME + '.xml')
INT_INFO = cli_t.TestInfo(INT_CONFIG, None, INT_NAME, frozenset())
GTF_INT_INFO = cli_t.TestInfo(GTF_INT_CONFIG, None, GTF_INT_NAME, frozenset())
JSON_FILE = 'module-info.json'
TEST_DATA_DIR = 'unittest_data'
JSON_FILE_PATH = os.path.join(os.path.dirname(__file__), TEST_DATA_DIR)
INFO_JSON = json.load(open(os.path.join(JSON_FILE_PATH, JSON_FILE)))
MODULE_INFO_TARGET = '/out/%s' % JSON_FILE
INT_TARGETS = {'tradefed-all', MODULE_INFO_TARGET}
GTF_INT_TARGETS = {'google-tradefed-all', MODULE_INFO_TARGET}
TARGETS = {'tradefed-all', MODULE_INFO_TARGET,
           'MODULES-IN-%s' % MODULE_DIR.replace('/', '-')}
GTF_TARGETS = {'google-tradefed-all', MODULE_INFO_TARGET,
               'MODULES-IN-%s' % MODULE_DIR.replace('/', '-')}
RUN_CMD_ARGS = '--test-info-file %s --log-level WARN' % TEST_INFO_FILE
RUN_CMD = cli_t.RUN_CMD % (cli_t.TF_TEMPLATE, RUN_CMD_ARGS)
GTF_RUN_CMD_ARGS = (RUN_CMD_ARGS +
                    ' --sponge-label %s' % cli_t.ATEST_SPONGE_LABEL)
GTF_RUN_CMD = cli_t.RUN_CMD % (cli_t.GTF_TEMPLATE, GTF_RUN_CMD_ARGS)
PRODUCT = 'bullhead'
OUT = '/android/master/out/target/product/%s' % PRODUCT
FIND_ONE = ROOT + 'foo/bar/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java\n'
FIND_TWO = ROOT + 'other/dir/test.java\n' + FIND_ONE
XML_TARGETS = {'CtsUiDeviceTestCases', 'CtsJankDeviceTestCases', 'VtsTarget'}

def isfile_side_effect(value):
    """Mock return values for os.path.isfile"""
    if value == '/%s/%s' % (MODULE_DIR, cli_t.MODULE_CONFIG):
        return True
    if value.endswith('.java'):
        return True
    if value.endswith(INT_NAME + '.xml'):
        return True
    if value.endswith(GTF_INT_NAME + '.xml'):
        return True

def findtest_side_effect(test_name, _):
    """Mock return values for _get_test_info"""
    if test_name == MODULE_NAME:
        return MODULE_INFO
    if test_name == CLASS_NAME:
        return CLASS_INFO
    if test_name == MODULE_CLASS:
        return CLASS_INFO
    if test_name == INT_NAME:
        return INT_INFO
    if test_name == GTF_INT_NAME:
        return GTF_INT_INFO

def targetsfromxml_side_effect(_):
    """Mock return values for _get_targets_from_xml"""
    # Must use side_effect instead of return_value to avoid pointer to same set
    return set()

def realpath_side_effect(path):
    """Mock return values for os.path.realpath."""
    return os.path.join(ROOT, path)

#pylint: disable=protected-access
#pylint: disable=no-self-use
class CLITranslatorUnittests(unittest.TestCase):
    """Unit tests for cli_t.py"""

    def setUp(self):
        """Run before execution of every test"""
        self.patches = {
            'isdir': mock.patch('os.path.isdir', return_value=True),
            'load_module_info': mock.patch.object(
                    cli_t.CLITranslator, '_load_module_info',
                    return_value=(MODULE_INFO_TARGET, INFO_JSON)),
        }
        self.mocks = {k: v.start() for k, v in self.patches.iteritems()}
        self.ctr = cli_t.CLITranslator(results_dir=TEST_INFO_DIR)
        # Assume you are running in an AOSP environment.
        self._gtf_dirs = self.ctr.gtf_dirs
        self.ctr.gtf_dirs = []

    def tearDown(self):
        for _, patch in self.patches.iteritems():
            patch.stop()

    #pylint: disable=invalid-name
    def assertStrictEqual(self, first, second):
        """Check for strict equality and strict equality of nametuple elements.

        assertEqual considers types equal to their subtypes, but we want to
        not consider set() and frozenset() equal for testing.
        """
        self.assertEqual(first, second)
        # allow byte and unicode string equality.
        if not (isinstance(first, basestring) and
                isinstance(second, basestring)):
            self.assertIsInstance(first, type(second))
            self.assertIsInstance(second, type(first))
        # Recursively check elements of namedtuples for strict equals.
        if isinstance(first, tuple) and hasattr(first, '_fields'):
            for f in first._fields:
                self.assertStrictEqual(getattr(first, f), getattr(second, f))

    @mock.patch('os.environ.get', return_value=JSON_FILE_PATH)
    @mock.patch('os.path.isfile', return_value=True)
    @mock.patch('atest_utils._run_limited_output')
    def test_load_module_info(self, _checkout, mock_isfile, _envget):
        """Test _load_module_info loads module-info.json correctly"""
        # stop patch and instantiate new cli_t, because this is mocked in setup.
        self.patches['load_module_info'].stop()
        del self.patches['load_module_info']
        new_ctr = cli_t.CLITranslator(results_dir=TEST_INFO_DIR)
        # called in __init__ so just check self.module_info
        self.assertStrictEqual(new_ctr.module_info, INFO_JSON)
        # test logic when module-info.json file doesn't exist yet.
        mock_isfile.return_value = False
        self.assertStrictEqual(new_ctr._load_module_info()[1], INFO_JSON)

    def test_get_test_reference_types(self):
        """Test _get_test_reference_types parses reference types correctly."""
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('moduleOrClassName'),
            [REF_TYPE.INTEGRATION, REF_TYPE.MODULE, REF_TYPE.CLASS]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('module_or_class_name'),
            [REF_TYPE.INTEGRATION, REF_TYPE.MODULE, REF_TYPE.CLASS]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('class.name.or.package'),
            [REF_TYPE.FILE_PATH, REF_TYPE.QUALIFIED_CLASS, REF_TYPE.PACKAGE]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('module:class'),
            [REF_TYPE.MODULE_CLASS]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('module:class.or.package'),
            [REF_TYPE.MODULE_CLASS, REF_TYPE.MODULE_PACKAGE]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('.'),
            [REF_TYPE.FILE_PATH]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('..'),
            [REF_TYPE.FILE_PATH]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('./rel/path/to/test'),
            [REF_TYPE.FILE_PATH]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('rel/path/to/test'),
            [REF_TYPE.FILE_PATH, REF_TYPE.INTEGRATION]
        )
        self.assertStrictEqual(
            self.ctr._get_test_reference_types('/abs/path/to/test'),
            [REF_TYPE.FILE_PATH]
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
        self.assertStrictEqual(self.ctr._find_parent_module_dir(abs_class_dir),
                               MODULE_DIR)

    @mock.patch('__builtin__.raw_input', return_value='1')
    def test_extract_test_path(self, _):
        """Test _extract_test_dir method."""
        path = os.path.join(ROOT, CLASS_DIR, CLASS_NAME + '.java')
        self.assertStrictEqual(self.ctr._extract_test_path(FIND_ONE), path)
        path = os.path.join(ROOT, CLASS_DIR, CLASS_NAME + '.java')
        self.assertStrictEqual(self.ctr._extract_test_path(FIND_TWO), path)

    def test_get_module_name(self):
        """Test _get_module_name method."""
        self.assertStrictEqual(self.ctr._get_module_name(MODULE_DIR),
                               MODULE_NAME)
        self.assertRaises(cli_t.UnregisteredModuleError,
                          self.ctr._get_module_name, 'bad/path')

    def test_get_targets_from_xml(self):
        """Test _get_targets_from_xml method."""
        # Mocking Etree is near impossible, so use a real file.
        xml_file = os.path.join(
            os.path.dirname(os.path.realpath(__file__)), 'unittest_data',
            cli_t.MODULE_CONFIG)
        self.assertStrictEqual(self.ctr._get_targets_from_xml(xml_file),
                               XML_TARGETS)

    def test_split_methods(self):
        """Test _split_methods method."""
        # Class
        self.assertStrictEqual(self.ctr._split_methods('Class.Name'),
                               ('Class.Name', set()))
        self.assertStrictEqual(self.ctr._split_methods('Class.Name#Method'),
                               ('Class.Name', {'Method'}))
        self.assertStrictEqual(self.ctr._split_methods('Class.Name#Method,Method2'),
                               ('Class.Name', {'Method', 'Method2'}))
        self.assertStrictEqual(self.ctr._split_methods('Class.Name#Method,Method2'),
                               ('Class.Name', {'Method', 'Method2'}))
        self.assertStrictEqual(self.ctr._split_methods('Class.Name#Method,Method2'),
                               ('Class.Name', {'Method', 'Method2'}))
        self.assertRaises(cli_t.TooManyMethodsError, self.ctr._split_methods,
                          'class.name#Method,class.name.2#method')
        # Path
        self.assertStrictEqual(self.ctr._split_methods('foo/bar/class.java'),
                               ('foo/bar/class.java', set()))
        self.assertStrictEqual(self.ctr._split_methods('foo/bar/class.java#Method'),
                               ('foo/bar/class.java', {'Method'}))

    def test_find_test_by_module_name(self):
        """Test _find_test_by_module_name method."""
        self.assertStrictEqual(self.ctr._find_test_by_module_name(MODULE_NAME),
                               MODULE_INFO)
        self.assertIsNone(self.ctr._find_test_by_module_name('Not_Module'))

    @mock.patch('subprocess.check_output', return_value=FIND_ONE)
    @mock.patch.object(cli_t.CLITranslator, '_get_fully_qualified_class_name',
                       return_value=FULL_CLASS_NAME)
    @mock.patch('os.path.isfile', side_effect=isfile_side_effect)
    def test_find_test_by_class_name(self, _isfile, _class, mock_checkoutput):
        """Test _find_test_by_class_name method."""
        self.assertStrictEqual(self.ctr._find_test_by_class_name(CLASS_NAME),
                               CLASS_INFO)
        # with method
        class_with_method = '%s#%s' % (CLASS_NAME, METHOD_NAME)
        self.assertStrictEqual(self.ctr._find_test_by_class_name(class_with_method),
                               METHOD_INFO)
        class_methods = '%s,%s' % (class_with_method, METHOD2_NAME)
        self.assertStrictEqual(self.ctr._find_test_by_class_name(class_methods),
                               FLAT_METHOD_INFO)
        # find output fails to find class file
        mock_checkoutput.return_value = ''
        self.assertIsNone(self.ctr._find_test_by_class_name('Not class'))

    @mock.patch('subprocess.check_output', return_value=FIND_ONE)
    @mock.patch.object(cli_t.CLITranslator, '_get_fully_qualified_class_name',
                       return_value=FULL_CLASS_NAME)
    @mock.patch('os.path.isfile', side_effect=isfile_side_effect)
    def test_find_test_by_module_and_class(self, _isfile, _class,
                                           mock_checkoutput):
        """Test _find_test_by_module_and_class method."""
        test_info = self.ctr._find_test_by_module_and_class(MODULE_CLASS)
        self.assertStrictEqual(test_info, CLASS_INFO)
        # with method
        test_info = self.ctr._find_test_by_module_and_class(MODULE_CLASS_METHOD)
        self.assertStrictEqual(test_info, METHOD_INFO)
        # bad module, good class, returns None
        bad_module = '%s:%s' % ('BadMod', CLASS_NAME)
        self.assertIsNone(self.ctr._find_test_by_module_and_class(bad_module))
        # find output fails to find class file
        mock_checkoutput.return_value = ''
        bad_class = '%s:%s' % (MODULE_NAME, 'Anything')
        self.assertIsNone(self.ctr._find_test_by_module_and_class(bad_class))

    @mock.patch('subprocess.check_output')
    @mock.patch('os.path.exists', return_value=True)
    def test_find_test_by_integration_name(self, _, mock_find):
        """Test _find_test_by_integration_name method"""
        mock_find.return_value = os.path.join(ROOT, INT_DIR, INT_NAME + '.xml')
        test_info = self.ctr._find_test_by_integration_name(INT_NAME)
        self.assertTrue(test_info == INT_INFO)
        mock_find.return_value = os.path.join(ROOT, GTF_INT_DIR,
                                              GTF_INT_NAME + '.xml')
        self.assertStrictEqual(
            self.ctr._find_test_by_integration_name(GTF_INT_NAME),
            GTF_INT_INFO)
        mock_find.return_value = ''
        self.assertIsNone(self.ctr._find_test_by_integration_name('NotIntName'))

    @mock.patch.object(cli_t.CLITranslator, '_get_fully_qualified_class_name',
                       return_value=FULL_CLASS_NAME)
    @mock.patch('os.path.realpath', side_effect=realpath_side_effect)
    @mock.patch('os.path.isfile', side_effect=isfile_side_effect)
    @mock.patch.object(cli_t.CLITranslator, '_get_module_name',
                       return_value=MODULE_NAME)
    @mock.patch.object(cli_t.CLITranslator, '_find_parent_module_dir')
    @mock.patch('os.path.exists')
    def test_find_test_by_path(self, mock_pathexists, mock_dir, _name,
                               _isfile, _real, _class):
        """Test _find_test_by_path method."""
        mock_pathexists.return_value = False
        self.assertStrictEqual(None, self.ctr._find_test_by_path('bad/path'))
        mock_pathexists.return_value = True
        mock_dir.return_value = None
        self.assertStrictEqual(None, self.ctr._find_test_by_path('no/module'))
        mock_dir.return_value = MODULE_DIR
        class_path = '%s.java' % CLASS_NAME
        self.assertStrictEqual(CLASS_INFO,
                               self.ctr._find_test_by_path(class_path))
        class_with_method = '%s#%s' % (class_path, METHOD_NAME)
        self.assertStrictEqual(self.ctr._find_test_by_path(class_with_method),
                               METHOD_INFO)
        class_with_methods = '%s,%s' % (class_with_method, METHOD2_NAME)
        self.assertStrictEqual(self.ctr._find_test_by_path(class_with_methods),
                               FLAT_METHOD_INFO)
        self.assertStrictEqual(MODULE_INFO,
                               self.ctr._find_test_by_path('/some/dir'))
        path = os.path.join(INT_DIR, INT_NAME + '.xml')
        self.assertStrictEqual(INT_INFO, self.ctr._find_test_by_path(path))
        path = os.path.join(GTF_INT_DIR, GTF_INT_NAME + '.xml')
        self.assertStrictEqual(GTF_INT_INFO, self.ctr._find_test_by_path(path))

    def test_flatten_test_filters(self):
        """Test _flatten_test_filters method."""
        # No Flattening
        filters = self.ctr._flatten_test_filters({CLASS_FILTER})
        self.assertStrictEqual(frozenset([CLASS_FILTER]), filters)
        filters = self.ctr._flatten_test_filters({CLASS2_FILTER})
        self.assertStrictEqual(frozenset([CLASS2_FILTER]), filters)
        filters = self.ctr._flatten_test_filters({METHOD_FILTER})
        self.assertStrictEqual(frozenset([METHOD_FILTER]), filters)
        filters = self.ctr._flatten_test_filters({METHOD_FILTER,
                                                  CLASS2_METHOD_FILTER})
        self.assertStrictEqual(frozenset([METHOD_FILTER, CLASS2_METHOD_FILTER]),
                               filters)
        # Flattening
        filters = self.ctr._flatten_test_filters({METHOD_FILTER,
                                                  METHOD2_FILTER})
        self.assertStrictEqual(filters, frozenset([FLAT_METHOD_FILTER]))
        filters = self.ctr._flatten_test_filters({METHOD_FILTER, METHOD2_FILTER,
                                                  CLASS2_METHOD_FILTER,})
        self.assertStrictEqual(filters,
                               frozenset([FLAT_METHOD_FILTER,
                                          CLASS2_METHOD_FILTER]))

    def test_flatten_test_infos(self):
        """Test _flatten_test_infos method."""
        # No Flattening
        test_infos = self.ctr._flatten_test_infos({MODULE_INFO})
        self.assertStrictEqual(test_infos, {MODULE_INFO})
        test_infos = self.ctr._flatten_test_infos([MODULE_INFO, MODULE2_INFO])
        self.assertStrictEqual(test_infos, {MODULE_INFO, MODULE2_INFO})
        test_infos = self.ctr._flatten_test_infos({CLASS_INFO})
        self.assertStrictEqual(test_infos, {CLASS_INFO})
        test_infos = self.ctr._flatten_test_infos({INT_INFO})
        self.assertStrictEqual(test_infos, {INT_INFO})
        test_infos = self.ctr._flatten_test_infos({METHOD_INFO})
        self.assertStrictEqual(test_infos, {METHOD_INFO})
        # Flattening
        test_infos = self.ctr._flatten_test_infos({CLASS_INFO, CLASS2_INFO})
        self.assertStrictEqual(test_infos, {FLAT_CLASS_INFO})
        test_infos = self.ctr._flatten_test_infos({CLASS_INFO, INT_INFO,
                                                   CLASS2_INFO})
        self.assertStrictEqual(test_infos, {INT_INFO, FLAT_CLASS_INFO})
        test_infos = self.ctr._flatten_test_infos({CLASS_INFO, MODULE_INFO,
                                                   CLASS2_INFO})
        self.assertStrictEqual(test_infos, {MODULE_INFO})
        test_infos = self.ctr._flatten_test_infos({MODULE2_INFO, INT_INFO,
                                                   CLASS_INFO, CLASS2_INFO,
                                                   GTF_INT_INFO})
        self.assertStrictEqual(test_infos, {INT_INFO, GTF_INT_INFO,
                                            FLAT_CLASS_INFO, MODULE2_INFO})
        test_infos = self.ctr._flatten_test_infos({METHOD_INFO,
                                                   CLASS2_METHOD_INFO})
        self.assertStrictEqual(test_infos, {METHOD_AND_CLASS2_METHOD})
        test_infos = self.ctr._flatten_test_infos({METHOD_INFO, METHOD2_INFO,
                                                   CLASS2_METHOD_INFO})
        self.assertStrictEqual(test_infos, {METHOD_METHOD2_AND_CLASS2_METHOD})
        test_infos = self.ctr._flatten_test_infos({METHOD_INFO, METHOD2_INFO,
                                                   CLASS2_METHOD_INFO,
                                                   MODULE2_INFO,
                                                   INT_INFO})
        self.assertStrictEqual(test_infos, {INT_INFO, MODULE2_INFO,
                                            METHOD_METHOD2_AND_CLASS2_METHOD})

    @mock.patch.object(cli_t.CLITranslator, '_get_targets_from_xml',
                       side_effect=targetsfromxml_side_effect)
    def test_generate_build_targets(self, _):
        """Test _generate_build_targets method."""
        # AOSP Targets
        self.ctr.gtf_dirs = None
        self.assertStrictEqual(self.ctr._generate_build_targets([MODULE_INFO]),
                               TARGETS)
        self.assertStrictEqual(self.ctr._generate_build_targets([CLASS_INFO]),
                               TARGETS)
        self.assertStrictEqual(self.ctr._generate_build_targets([INT_INFO]),
                               INT_TARGETS)
        # Internal Targets
        self.ctr.gtf_dirs = self._gtf_dirs
        self.assertEquals(self.ctr._generate_build_targets([MODULE_INFO]),
                          GTF_TARGETS)
        self.assertStrictEqual(self.ctr._generate_build_targets([GTF_INT_INFO]),
                               GTF_INT_TARGETS)

    def test_generate_run_commands(self):
        """Test _generate_run_command method."""
        # AOSP Run Cmd
        self.assertStrictEqual(self.ctr._generate_run_commands(TEST_INFO_FILE),
                               [RUN_CMD])
        # Internal Run Cmd
        self.ctr.gtf_dirs = self._gtf_dirs
        self.assertEquals(self.ctr._generate_run_commands(TEST_INFO_FILE),
                          [GTF_RUN_CMD])

    @mock.patch.object(cli_t.CLITranslator, '_find_test_by_module_name')
    @mock.patch.object(cli_t.CLITranslator, '_find_test_by_class_name')
    @mock.patch.object(cli_t.CLITranslator, '_find_test_by_module_and_class')
    @mock.patch.object(cli_t.CLITranslator, '_find_test_by_integration_name')
    def test_get_test_info(self, mock_findbyint, mock_findbymc,
                           mock_findbyclass, mock_findbymodule):
        """Test _find_test method."""
        ctr = cli_t.CLITranslator(results_dir=TEST_INFO_DIR)
        mock_findbymodule.return_value = MODULE_INFO
        mock_findbyclass.return_value = CLASS_INFO
        mock_findbymc.return_value = CLASS_INFO
        mock_findbyint.return_value = INT_INFO
        refs = [REF_TYPE.INTEGRATION, REF_TYPE.MODULE, REF_TYPE.CLASS]
        self.assertStrictEqual(ctr._get_test_info(INT_NAME, refs), INT_INFO)
        mock_findbyint.return_value = GTF_INT_INFO
        self.assertStrictEqual(ctr._get_test_info(GTF_INT_NAME, refs),
                               GTF_INT_INFO)
        mock_findbyint.return_value = None
        self.assertStrictEqual(ctr._get_test_info(MODULE_NAME, refs),
                               MODULE_INFO)
        mock_findbymodule.return_value = None
        self.assertStrictEqual(ctr._get_test_info(CLASS_NAME, refs), CLASS_INFO)
        mock_findbyclass.return_value = None
        self.assertIsNone(ctr._get_test_info(CLASS_NAME, refs))
        refs = [REF_TYPE.MODULE_CLASS]
        self.assertStrictEqual(ctr._get_test_info(MODULE_CLASS, refs),
                               CLASS_INFO)

    @mock.patch.object(cli_t.CLITranslator, '_get_targets_from_xml',
                       side_effect=targetsfromxml_side_effect)
    @mock.patch.object(cli_t.CLITranslator, '_get_test_info',
                       side_effect=findtest_side_effect)
    @mock.patch.object(cli_t.CLITranslator, '_create_test_info_file',
                       return_value=TEST_INFO_FILE)
    def test_translate(self, _filepath, _info, _xml):
        """Test translate method."""
        targets, run_cmds = self.ctr.translate([MODULE_NAME, CLASS_NAME])
        self.assertStrictEqual(targets, TARGETS)
        self.assertStrictEqual(run_cmds, [RUN_CMD])
        targets, run_cmds = self.ctr.translate([CLASS_NAME])
        self.assertStrictEqual(targets, TARGETS)
        self.assertStrictEqual(run_cmds, [RUN_CMD])
        targets, run_cmds = self.ctr.translate([MODULE_CLASS])
        self.assertStrictEqual(targets, TARGETS)
        self.assertStrictEqual(run_cmds, [RUN_CMD])
        targets, run_cmds = self.ctr.translate([INT_NAME])
        self.assertStrictEqual(targets, INT_TARGETS)
        self.assertStrictEqual(run_cmds, [RUN_CMD])
        # Internal
        self.ctr.gtf_dirs = self._gtf_dirs
        targets, run_cmds = self.ctr.translate([MODULE_NAME, CLASS_NAME])
        self.assertStrictEqual(targets, GTF_TARGETS)
        self.assertStrictEqual(run_cmds, [GTF_RUN_CMD])
        targets, run_cmds = self.ctr.translate([GTF_INT_NAME])
        self.assertStrictEqual(targets, GTF_INT_TARGETS)
        self.assertStrictEqual(run_cmds, [GTF_RUN_CMD])
        self.assertRaises(cli_t.NoTestFoundError, self.ctr.translate,
                          ['NonExistentClassOrModule'])

if __name__ == '__main__':
    unittest.main()
