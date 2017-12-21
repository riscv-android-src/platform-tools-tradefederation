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

"""
Aggregates test runners, groups tests by test runners and kicks off tests.
"""

import itertools

from test_runners import atest_tf_test_runner

_TEST_RUNNERS = {
    atest_tf_test_runner.AtestTradefedTestRunner.NAME: atest_tf_test_runner.AtestTradefedTestRunner,
}

class UnknownTestRunnerError(Exception):
    """Raised when an unknown test runner is specified."""


def _get_test_runners():
    """Returns the test runners.

    If external test runners are defined outside atest, they can be try-except
    imported into here.

    Returns:
        Dict of test runner name to test runner class.
    """
    test_runners_dict = _TEST_RUNNERS
    # Example import of external test runner:
    # try:
    #     import ext_tr
    #     test_runners_dict[ext_tr.tr_class.NAME] = ext_tr.tr_class
    # except ImportError:
    #     pass
    return test_runners_dict


def _group_tests_by_test_runners(test_infos):
    """Group the test_infos by test runners

    Args:
        test_infos: List of TestInfo.

    Returns:
        List of tuples (test runner, tests).
    """
    tests_by_test_runner = []
    test_runner_dict = _get_test_runners()
    key = lambda x: x.test_runner
    sorted_test_infos = sorted(list(test_infos), key=key)
    for test_runner, tests in itertools.groupby(sorted_test_infos, key):
        # groupby returns a grouper object, we want to operate on a list.
        tests = list(tests)
        test_runner_class = test_runner_dict.get(test_runner, None)
        if test_runner_class is None:
            raise UnknownTestRunnerError('Unknown Test Runner %s' % test_runner)
        tests_by_test_runner.append((test_runner_class, tests))
    return tests_by_test_runner


def get_test_runner_reqs(test_infos):
    """Returns the requirements for all test runners specified in the tests.

    Args:
        test_infos: List of TestInfo.

    Returns:
        Set of build targets required by the test runners.
    """
    dummy_result_dir = ''
    test_runner_build_req = set()
    for test_runner, _ in _group_tests_by_test_runners(test_infos):
        test_runner_build_req |= test_runner(
            dummy_result_dir).get_test_runner_build_reqs()
    return test_runner_build_req


def run_all_tests(results_dir, test_infos, extra_args):
    """Run the given tests.

    Args:
        test_infos: List of TestInfo.
        extra_args: Dict of extra args for test runners to use.
    """
    for test_runner, tests in _group_tests_by_test_runners(test_infos):
        test_runner(results_dir).run_tests(tests, extra_args)
