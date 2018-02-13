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

"""
Unittest constants.

Unittest constants get their own file since they're used purely for testing and
should not be combined with constants_defaults as part of normal atest
operation. These constants are used commonly as test data so when updating a
constant, do so with care and run all unittests to make sure nothing breaks.
"""

import os

import constants
from test_finders import test_info
from test_runners import atest_tf_test_runner as atf_tr

ROOT = '/'
MODULE_DIR = 'foo/bar/jank'
MODULE2_DIR = 'foo/bar/hello'
MODULE_NAME = 'CtsJankDeviceTestCases'
MODULE2_NAME = 'HelloWorldTests'
CLASS_NAME = 'CtsDeviceJankUi'
FULL_CLASS_NAME = 'android.jank.cts.ui.CtsDeviceJankUi'
FIND_ONE = ROOT + 'foo/bar/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java\n'
FIND_TWO = ROOT + 'other/dir/test.java\n' + FIND_ONE
INT_NAME = 'example/reboot'
GTF_INT_NAME = 'some/gtf_int_test'
TEST_DATA_DIR = 'unittest_data'

INT_DIR = 'tf/contrib/res/config'
GTF_INT_DIR = 'gtf/core/res/config'
CONFIG_FILE = os.path.join(MODULE_DIR, constants.MODULE_CONFIG)
CONFIG2_FILE = os.path.join(MODULE2_DIR, constants.MODULE_CONFIG)
JSON_FILE = 'module-info.json'
MODULE_INFO_TARGET = '/out/%s' % JSON_FILE
MODULE_BUILD_TARGETS = {'tradefed-core', MODULE_INFO_TARGET,
                        'MODULES-IN-%s' % MODULE_DIR.replace('/', '-'),
                        'module-specific-target'}
MODULE_DATA = {constants.TI_REL_CONFIG: CONFIG_FILE,
               constants.TI_FILTER: frozenset()}
MODULE_INFO = test_info.TestInfo(MODULE_NAME,
                                 atf_tr.AtestTradefedTestRunner.NAME,
                                 MODULE_BUILD_TARGETS,
                                 MODULE_DATA)
CLASS_FILTER = test_info.TestFilter(FULL_CLASS_NAME, frozenset())
CLASS_DATA = {constants.TI_REL_CONFIG: CONFIG_FILE,
              constants.TI_FILTER: frozenset([CLASS_FILTER])}
CLASS_BUILD_TARGETS = {'class-specific-target'}
CLASS_INFO = test_info.TestInfo(MODULE_NAME,
                                atf_tr.AtestTradefedTestRunner.NAME,
                                CLASS_BUILD_TARGETS,
                                CLASS_DATA)
MODULE_CLASS_COMBINED_BUILD_TARGETS = MODULE_BUILD_TARGETS | CLASS_BUILD_TARGETS
INT_CONFIG = os.path.join(INT_DIR, INT_NAME + '.xml')
GTF_INT_CONFIG = os.path.join(GTF_INT_DIR, GTF_INT_NAME + '.xml')
METHOD_NAME = 'method1'
METHOD_FILTER = test_info.TestFilter(FULL_CLASS_NAME, frozenset([METHOD_NAME]))
METHOD_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    data={constants.TI_FILTER: frozenset([METHOD_FILTER]),
          constants.TI_REL_CONFIG: CONFIG_FILE})
METHOD2_NAME = 'method2'
FLAT_METHOD_FILTER = test_info.TestFilter(
    FULL_CLASS_NAME, frozenset([METHOD_NAME, METHOD2_NAME]))
INT_INFO = test_info.TestInfo(INT_NAME,
                              atf_tr.AtestTradefedTestRunner.NAME,
                              set(),
                              data={constants.TI_REL_CONFIG: INT_CONFIG,
                                    constants.TI_FILTER: frozenset()})
GTF_INT_INFO = test_info.TestInfo(
    GTF_INT_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    set(),
    data={constants.TI_FILTER: frozenset(),
          constants.TI_REL_CONFIG: GTF_INT_CONFIG})
