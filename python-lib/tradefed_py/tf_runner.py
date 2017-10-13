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
import json
import time
import traceback
import unittest
from unittest.util import strclass
from unittest.signals import registerResult

# Tags that Tradefed can understand in SubprocessResultParser to get results
_CLASSNAME_TAG = 'className'
_METHOD_NAME_TAG = 'testName'
_START_TIME_TAG = 'start_time'
_END_TIME_TAG = 'end_time'
_TRACE_TAG = 'trace'
_TEST_COUNT_TAG = 'testCount'
_REASON_TAG = 'reason'
_TIME_TAG = 'time'

class TextTestResult(unittest.TextTestResult):
    """ Class for callbacks based on test state"""

    def _getClassName(self, test):
        return strclass(test.__class__)

    def _getMethodName(self, test):
        return test._testMethodName

    def startTestRun(self, count):
        resp = {_TEST_COUNT_TAG: count, 'runName': 'python-tradefed'}
        self.stream.write('TEST_RUN_STARTED %s\n' % json.dumps(resp))
        super(TextTestResult, self).startTestRun()

    def startTest(self, test):
        resp = {_START_TIME_TAG: time.time(), _CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test)}
        self.stream.write('TEST_STARTED %s\n' % json.dumps(resp))
        super(TextTestResult, self).startTest(test)

    def addSuccess(self, test):
        resp = {_END_TIME_TAG: time.time(), _CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test)}
        self.stream.write('TEST_ENDED %s\n' % json.dumps(resp))
        super(TextTestResult, self).addSuccess(test)

    def addFailure(self, test, err):
        resp = {_CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test), _TRACE_TAG: '\n'.join(traceback.format_exception(*err))}
        self.stream.write('TEST_FAILED %s\n' % json.dumps(resp))
        resp = {_END_TIME_TAG: time.time(), _CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test)}
        self.stream.write('TEST_ENDED %s\n' % json.dumps(resp))
        super(TextTestResult, self).addFailure(test, err)

    def addSkip(self, test, reason):
        resp = {_CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test)}
        self.stream.write('TEST_IGNORED %s\n' % json.dumps(resp))
        resp = {_END_TIME_TAG: time.time(), _CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test)}
        self.stream.write('TEST_ENDED %s\n' % json.dumps(resp))
        super(TextTestResult, self).addSkip(test, reason)

    def addExpectedFailure(self, test, err):
        resp = {_CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test), _TRACE_TAG: '\n'.join(traceback.format_exception(*err))}
        self.stream.write('TEST_ASSUMPTION_FAILURE %s\n' % json.dumps(resp))
        resp = {_END_TIME_TAG: time.time(), _CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test)}
        self.stream.write('TEST_ENDED %s\n' % json.dumps(resp))
        super(TextTestResult, self).addExpectedFailure(test, err)

    def addUnexpectedSuccess(self, test):
        resp = {_CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test), _TRACE_TAG: 'Unexpected success'}
        self.stream.write('TEST_ASSUMPTION_FAILURE %s\n' % json.dumps(resp))
        resp = {_END_TIME_TAG: time.time(), _CLASSNAME_TAG: self._getClassName(test), _METHOD_NAME_TAG: self._getMethodName(test)}
        self.stream.write('TEST_ENDED %s\n' % json.dumps(resp))
        super(TextTestResult, self).addUnexpectedSuccess(test)

    def addError(self, test, err):
        resp = {_REASON_TAG: str(err)}
        self.stream.write('TEST_RUN_FAILED %s\n' % json.dumps(resp))
        super(TextTestResult, self).addError(test, err)

    def stopTestRun(self, elapsedTime):
        resp = {_TIME_TAG: elapsedTime}
        self.stream.write('TEST_RUN_ENDED %s\n' % json.dumps(resp))
        super(TextTestResult, self).stopTestRun()

class TextTestRunner(unittest.TextTestRunner):
    """ Class runner that ensure the callbacks order"""

    def run(self, test):
        """ Run the given test case or test suite. Copied from unittest to replace the startTestRun
        callback"""
        result = self._makeResult()
        result.failfast = self.failfast
        result.buffer = self.buffer
        registerResult(result)

        startTime = time.time()
        startTestRun = getattr(result, 'startTestRun', None)
        if startTestRun is not None:
            startTestRun(test.countTestCases())
        try:
            test(result)
        finally:
            stopTestRun = getattr(result, 'stopTestRun', None)
            if stopTestRun is not None:
                stopTestRun(time.time() - startTime)
            else:
                result.printErrors()
        stopTime = time.time()
        timeTaken = stopTime - startTime
        if hasattr(result, 'separator2'):
            self.stream.writeln(result.separator2)
        run = result.testsRun
        self.stream.writeln('Ran %d test%s in %.3fs' %
                            (run, run != 1 and 's' or '', timeTaken))
        self.stream.writeln()

        expectedFails = unexpectedSuccesses = skipped = 0
        try:
            results = map(len, (result.expectedFailures,
                                result.unexpectedSuccesses,
                                result.skipped))
            expectedFails, unexpectedSuccesses, skipped = results
        except AttributeError:
            pass
        infos = []
        if not result.wasSuccessful():
            self.stream.write('FAILED')
            failed, errored = map(len, (result.failures, result.errors))
            if failed:
                infos.append('failures=%d' % failed)
            if errored:
                infos.append('errors=%d' % errored)
        else:
            self.stream.write('OK')
        if skipped:
            infos.append('skipped=%d' % skipped)
        if expectedFails:
            infos.append('expected failures=%d' % expectedFails)
        if unexpectedSuccesses:
            infos.append('unexpected successes=%d' % unexpectedSuccesses)
        if infos:
            self.stream.writeln(' (%s)' % (', '.join(infos),))
        else:
            self.stream.write('\n')
        return result
