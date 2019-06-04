# Copyright 2019, The Android Open Source Project
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
Classes for bug events history
"""

import datetime
import json
import os

_META_FILE = os.path.join(os.path.expanduser('~'),
                          '.config', 'asuite', 'atest_history.json')
_DETECT_OPTION_FILTER = ['-v', '--verbose']
_DETECTED_SUCCESS = 1
_DETECTED_FAIL = 0

class BugDetector(object):
    """Class for handling if a bug is detected by comparing test history."""

    def __init__(self, argv, exit_code, history_file=None):
        """BugDetector constructor

        Args:
            argv: A list of arguments.
            exit_code: An integer of exit code.
            history_file: A string of a given history file path.
        """
        self.detect_key = self.get_detect_key(argv)
        self.exit_code = exit_code
        self.file = history_file if history_file else _META_FILE
        self.history = self.get_history()
        self.caught_result = self.detect_bug_caught()
        self.update_history()

    def get_detect_key(self, argv):
        """Get the key for history searching.

        1. remove '-v' in argv to argv_no_verbose
        2. sort the argv_no_verbose

        Args:
            argv: A list of arguments.

        Returns:
            A string of ordered command line.
        """
        argv_without_option = [x for x in argv if x not in _DETECT_OPTION_FILTER]
        argv_without_option.sort()
        return ' '.join(argv_without_option)

    def get_history(self):
        """Get a history object from a history file.

        e.g.
        {
            "SystemUITests:.ScrimControllerTest":{
                "latest_exit_code": 5, "updated_at": "2019-01-26T15:33:08.305026"},
            "--host hello_world_test ":{
                "latest_exit_code": 0, "updated_at": "2019-02-26T15:33:08.305026"},
        }

        Returns:
            An object of loading from a history.
        """
        if os.path.exists(self.file):
            with open(self.file) as json_file:
                return json.load(json_file)
        return {}

    def detect_bug_caught(self):
        """Detection of catching bugs.

        When latest_exit_code and current exit_code are different, treat it
        as a bug caught.

        Returns:
            A integer of detecting result, e.g.
            1: success
            0: fail
        """
        if not self.history:
            return _DETECTED_FAIL
        latest = self.history.get(self.detect_key, {})
        if latest.get('latest_exit_code', self.exit_code) == self.exit_code:
            return _DETECTED_FAIL
        return _DETECTED_SUCCESS

    def update_history(self):
        """Update the history file."""
        latest_bug = {
            self.detect_key: {
                'latest_exit_code': self.exit_code,
                'updated_at': datetime.datetime.now().isoformat()
            }
        }
        self.history.update(latest_bug)
        with open(self.file, 'w') as outfile:
            json.dump(self.history, outfile)
