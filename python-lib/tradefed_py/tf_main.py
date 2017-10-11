#
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import sys
import os
import tf_runner
import unittest
from unittest import loader

class TradefedProgram(unittest.TestProgram):
    """ Main Runner Class that should be used to run the tests. This runner ensure that the
    reporting is compatible with Tradefed.

    """

    def runTests(self):
        # TODO: Extend the argument parsing to allow Tradefed to pass more
        # options
        if self.testRunner is None:
            self.testRunner = tf_runner.TextTestRunner(verbosity=self.verbosity, failfast=self.failfast, buffer=self.buffer, resultclass=tf_runner.TextTestResult)
        super(TradefedProgram, self).runTests()

main = TradefedProgram

def main_run():
    TradefedProgram(module=None)
