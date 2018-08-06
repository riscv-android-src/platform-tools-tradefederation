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
TestInfo class.
"""

from collections import namedtuple


TestFilterBase = namedtuple('TestFilter', ['class_name', 'methods'])


class TestInfo(object):
    """Information needed to identify and run a test."""

    # pylint: disable=too-many-arguments
    def __init__(self, test_name, test_runner, build_targets, data=None, suite=None):
        """Init for TestInfo.

        Args:
            test_name: String of test name.
            test_runner: String of test runner.
            build_targets: Set of build targets.
            data: Dict of data for test runners to use.
            suite: Suite for test runners to use.
        """
        self.test_name = test_name
        self.test_runner = test_runner
        self.build_targets = build_targets
        self.data = data if data else {}
        self.suite = suite

    def __str__(self):
        return ('test_name:%s - test_runner:%s - build_targets:%s - data:%s'
                ' - suite:%s' % (self.test_name, self.test_runner,
                                 self.build_targets, self.data, self.suite))


class TestFilter(TestFilterBase):
    """Information needed to filter a test in Tradefed"""

    def to_set_of_tf_strings(self):
        """Return TestFilter as set of strings in TradeFed filter format."""
        if self.methods:
            return {'%s#%s' % (self.class_name, m) for m in self.methods}
        return {self.class_name}
