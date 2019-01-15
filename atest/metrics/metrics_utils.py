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
Utility functions for metrics.
"""

import time

import metrics


def convert_duration(diff_time_sec):
    """Compute duration from time difference.

    A Duration represents a signed, fixed-length span of time represented
    as a count of seconds and fractions of seconds at nanosecond
    resolution.

    Args:
        dur_time_sec: The time in seconds as a floating point number.

    Returns:
        A dict of Duration.
    """
    seconds = long(diff_time_sec)
    nanos = int((diff_time_sec - seconds)*10**9)
    return {'seconds': seconds, 'nanos': nanos}

def send_exit_event(start_time, exit_code, stacktrace='', logs=''):
    """log exit event and flush all events to clearcut.
    Args:
        start_time: Start time in seconds.
        exit_code: An integer of exit code.
        stacktrace: A string of stacktrace.
        logs: A string of logs.
    """
    clearcut = metrics.AtestExitEvent(
        duration=convert_duration(time.time()-start_time),
        exit_code=exit_code,
        stacktrace=stacktrace,
        logs=logs)
    # pylint: disable=no-member
    clearcut.flush_events()
