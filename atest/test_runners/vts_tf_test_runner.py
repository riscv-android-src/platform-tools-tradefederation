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
VTS Tradefed test runner class.
"""

import copy

# pylint: disable=import-error
import atest_tf_test_runner


class VtsTradefedTestRunner(atest_tf_test_runner.AtestTradefedTestRunner):
    """TradeFed Test Runner class."""
    NAME = 'VtsTradefedTestRunner'
    EXECUTABLE = 'vts-tradefed'
    _RUN_CMD = ('{exe} run commandAndExit vts-staging-default -m {test} {args}')
    _BUILD_REQ = set()
    _DEFAULT_ARGS = ['--skip-all-system-status-check',
                     '--skip-preconditions',
                     '--primary-abi-only']

    def __init__(self, results_dir):
        """Init stuff for vts tradefed runner class."""
        super(VtsTradefedTestRunner, self).__init__(results_dir)
        self.run_cmd_dict = {'exe': self.EXECUTABLE,
                             'test': '',
                             'args': ' '.join(self._DEFAULT_ARGS)}

    def run_tests(self, test_infos, _extra_args):
        """Run the list of test_infos.

        Args:
            test_infos: List of TestInfo.
            _extra_args: Dict of extra args to add to test run.
                         (currently unused)
        """
        run_cmds = self._generate_run_commands(test_infos)
        for run_cmd in run_cmds:
            super(VtsTradefedTestRunner, self).run(run_cmd)

    # pylint: disable=arguments-differ
    def _generate_run_commands(self, test_infos):
        """Generate a list of run commands from TestInfos.

        Args:
            test_infos: List of TestInfo tests to run.

        Returns:
            A List of strings that contains the vts-tradefed run command.
        """
        cmds = []
        for test_info in test_infos:
            cmd_dict = copy.deepcopy(self.run_cmd_dict)
            cmd_dict['test'] = test_info.module_name
            cmds.append(self._RUN_CMD.format(**cmd_dict))
        return cmds
