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
Test Suite Finder class.
"""

import logging

# pylint: disable=import-error
import constants
import test_finder_base
import test_info
from test_runners import test_suite_test_runner


# TODO: Change TEST_SUITE to SUITE_PLAN and the relative strings as well
# on the next CL.
class TestSuiteFinder(test_finder_base.TestFinderBase):
    """Test suite finder class."""
    NAME = 'TEST_SUITE'
    _TEST_SUITE_TEST_RUNNER = test_suite_test_runner.TestSuiteTestRunner.NAME

    def __init__(self, module_info=None):
        super(TestSuiteFinder, self).__init__()
        self.mod_info = module_info

    def find_test_by_suite_name(self, suite_name):
        """Find the test for the given suite name.

        Strategy:
            suite_name: cts --> Use cts-tradefed

        Args:
            suite_name: A string of suite name.

        Returns:
            A populated TestInfo namedtuple if suite_name matches
            a suite in constants.TEST_SUITE_NAMES, else None.
        """
        # TODO: suite plan such as "atest cts-common" will be supported
        # on the next CL.
        if suite_name in constants.TEST_SUITE_NAMES:
            logging.debug('Finding test by suite: %s', suite_name)
            return test_info.TestInfo(
                test_name=suite_name,
                test_runner=self._TEST_SUITE_TEST_RUNNER,
                build_targets=set([suite_name]))
        return None
