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
from collections import deque
from datetime import timedelta
import errno
import json
import logging
import os
import re
import signal
import socket
import subprocess
import sys

# pylint: disable=import-error
import atest_utils
import constants
from test_finders import test_info
from test_runners import test_runner_base

OLD_OUTPUT_ENV_VAR = 'ATEST_OLD_OUTPUT'
POLL_FREQ_SECS = 10
SOCKET_HOST = '127.0.0.1'
SOCKET_QUEUE_MAX = 1
SOCKET_BUFFER = 4096
# Socket Events of form FIRST_EVENT {JSON_DATA}\nSECOND_EVENT {JSON_DATA}
# EVENT_RE has groups for the name and the data. "." does not match \n.
EVENT_RE = re.compile(r'^(?P<event_name>[A-Z_]+) (?P<json_data>{.*})(?:\n|$)')
EVENT_NAMES = {'module_started': 'TEST_MODULE_STARTED',
               'module_ended': 'TEST_MODULE_ENDED',
               'run_started': 'TEST_RUN_STARTED',
               'run_ended': 'TEST_RUN_ENDED',
               # Next three are test-level events
               'test_started': 'TEST_STARTED',
               'test_failed': 'TEST_FAILED',
               'test_ended': 'TEST_ENDED',
               # Last two failures are runner-level, not test-level.
               # Invocation failure is broader than run failure.
               'run_failed': 'TEST_RUN_FAILED',
               'invocation_failed': 'INVOCATION_FAILED',
               'test_ignored': 'TEST_IGNORED'}
EVENT_PAIRS = {EVENT_NAMES['module_started']: EVENT_NAMES['module_ended'],
               EVENT_NAMES['run_started']: EVENT_NAMES['run_ended'],
               EVENT_NAMES['test_started']: EVENT_NAMES['test_ended']}
START_EVENTS = list(EVENT_PAIRS.keys())
END_EVENTS = list(EVENT_PAIRS.values())
TEST_NAME_TEMPLATE = '%s#%s'
EXEC_DEPENDENCIES = ('adb', 'aapt')

CONNECTION_STATE = {
    'current_test': None,
    'last_failed': None,
    'last_ignored': None,
    'current_group': None,
    'current_group_total': None,
    'test_count': 0,
    'test_start_time': None
}

# time in millisecond.
ONE_SECOND = 1000
ONE_MINUTE = 60000
ONE_HOUR = 3600000

class TradeFedExitError(Exception):
    """Raised when TradeFed exists before test run has finished."""

TRADEFED_EXIT_MSG = ('TradeFed subprocess exited early with exit code=%s.')

EVENTS_NOT_BALANCED = ('Error: Saw %s Start event and %s End event. These '
                       'should be equal!')


class AtestTradefedTestRunner(test_runner_base.TestRunnerBase):
    """TradeFed Test Runner class."""
    NAME = 'AtestTradefedTestRunner'
    EXECUTABLE = 'atest_tradefed.sh'
    _TF_TEMPLATE = 'template/local_min'
    _RUN_CMD = ('{exe} {template} --template:map '
                'test=atest {args}')
    _BUILD_REQ = {'tradefed-core'}

    def __init__(self, results_dir, module_info=None, **kwargs):
        """Init stuff for base class."""
        super(AtestTradefedTestRunner, self).__init__(results_dir, **kwargs)
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

        Returns:
            0 if tests succeed, non-zero otherwise.
        """
        if os.getenv(OLD_OUTPUT_ENV_VAR):
            return self.run_tests_raw(test_infos, extra_args, reporter)
        return self.run_tests_pretty(test_infos, extra_args, reporter)

    def run_tests_raw(self, test_infos, extra_args, reporter):
        """Run the list of test_infos. See base class for more.

        Args:
            test_infos: A list of TestInfos.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.

        Returns:
            0 if tests succeed, non-zero otherwise.
        """
        iterations = self._generate_iterations(extra_args)
        reporter.register_unsupported_runner(self.NAME)

        ret_code = constants.EXIT_CODE_SUCCESS
        for _ in range(iterations):
            run_cmds = self.generate_run_commands(test_infos, extra_args)
            subproc = self.run(run_cmds[0], output_to_stdout=True)
            ret_code |= self.wait_for_subprocess(subproc)
        return ret_code

    # pylint: disable=broad-except
    # pylint: disable=too-many-locals
    def run_tests_pretty(self, test_infos, extra_args, reporter):
        """Run the list of test_infos. See base class for more.

        Args:
            test_infos: A list of TestInfos.
            extra_args: Dict of extra args to add to test run.
            reporter: An instance of result_report.ResultReporter.

        Returns:
            0 if tests succeed, non-zero otherwise.
        """
        iterations = self._generate_iterations(extra_args)
        ret_code = constants.EXIT_CODE_SUCCESS
        for _ in range(iterations):
            server = self._start_socket_server()
            run_cmds = self.generate_run_commands(test_infos, extra_args,
                                                  server.getsockname()[1])
            subproc = self.run(run_cmds[0], output_to_stdout=self.is_verbose)
            try:
                signal.signal(signal.SIGINT, self._signal_passer(subproc))
                conn, addr = self._exec_with_tf_polling(server.accept, subproc)
                logging.debug('Accepted connection from %s', addr)
                self._process_connection(conn, reporter)
            except Exception as error:
                # exc_info=1 tells logging to log the stacktrace
                logging.debug('Caught exception:', exc_info=1)
                # Remember our current exception scope, before new try block
                # Python3 will make this easier, the error itself stores
                # the scope via error.__traceback__ and it provides a
                # "raise from error" pattern.
                # https://docs.python.org/3.5/reference/simple_stmts.html#raise
                exc_type, exc_msg, traceback_obj = sys.exc_info()
                # If atest crashes, try to kill TF subproc group as well.
                try:
                    logging.debug('Killing TF subproc: %s', subproc.pid)
                    os.killpg(os.getpgid(subproc.pid), signal.SIGINT)
                except OSError:
                    # this wipes our previous stack context, which is why
                    # we have to save it above.
                    logging.debug('Subproc already terminated, skipping')
                finally:
                    if self.test_log_file:
                        with open(self.test_log_file.name, 'r') as f:
                            intro_msg = "Unexpected Tradefed Issue. Raw Output:"
                            print(atest_utils.colorize(intro_msg, constants.RED))
                            print(f.read())
                    # Ignore socket.recv() raising due to ctrl-c
                    if not error.args or error.args[0] != errno.EINTR:
                        raise exc_type, exc_msg, traceback_obj
            finally:
                server.close()
                subproc.wait()
                ret_code |= subproc.returncode
        return ret_code

    def _start_socket_server(self):
        """Start a TCP server."""
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        # Port 0 lets the OS pick an open port between 1024 and 65535.
        server.bind((SOCKET_HOST, 0))
        server.listen(SOCKET_QUEUE_MAX)
        server.settimeout(POLL_FREQ_SECS)
        logging.debug('Socket server started on port %s',
                      server.getsockname()[1])
        return server

    def _exec_with_tf_polling(self, socket_func, tf_subproc):
        """Check for TF subproc exit during blocking socket func.

        Args:
            socket_func: A blocking socket function, e.g. recv(), accept().
            tf_subproc: The tradefed subprocess to poll.
        """
        while True:
            try:
                return socket_func()
            except socket.timeout:
                logging.debug('Polling TF subproc for early exit.')
                if tf_subproc.poll() is not None:
                    logging.debug('TF subproc exited early')
                    raise TradeFedExitError(TRADEFED_EXIT_MSG
                                            % tf_subproc.returncode)

    def _process_connection(self, conn, reporter):
        """Process a socket connection from TradeFed.

        Expect data of form EVENT_NAME {JSON_DATA}.  Multiple events will be
        \n deliminated.  Need to buffer data in case data exceeds socket
        buffer.

        Args:
            conn: A socket connection.
            reporter: A result_report.ResultReporter
        """
        connection_state = CONNECTION_STATE.copy()
        conn.settimeout(None)
        buf = ''
        event_stack = deque()
        while True:
            logging.debug('Waiting to receive data')
            data = conn.recv(SOCKET_BUFFER)
            logging.debug('received: %s', data)
            if data:
                buf += data
                while True:
                    match = EVENT_RE.match(buf)
                    if match:
                        try:
                            event_data = json.loads(match.group('json_data'))
                        except ValueError:
                            # Json incomplete, wait for more data.
                            break
                        event_name = match.group('event_name')
                        buf = buf[match.end():]
                        self._process_event(event_name, event_data, reporter,
                                            connection_state, event_stack)
                        continue
                    break
            else:
                # client sent empty string, so no more data.
                conn.close()
                break

    def _check_events_are_balanced(self, event_name, reporter, state,
                                   event_stack):
        """Check Start events and End events. They should be balanced.

        If they are not balanced, print the error message in
        state['last_failed'], then raise TradeFedExitError.

        Args:
            event_name: A string of the event name.
            reporter: A ResultReporter instance.
            state: A dict of the state of the test run.
            event_stack: A collections.deque(stack) of the events for pairing
                         START and END events.
        Raises:
            TradeFedExitError if we doesn't have a balance of START/END events.
        """
        start_event = event_stack.pop() if event_stack else None
        if not start_event or EVENT_PAIRS[start_event] != event_name:
            # Here bubble up the failed trace in the situation having
            # TEST_FAILED but never receiving TEST_ENDED.
            if state['last_failed'] and (start_event ==
                                         EVENT_NAMES['test_started']):
                reporter.process_test_result(test_runner_base.TestResult(
                    runner_name=self.NAME,
                    group_name=state['current_group'],
                    test_name=state['last_failed']['name'],
                    status=test_runner_base.FAILED_STATUS,
                    details=state['last_failed']['trace'],
                    test_count=state['test_count'],
                    test_time='',
                    runner_total=None,
                    group_total=state['current_group_total']))
            raise TradeFedExitError(EVENTS_NOT_BALANCED % (start_event,
                                                           event_name))

    def _print_duration(self, duration):
        """Convert duration from ms to 3h2m43.034s.

        Args:
            duration: millisecond

        Returns:
            string in h:m:s, m:s, s or millis, depends on the duration.
        """
        delta = timedelta(milliseconds=duration)
        timestamp = str(delta).split(':') # hh:mm:microsec

        if duration < ONE_SECOND:
            return "({}ms)".format(duration)
        elif duration < ONE_MINUTE:
            return "({:.3f}s)".format(float(timestamp[2]))
        elif duration < ONE_HOUR:
            return "({0}m{1:.3f}s)".format(timestamp[1], float(timestamp[2]))
        return "({0}h{1}m{2:.3f}s)".format(timestamp[0],
                                           timestamp[1], float(timestamp[2]))

    # pylint: disable=too-many-branches
    def _process_event(self, event_name, event_data, reporter, state,
                       event_stack):
        """Process the events of the test run and call reporter with results.

        Args:
            event_name: A string of the event name.
            event_data: A dict of event data.
            reporter: A ResultReporter instance.
            state: A dict of the state of the test run.
            event_stack: A collections.deque(stack) of the events for pairing
                         START and END events.
        """
        logging.debug('Processing %s %s', event_name, event_data)
        if event_name in START_EVENTS:
            event_stack.append(event_name)
        elif event_name in END_EVENTS:
            self._check_events_are_balanced(event_name, reporter, state,
                                            event_stack)
        if event_name == EVENT_NAMES['module_started']:
            state['current_group'] = event_data['moduleName']
            state['last_failed'] = None
            state['current_test'] = None
        elif event_name == EVENT_NAMES['run_started']:
            # Technically there can be more than one run per module.
            state['current_group_total'] = event_data['testCount']
            state['test_count'] = 0
            state['last_failed'] = None
            state['current_test'] = None
        elif event_name == EVENT_NAMES['test_started']:
            name = TEST_NAME_TEMPLATE % (event_data['className'],
                                         event_data['testName'])
            state['current_test'] = name
            state['test_count'] += 1
            state['test_start_time'] = event_data['start_time']
        elif event_name == EVENT_NAMES['test_failed']:
            state['last_failed'] = {'name': TEST_NAME_TEMPLATE % (
                event_data['className'],
                event_data['testName']),
                                    'trace': event_data['trace']}
        elif event_name == EVENT_NAMES['test_ignored']:
            name = TEST_NAME_TEMPLATE % (event_data['className'],
                                         event_data['testName'])
            state['last_ignored'] = name
        elif event_name == EVENT_NAMES['run_failed']:
            # Module and Test Run probably started, but failure occurred.
            reporter.process_test_result(test_runner_base.TestResult(
                runner_name=self.NAME,
                group_name=state['current_group'],
                test_name=state['current_test'],
                status=test_runner_base.ERROR_STATUS,
                details=event_data['reason'],
                test_count=state['test_count'],
                test_time='',
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
                test_count=state['test_count'],
                test_time='',
                runner_total=None,
                group_total=state['current_group_total']))
        elif event_name == EVENT_NAMES['test_ended']:
            name = TEST_NAME_TEMPLATE % (event_data['className'],
                                         event_data['testName'])
            if state['test_start_time']:
                test_time = self._print_duration(event_data['end_time'] -
                                                 state['test_start_time'])
            else:
                test_time = ''
            if state['last_failed'] and name == state['last_failed']['name']:
                status = test_runner_base.FAILED_STATUS
                trace = state['last_failed']['trace']
                state['last_failed'] = None
            elif state['last_ignored'] and name == state['last_ignored']:
                status = test_runner_base.IGNORED_STATUS
                state['last_ignored'] = None
                trace = None
            else:
                status = test_runner_base.PASSED_STATUS
                trace = None
            reporter.process_test_result(test_runner_base.TestResult(
                runner_name=self.NAME,
                group_name=state['current_group'],
                test_name=name,
                status=status,
                details=trace,
                test_count=state['test_count'],
                test_time=test_time,
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
    def _is_missing_exec(executable):
        """Check if system build executable is available.

        Args:
            executable: Executable we are checking for.
        Returns:
            True if executable is missing, False otherwise.
        """
        try:
            output = subprocess.check_output(['which', executable])
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
        # Always add ATest's own TF target.
        build_req.add(constants.ATEST_TF_MODULE)
        # Add adb if we can't find it.
        for executable in EXEC_DEPENDENCIES:
            if self._is_missing_exec(executable):
                build_req.add(executable)
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
            if constants.HOST == arg:
                args_to_append.append('-n')
                args_to_append.append('--prioritize-host-config')
                args_to_append.append('--skip-host-arch-check')
                continue
            if constants.CUSTOM_ARGS == arg:
                # We might need to sanitize it prior to appending but for now
                # let's just treat it like a simple arg to pass on through.
                args_to_append.extend(extra_args[arg])
                continue
            if constants.ALL_ABI == arg:
                args_to_append.append('--all-abi')
                continue
            if constants.DRY_RUN == arg:
                continue
            if constants.INSTANT == arg:
                args_to_append.append('--enable-parameterized-modules')
                args_to_append.append('--module-parameter')
                args_to_append.append('instant_app')
                continue
            args_not_supported.append(arg)
        return args_to_append, args_not_supported

    def _generate_metrics_folder(self, extra_args):
        """Generate metrics folder."""
        metrics_folder = ''
        if extra_args.get(constants.PRE_PATCH_ITERATIONS):
            metrics_folder = os.path.join(self.results_dir, 'baseline-metrics')
        elif extra_args.get(constants.POST_PATCH_ITERATIONS):
            metrics_folder = os.path.join(self.results_dir, 'new-metrics')
        return metrics_folder

    def _generate_iterations(self, extra_args):
        """Generate iterations."""
        iterations = 1
        if extra_args.get(constants.PRE_PATCH_ITERATIONS):
            iterations = extra_args.pop(constants.PRE_PATCH_ITERATIONS)
        elif extra_args.get(constants.POST_PATCH_ITERATIONS):
            iterations = extra_args.pop(constants.POST_PATCH_ITERATIONS)
        return iterations

    def generate_run_commands(self, test_infos, extra_args, port=None):
        """Generate a single run command from TestInfos.

        Args:
            test_infos: A set of TestInfo instances.
            extra_args: A Dict of extra args to append.
            port: Optional. An int of the port number to send events to. If
                  None, then subprocess reporter in TF won't try to connect.

        Returns:
            A list that contains the string of atest tradefed run command.
            Only one command is returned.
        """
        args = self._create_test_args(test_infos)
        metrics_folder = self._generate_metrics_folder(extra_args)

        # Create a copy of args as more args could be added to the list.
        test_args = list(args)
        if port:
            test_args.extend(['--subprocess-report-port', str(port)])
        if metrics_folder:
            test_args.extend(['--metrics-folder', metrics_folder])
            logging.info('Saved metrics in: %s', metrics_folder)
        log_level = 'VERBOSE' if self.is_verbose else 'WARN'
        test_args.extend(['--log-level', log_level])

        args_to_add, args_not_supported = self._parse_extra_args(extra_args)

        # TODO(b/122889707) Remove this after finding the root cause.
        env_serial = os.environ.get(constants.ANDROID_SERIAL)
        # Use the env variable ANDROID_SERIAL if it's set by user.
        if env_serial and '--serial' not in args_to_add:
            args_to_add.append("--serial")
            args_to_add.append(env_serial)

        test_args.extend(args_to_add)
        if args_not_supported:
            logging.info('%s does not support the following args %s',
                         self.EXECUTABLE, args_not_supported)

        test_args.extend(atest_utils.get_result_server_args())
        self.run_cmd_dict['args'] = ' '.join(test_args)
        return [self._RUN_CMD.format(**self.run_cmd_dict)]

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
        args = []
        if not test_infos:
            return []

        # Only need to check one TestInfo to determine if the tests are
        # configured in TEST_MAPPING.
        if test_infos[0].from_test_mapping:
            args.extend(constants.TEST_MAPPING_RESULT_SERVER_ARGS)
        test_infos = self._flatten_test_infos(test_infos)

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
                if constants.TF_INCLUDE_FILTER_OPTION == option[0]:
                    suite_filter = (
                        constants.TF_SUITE_FILTER_ARG_VALUE_FMT.format(
                            test_name=info.test_name, option_value=option[1]))
                    args.extend([constants.TF_INCLUDE_FILTER, suite_filter])
                elif constants.TF_EXCLUDE_FILTER_OPTION == option[0]:
                    suite_filter = (
                        constants.TF_SUITE_FILTER_ARG_VALUE_FMT.format(
                            test_name=info.test_name, option_value=option[1]))
                    args.extend([constants.TF_EXCLUDE_FILTER, suite_filter])
                else:
                    module_arg = (
                        constants.TF_MODULE_ARG_VALUE_FMT.format(
                            test_name=info.test_name, option_name=option[0],
                            option_value=option[1]))
                    args.extend([constants.TF_MODULE_ARG, module_arg])
        return args
