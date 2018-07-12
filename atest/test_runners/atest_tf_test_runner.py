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
Atest Tradefed test runner class.
"""

from __future__ import print_function
import json
import logging
import os
import re
import signal
import socket
import subprocess

# pylint: disable=import-error
import atest_utils
import constants
from test_finders import test_info
import test_runner_base

PRETTY_RESULT_ENV_VAR = 'ATEST_PRETTY_RESULT'
SOCKET_HOST = '127.0.0.1'
SOCKET_QUEUE_MAX = 1
SOCKET_BUFFER_SIZE = 4096
EVENT_RE = re.compile(r'^(?P<event_name>[A-Z_]+) (?P<json_data>{.+)$')
EVENT_NAMES = {'module_started': 'TEST_MODULE_STARTED',
               'run_started': 'TEST_RUN_STARTED',
               # Next three are test-level events
               'test_started': 'TEST_STARTED',
               'test_failed': 'TEST_FAILED',
               'test_ended': 'TEST_ENDED',
               # Last two failures are runner-level, not test-level.
               # Invocation failure is broader than run failure.
               'run_failed': 'TEST_RUN_FAILED',
               'invocation_failed': 'INVOCATION_FAILED'}
TEST_NAME_TEMPLATE = '%s#%s'

CONNECTION_STATE = {
    'current_test': None,
    'last_failed': None,
    'current_group': None,
    'current_group_total': None
}


class AtestTradefedTestRunner(test_runner_base.TestRunnerBase):
    """TradeFed Test Runner class."""
    NAME = 'AtestTradefedTestRunner'
    EXECUTABLE = 'atest_tradefed.sh'
    _TF_TEMPLATE = 'template/local_min'
    _RUN_CMD = ('{exe} run commandAndExit {template} --template:map '
                'test=atest {args}')
    _BUILD_REQ = {'tradefed-core'}

    def __init__(self, results_dir, module_info=None, **kwargs):
        """Init stuff for base class."""
        super(AtestTradefedTestRunner, self).__init__(results_dir)
        self.module_info = module_info
        self.run_cmd_dict = {'exe': self.EXECUTABLE,
                             'template': self._TF_TEMPLATE,
                             'args': ''}
        self.is_verbose = logging.getLogger().isEnabledFor(logging.DEBUG)

    def run_tests(self, test_infos, extra_args, reporter):
        """Run the list of test_infos. See base class for more.

        Args:
            test_infos: A list of TestInfos.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.
        """
        if os.getenv(PRETTY_RESULT_ENV_VAR):
            self.run_tests_pretty(test_infos, extra_args, reporter)
        else:
            self.run_tests_raw(test_infos, extra_args, reporter)

    def run_tests_raw(self, test_infos, extra_args, reporter):
        """Run the list of test_infos. See base class for more.

        Args:
            test_infos: A list of TestInfos.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.
        """
        iterations = 1
        metrics_folder = ''
        if extra_args.get(constants.PRE_PATCH_ITERATIONS):
            iterations = extra_args.pop(constants.PRE_PATCH_ITERATIONS)
            metrics_folder = os.path.join(self.results_dir, 'baseline-metrics')
        elif extra_args.get(constants.POST_PATCH_ITERATIONS):
            iterations = extra_args.pop(constants.POST_PATCH_ITERATIONS)
            metrics_folder = os.path.join(self.results_dir, 'new-metrics')
        args = self._create_test_args(test_infos)

        reporter.register_unsupported_runner(self.NAME)

        for _ in range(iterations):
            run_cmd = self._generate_run_command(args, extra_args,
                                                 metrics_folder)
            subproc = self.run(run_cmd, output_to_stdout=True)
            try:
                signal.signal(signal.SIGINT, self._signal_passer(subproc))
                subproc.wait()
            except:
                # If atest crashes, kill TF subproc group as well.
                os.killpg(os.getpgid(subproc.pid), signal.SIGINT)
                raise

    def run_tests_pretty(self, test_infos, extra_args, reporter):
        """Run the list of test_infos. See base class for more.

        Args:
            test_infos: A list of TestInfos.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.
        """
        iterations = 1
        metrics_folder = ''
        if extra_args.get(constants.PRE_PATCH_ITERATIONS):
            iterations = extra_args.pop(constants.PRE_PATCH_ITERATIONS)
            metrics_folder = os.path.join(self.results_dir, 'baseline-metrics')
        elif extra_args.get(constants.POST_PATCH_ITERATIONS):
            iterations = extra_args.pop(constants.POST_PATCH_ITERATIONS)
            metrics_folder = os.path.join(self.results_dir, 'new-metrics')
        args = self._create_test_args(test_infos)

        for _ in range(iterations):
            server = self._start_socket_server()
            run_cmd = self._generate_run_command(args, extra_args,
                                                 metrics_folder,
                                                 server.getsockname()[1])
            subproc = self.run(run_cmd, output_to_stdout=self.is_verbose)
            try:
                signal.signal(signal.SIGINT, self._signal_passer(subproc))
                # server.accept() blocks until connection received
                conn, addr = server.accept()
                logging.debug('Accepted connection from %s', addr)
                self._process_connection(conn, reporter)
                if metrics_folder:
                    logging.info('Saved metrics in: %s', metrics_folder)
            except:
                # If atest crashes, kill TF subproc group as well.
                os.killpg(os.getpgid(subproc.pid), signal.SIGINT)
                raise
            finally:
                server.shutdown(socket.SHUT_RDWR)
                server.close()
                subproc.wait()

    def _signal_passer(self, proc):
        """Return the signal_handler func bound to proc.

        Args:
            proc: The tradefed subprocess.

        Returns: signal_handler function.
        """
        def signal_handler(_signal_number, _frame):
            """Pass SIGINT to proc.

            If user hits ctrl-c during atest run, the TradeFed subprocess
            won't stop unless we also send it a SIGINT. The TradeFed process
            is started in a process group, so this SIGINT is sufficient to
            kill all the child processes TradeFed spawns as well.
            """
            print('Ctrl-C received. Killing Tradefed subprocess group')
            os.killpg(os.getpgid(proc.pid), signal.SIGINT)
        return signal_handler

    def _start_socket_server(self):
        """Start a TCP server."""
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        # Port 0 lets the OS pick an open port between 1024 and 65535.
        server.bind((SOCKET_HOST, 0))
        server.listen(SOCKET_QUEUE_MAX)
        logging.debug('Socket server started on port %s',
                      server.getsockname()[1])
        return server

    def _process_connection(self, conn, reporter):
        """Process a socket connection from TradeFed.

        This involves chunking the data until we have a full EVENT msg. Then
        parsing the event message to see if a test has finished. If a test
        has finished then use the reporter to process that test result.

        Args:
            conn: A socket connection.
            reporter: A result_report.ResultReporter
        """
        event_name_for_chunk = None
        json_data_chunk = ''
        connection_state = CONNECTION_STATE.copy()
        while True:
            logging.debug('Waiting to receive data')
            data = conn.recv(SOCKET_BUFFER_SIZE)
            logging.debug('received: %s', data)
            if data:
                # Client Socket Reporter sends data in discrete "event" blocks
                # of the form "EVENT_NAME {JSON DATA}". So we don't need
                # to worry about getting more than EVENT_NAME {JSON DATA}, but
                # we need to chunk in case EVENT_NAME {JSON DATA} exceeds our
                # recv buffer and we only get part of it.
                match = EVENT_RE.match(data)
                if match:
                    event_name = match.group('event_name')
                    try:
                        event_data = json.loads(match.group('json_data'))
                    except ValueError:
                        # exceeded buffer, start chunking
                        event_name_for_chunk = match.group('event_name')
                        json_data_chunk = match.group('json_data')
                        continue
                else:
                    assert event_name_for_chunk
                    json_data_chunk += data
                    try:
                        event_data = json.loads(json_data_chunk)
                        json_data_chunk = ''
                        event_name = event_name_for_chunk
                        event_name_for_chunk = None
                    except ValueError:
                        # json data not complete, keep chunking
                        continue
                # Only reach here if have event_name and full event_data.
                self._process_event(event_name, event_data, reporter,
                                    connection_state)
            else:
                # client sent empty string, so no more data.
                conn.close()
                break

    def _process_event(self, event_name, event_data, reporter, state):
        """Process the events of the test run and call reporter with results.

        Args:
            event_name: A string of the event name.
            event_data: A dict of event data.
            reporter: A ResultReporter instance.
            state: A dict of the state of the test run.
        """
        if event_name == EVENT_NAMES['module_started']:
            state['current_group'] = event_data['moduleName']
            state['last_failed'] = None
            state['current_test'] = None
        elif event_name == EVENT_NAMES['run_started']:
            # Technically there can be more than one run per module.
            state['current_group_total'] = event_data['testCount']
            state['last_failed'] = None
            state['current_test'] = None
        elif event_name == EVENT_NAMES['test_started']:
            name = TEST_NAME_TEMPLATE % (event_data['className'],
                                         event_data['testName'])
            state['current_test'] = name
        elif event_name == EVENT_NAMES['test_failed']:
            state['last_failed'] = {'name': TEST_NAME_TEMPLATE % (
                event_data['className'],
                event_data['testName']),
                                    'trace': event_data['trace']}
        elif event_name == EVENT_NAMES['run_failed']:
            # Module and Test Run probably started, but failure occurred.
            reporter.process_test_result(test_runner_base.TestResult(
                runner_name=self.NAME,
                group_name=state['current_group'],
                test_name=state['current_test'],
                status=test_runner_base.ERROR_STATUS,
                details=event_data['reason'],
                runner_total=None,
                group_total=state['current_group_total']))
        elif event_name == EVENT_NAMES['invocation_failed']:
            # Broadest possible failure. May not even start the module/test run.
            reporter.process_test_result(test_runner_base.TestResult(
                runner_name=self.NAME,
                group_name=state['current_group'],
                test_name=state['current_test'],
                status=test_runner_base.ERROR_STATUS,
                details=event_data['cause'],
                runner_total=None,
                group_total=state['current_group_total']))
        elif event_name == EVENT_NAMES['test_ended']:
            name = TEST_NAME_TEMPLATE % (event_data['className'],
                                         event_data['testName'])
            if state['last_failed'] and name == state['last_failed']['name']:
                status = test_runner_base.FAILED_STATUS
                trace = state['last_failed']['trace']
            else:
                status = test_runner_base.PASSED_STATUS
                trace = None
            reporter.process_test_result(test_runner_base.TestResult(
                runner_name=self.NAME,
                group_name=state['current_group'],
                test_name=name,
                status=status,
                details=trace,
                runner_total=None,
                group_total=state['current_group_total']))

    def host_env_check(self):
        """Check that host env has everything we need.

        We actually can assume the host env is fine because we have the same
        requirements that atest has. Update this to check for android env vars
        if that changes.
        """
        pass

    @staticmethod
    def _is_missing_adb():
        """Check if system built adb is available.

        TF requires adb and we want to make sure we use the latest built adb
        (vs. system adb that might be too old).

        Returns:
            True if adb is missing, False otherwise.
        """
        try:
            output = subprocess.check_output(['which', 'adb'])
        except subprocess.CalledProcessError:
            return True
        # TODO: Check if there is a clever way to determine if system adb is
        # good enough.
        root_dir = os.environ.get(constants.ANDROID_BUILD_TOP)
        return os.path.commonprefix([output, root_dir]) != root_dir

    def get_test_runner_build_reqs(self):
        """Return the build requirements.

        Returns:
            Set of build targets.
        """
        build_req = self._BUILD_REQ
        # Use different base build requirements if google-tf is around.
        if self.module_info.is_module(constants.GTF_MODULE):
            build_req = {constants.GTF_TARGET}
        # Add adb if we can't find it.
        if self._is_missing_adb():
            build_req.add('adb')
        return build_req

    @staticmethod
    def _parse_extra_args(extra_args):
        """Convert the extra args into something tf can understand.

        Args:
            extra_args: Dict of args

        Returns:
            Tuple of args to append and args not supported.
        """
        args_to_append = []
        args_not_supported = []
        for arg in extra_args:
            if constants.WAIT_FOR_DEBUGGER == arg:
                args_to_append.append('--wait-for-debugger')
                continue
            if constants.DISABLE_INSTALL == arg:
                args_to_append.append('--disable-target-preparers')
                continue
            if constants.SERIAL == arg:
                args_to_append.append('--serial')
                args_to_append.append(extra_args[arg])
                continue
            if constants.DISABLE_TEARDOWN == arg:
                args_to_append.append('--disable-teardown')
                continue
            if constants.CUSTOM_ARGS == arg:
                # We might need to sanitize it prior to appending but for now
                # let's just treat it like a simple arg to pass on through.
                args_to_append.extend(extra_args[arg])
                continue
            if constants.ALL_ABI == arg:
                args_to_append.append('--all-abi')
                continue
            args_not_supported.append(arg)
        return args_to_append, args_not_supported

    def _generate_run_command(self, args, extra_args, metrics_folder,
                              port=None):
        """Generate a single run command from TestInfos.

        Args:
            args: A list of strings of TF arguments to run the tests.
            extra_args: A Dict of extra args to append.
            metrics_folder: A string of the filepath to put metrics.
            port: Optional. An int of the port number to send events to. If
                  None, then subprocess reporter in TF won't try to connect.

        Returns:
            A string that contains the atest tradefed run command.
        """
        # Create a copy of args as more args could be added to the list.
        test_args = list(args)
        if port:
            test_args.extend(['--subprocess-report-port', str(port)])
        if metrics_folder:
            test_args.extend(['--metrics-folder', metrics_folder])
        log_level = 'VERBOSE' if self.is_verbose else 'WARN'
        test_args.extend(['--log-level', log_level])

        args_to_add, args_not_supported = self._parse_extra_args(extra_args)
        test_args.extend(args_to_add)
        if args_not_supported:
            logging.info('%s does not support the following args %s',
                         self.EXECUTABLE, args_not_supported)

        test_args.extend(atest_utils.get_result_server_args())
        self.run_cmd_dict['args'] = ' '.join(test_args)
        return self._RUN_CMD.format(**self.run_cmd_dict)

    def _flatten_test_infos(self, test_infos):
        """Sort and group test_infos by module_name and sort and group filters
        by class name.

            Example of three test_infos in a set:
                Module1, {(classA, {})}
                Module1, {(classB, {Method1})}
                Module1, {(classB, {Method2}}
            Becomes a set with one element:
                Module1, {(ClassA, {}), (ClassB, {Method1, Method2})}
            Where:
                  Each line is a test_info namedtuple
                  {} = Frozenset
                  () = TestFilter namedtuple

        Args:
            test_infos: A set of TestInfo namedtuples.

        Returns:
            A set of TestInfos flattened.
        """
        results = set()
        key = lambda x: x.test_name
        for module, group in atest_utils.sort_and_group(test_infos, key):
            # module is a string, group is a generator of grouped TestInfos.
            # Module Test, so flatten test_infos:
            no_filters = False
            filters = set()
            test_runner = None
            build_targets = set()
            data = {}
            for test_info_i in group:
                # We can overwrite existing data since it'll mostly just
                # comprise of filters and relative configs.
                data.update(test_info_i.data)
                test_runner = test_info_i.test_runner
                build_targets |= test_info_i.build_targets
                test_filters = test_info_i.data.get(constants.TI_FILTER)
                if not test_filters or no_filters:
                    # test_info wants whole module run, so hardcode no filters.
                    no_filters = True
                    filters = set()
                    continue
                filters |= test_filters
            data[constants.TI_FILTER] = self._flatten_test_filters(filters)
            results.add(
                test_info.TestInfo(test_name=module,
                                   test_runner=test_runner,
                                   build_targets=build_targets,
                                   data=data))
        return results

    @staticmethod
    def _flatten_test_filters(filters):
        """Sort and group test_filters by class_name.

            Example of three test_filters in a frozenset:
                classA, {}
                classB, {Method1}
                classB, {Method2}
            Becomes a frozenset with these elements:
                classA, {}
                classB, {Method1, Method2}
            Where:
                Each line is a TestFilter namedtuple
                {} = Frozenset

        Args:
            filters: A frozenset of test_filters.

        Returns:
            A frozenset of test_filters flattened.
        """
        results = set()
        key = lambda x: x.class_name
        for class_name, group in atest_utils.sort_and_group(filters, key):
            # class_name is a string, group is a generator of TestFilters
            assert class_name is not None
            methods = set()
            for test_filter in group:
                if not test_filter.methods:
                    # Whole class should be run
                    methods = set()
                    break
                methods |= test_filter.methods
            results.add(test_info.TestFilter(class_name, frozenset(methods)))
        return frozenset(results)

    def _create_test_args(self, test_infos):
        """Compile TF command line args based on the given test infos.

        Args:
            test_infos: A set of TestInfo instances.

        Returns: A list of TF arguments to run the tests.
        """
        test_infos = self._flatten_test_infos(test_infos)
        args = []
        for info in test_infos:
            args.extend([constants.TF_INCLUDE_FILTER, info.test_name])
            filters = set()
            for test_filter in info.data.get(constants.TI_FILTER, []):
                filters.update(test_filter.to_set_of_tf_strings())
            for test_filter in filters:
                filter_arg = constants.TF_ATEST_INCLUDE_FILTER_VALUE_FMT.format(
                    test_name=info.test_name, test_filter=test_filter)
                args.extend([constants.TF_ATEST_INCLUDE_FILTER, filter_arg])
            for option in info.data.get(constants.TI_MODULE_ARG, []):
                module_arg = (
                    constants.TF_MODULE_ARG_VALUE_FMT.format(
                        test_name=info.test_name, option_name=option[0],
                        option_value=option[1]))
                args.extend([constants.TF_MODULE_ARG, module_arg])

        return args
