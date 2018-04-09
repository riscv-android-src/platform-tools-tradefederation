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
Result Reporter

The result reporter formats and prints test results.

----

Example Output for command to run following tests:
CtsAnimationTestCases:EvaluatorTest, ScreenDecorWindowTests#testFlagChange and
HelloWorldTests

Running Tests ...

CtsAnimationTestCases (7 Tests)
------------------------------
android.animation.cts.EvaluatorTest#testRectEvaluator: PASSED
android.animation.cts.EvaluatorTest#testIntArrayEvaluator: PASSED
android.animation.cts.EvaluatorTest#testIntEvaluator: PASSED
android.animation.cts.EvaluatorTest#testFloatArrayEvaluator: PASSED
android.animation.cts.EvaluatorTest#testPointFEvaluator: PASSED
android.animation.cts.EvaluatorTest#testArgbEvaluator: PASSED
android.animation.cts.EvaluatorTest#testFloatEvaluator: PASSED

FrameworksServicesTests (1 Test)
-------------------------------
com.android.server.wm.ScreenDecorWindowTests#testFlagChange: PASSED

HelloWorldTests
---------------
ERROR EXECUTING TEST RUN: Instrumentation run failed due to 'Process crashed.'

SUMMARY
-------
CtsAnimationTestCases: Passed: 7, Failed: 0
FrameworksServicesTests: Passed: 1, Failed: 0
HelloWorldTests: Passed: 0, Failed: 0
(Errors occurred during above test run. Counts may be inaccurate.)

Total: 8, Passed: 8, Failed: 0
WARNING: Errors occurred during test run. Counts may be inaccurate.

TODO(b/79699032): Update reporter to add color and implement final formatting.
"""

from __future__ import print_function
from collections import OrderedDict

from test_runners import test_runner_base

UNSUPPORTED_FLAG = 'UNSUPPORTED_RUNNER'


class RunStat(object):
    """Class for storing stats of a test run."""

    def __init__(self, passed=0, failed=0, run_errors=False):
        """Initialize a new instance of RunStat class.

        Args:
            passed: An int of the number of passing tests.
            failed: An int of the number of failed tests.
            run_errors: A boolean if there were run errors
        """
        # TODO(b/109822985): Track group and run estimated totals for updating
        # summary line
        self.passed = passed
        self.failed = failed
        # Run errors are not for particular tests, they are runner errors.
        self.run_errors = run_errors

    @property
    def total(self):
        """Getter for total tests actually ran. Accessed via self.total"""
        return self.passed + self.failed


class ResultReporter(object):
    """Result Reporter class.

    As each test is run, the test runner will call self.process_test_result()
    with a TestResult namedtuple that contains the following information:
    - runner_name:   Name of the test runner
    - group_name:    Name of the test group if any.
                     In Tradefed that's the Module name.
    - test_name:     Name of the test.
                     In Tradefed that's qualified.class#Method
    - status:        The strings FAILED or PASSED.
    - stacktrace:    The stacktrace if the test failed.
    - group_total:   The total tests scheduled to be run for a group.
                     In Tradefed this is provided when the Module starts.
    - runner_total:  The total tests scheduled to be run for the runner.
                     In Tradefed this is not available so is None.

    The Result Reporter will print the results of this test and then update
    its stats state.

    Test stats are stored in the following structure:
    - self.run_stats: Is RunStat instance containing stats for the overall run.
                      This include pass/fail counts across ALL test runners.

    - self.runners:  Is of the form: {RunnerName: {GroupName: RunStat Instance}}
                     Where {} is an ordered dict.

                     The stats instance contains stats for each test group.
                     If the runner doesn't support groups, then the group
                     name will be None.

    For example this could be a state of ResultReporter:

    run_stats: RunStat(passed:10, failed:5)
    runners: {'AtestTradefedTestRunner':
                            {'Module1': RunStat(passed:1, failed:1),
                             'Module2': RunStat(passed:0, failed:4)},
              'RobolectricTestRunner': {None: RunStat(passed:5, failed:0)},
              'VtsTradefedTestRunner': {'Module1': RunStat(passed:4, failed:0)}}
    """

    def __init__(self):
        self.run_stats = RunStat()
        self.runners = OrderedDict()

    def process_test_result(self, test):
        """Given the results of a single test, update stats and print results.

        Args:
            test: A TestResult namedtuple.
        """
        if test.runner_name not in self.runners:
            self.runners[test.runner_name] = OrderedDict()
        if test.group_name not in self.runners[test.runner_name]:
            self.runners[test.runner_name][test.group_name] = RunStat()
            self._print_group_title(test)
        self._update_stats(test,
                           self.runners[test.runner_name][test.group_name])
        self._print_result(test)

    def register_unsupported_runner(self, runner_name):
        """Register an unsupported runner.

           Prints the following to the screen:

           RunnerName
           ----------
           This runner does not support normal results formatting.
           Below is the raw output of the test runner.

           RAW OUTPUT:
           <Raw Runner Output>

           Args:
              runner_name: A String of the test runner's name.
        """
        assert runner_name not in self.runners
        self.runners[runner_name] = UNSUPPORTED_FLAG
        print('\n', runner_name, '\n', '-' * len(runner_name), sep='')
        print('This runner does not support normal results formatting. Below '
              'is the raw output of the test runner.\n\nRAW OUTPUT:')

    def print_starting_text(self):
        """Print starting text for running tests."""
        print('Running Tests ...')

    def print_summary(self):
        """Print summary of all test runs."""
        print('\nSUMMARY')
        print('-------')
        for runner_name, groups in self.runners.items():
            if groups == UNSUPPORTED_FLAG:
                print(runner_name, 'Unsupported. See raw output above.')
                continue
            for group_name, stats in groups.items():
                name = group_name if group_name else runner_name
                print('%s: Passed: %s, Failed: %s' % (name, stats.passed,
                                                      stats.failed))
                if stats.run_errors:
                    print('(Errors occurred during above test run. '
                          'Counts may be inaccurate.)')
        print('\nTotal: %s, Passed: %s, Failed: %s' % (
            self.run_stats.total, self.run_stats.passed, self.run_stats.failed))
        if self.run_stats.run_errors:
            print('WARNING: Errors occurred during test run. '
                  'Counts may be inaccurate.')
        print()

    def _update_stats(self, test, group):
        """Given the results of a single test, update test run stats.

        Args:
            test: a TestResult namedtuple.
            group: a RunStat instance for a test group.
        """
        # TODO(109822985): Track group and run estimated totals for updating
        # summary line
        if test.status == test_runner_base.PASSED_STATUS:
            self.run_stats.passed += 1
            group.passed += 1
        elif test.status == test_runner_base.FAILED_STATUS:
            self.run_stats.failed += 1
            group.failed += 1
        elif test.status == test_runner_base.ERROR_STATUS:
            self.run_stats.run_errors = True
            group.run_errors = True

    def _print_group_title(self, test):
        """Print the title line for a test group.

        Test Group/Runner Name (## Total)
        ---------------------------------

        Args:
            test: A TestResult namedtuple.
        """
        title = test.group_name or test.runner_name
        total = ''
        if test.group_total:
            if test.group_total > 1:
                total = '(%s Tests)' % test.group_total
            else:
                total = '(%s Test)' % test.group_total
        underline = '-' * (len(title) + len(total))
        print('\n%s %s\n%s' % (title, total, underline))

    def _print_result(self, test):
        """Print the results of a single test.

           Looks like:
           fully.qualified.class#TestMethod: PASSED/FAILED

        Args:
            test: a TestResult namedtuple.
        """
        if test.test_name:
            print('%s: %s' % (test.test_name, test.status))
        if test.status == test_runner_base.FAILED_STATUS:
            print('\nSTACKTRACE:\n%s' % test.details)
        elif test.status == test_runner_base.ERROR_STATUS:
            print('ERROR EXECUTING TEST RUN:', test.details)
