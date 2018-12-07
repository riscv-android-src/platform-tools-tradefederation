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
Base test runner class.

Class that other test runners will instantiate for test runners.
"""

import logging
import signal
import subprocess
import tempfile
import os
from collections import namedtuple

# pylint: disable=import-error
import atest_error

# TestResult contains information of individual tests during a test run.
TestResult = namedtuple('TestResult', ['runner_name', 'group_name',
                                       'test_name', 'status', 'details',
                                       'test_count', 'test_time',
                                       'runner_total', 'group_total'])
FAILED_STATUS = 'FAILED'
PASSED_STATUS = 'PASSED'
ERROR_STATUS = 'ERROR'

class TestRunnerBase(object):
    """Base Test Runner class."""
    NAME = ''
    EXECUTABLE = ''

    def __init__(self, results_dir, **kwargs):
        """Init stuff for base class."""
        self.results_dir = results_dir
        self.test_log_file = None
        if not self.NAME:
            raise atest_error.NoTestRunnerName('Class var NAME is not defined.')
        if not self.EXECUTABLE:
            raise atest_error.NoTestRunnerExecutable('Class var EXECUTABLE is '
                                                     'not defined.')
        if kwargs:
            logging.info('ignoring the following args: %s', kwargs)

    def run(self, cmd, output_to_stdout=False):
        """Shell out and execute command.

        Args:
            cmd: A string of the command to execute.
            output_to_stdout: A boolean. If False, the raw output of the run
                              command will not be seen in the terminal. This
                              is the default behavior, since the test_runner's
                              run_tests() method should use atest's
                              result reporter to print the test results.

                              Set to True to see the output of the cmd. This
                              would be appropriate for verbose runs.
        """
        if not output_to_stdout:
            self.test_log_file = tempfile.NamedTemporaryFile(mode='w',
                                                             dir=self.results_dir,
                                                             delete=True)
        logging.debug('Executing command: %s', cmd)
        return subprocess.Popen(cmd, preexec_fn=os.setsid, shell=True,
                                stderr=subprocess.STDOUT, stdout=self.test_log_file)

    def wait_for_subprocess(self, proc):
        """Check the process status. Interrupt the TF subporcess if user
        hits Ctrl-C.

        Args:
            proc: The tradefed subprocess.

        Returns:
            Return code of the subprocess for running tests.
        """
        try:
            logging.debug('Runner Name: %s, Process ID: %s', self.NAME, proc.pid)
            signal.signal(signal.SIGINT, self._signal_passer(proc))
            proc.wait()
            return proc.returncode
        except:
            # If atest crashes, kill TF subproc group as well.
            os.killpg(os.getpgid(proc.pid), signal.SIGINT)
            raise

    def _signal_passer(self, proc):
        """Return the signal_handler func bound to proc.

        Args:
            proc: The tradefed subprocess.

        Returns:
            signal_handler function.
        """
        def signal_handler(_signal_number, _frame):
            """Pass SIGINT to proc.

            If user hits ctrl-c during atest run, the TradeFed subprocess
            won't stop unless we also send it a SIGINT. The TradeFed process
            is started in a process group, so this SIGINT is sufficient to
            kill all the child processes TradeFed spawns as well.
            """
            logging.info('Ctrl-C received. Killing Tradefed subprocess group')
            os.killpg(os.getpgid(proc.pid), signal.SIGINT)
        return signal_handler

    def run_tests(self, test_infos, extra_args, reporter):
        """Run the list of test_infos.

        Should contain code for kicking off the test runs using
        test_runner_base.run(). Results should be processed and printed
        via the reporter passed in.

        Args:
            test_infos: List of TestInfo.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.
        """
        raise NotImplementedError

    def host_env_check(self):
        """Checks that host env has met requirements."""
        raise NotImplementedError

    def get_test_runner_build_reqs(self):
        """Returns a list of build targets required by the test runner."""
        raise NotImplementedError
