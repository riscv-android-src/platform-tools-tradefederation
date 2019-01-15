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
Metrics class.
"""

import constants
import metrics_base

class AtestStartEvent(metrics_base.MetricsBase):
    """
    Create Atest start event and send to clearcut.

    Usage:
        metrics.AtestStartEvent(
            command_line='example_atest_command',
            test_references=['example_test_reference'],
            cwd='example/working/dir',
            os='example_os')
    """
    _EVENT_NAME = 'atest_start_event'
    command_line = constants.INTERNAL
    test_references = constants.INTERNAL
    cwd = constants.INTERNAL
    os = constants.INTERNAL

class AtestExitEvent(metrics_base.MetricsBase):
    """
    Create Atest exit event and send to clearcut.

    Usage:
        metrics.AtestExitEvent(
            duration=metrics_utils.convert_duration(end-start),
            exit_code=0,
            stacktrace='some_trace',
            logs='some_logs')
    """
    _EVENT_NAME = 'atest_exit_event'
    duration = constants.EXTERNAL
    exit_code = constants.EXTERNAL
    stacktrace = constants.INTERNAL
    logs = constants.INTERNAL
