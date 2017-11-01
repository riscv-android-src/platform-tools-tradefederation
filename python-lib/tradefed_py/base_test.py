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

import android_device
import unittest

class _TradefedTestClass(unittest.TestCase):
    """ A base test class to extends to receive a device object for testing in python

        All tests should extends this class to be properly supported by Tradefed.
    """

    def setUpDevice(self, serial):
        """ Setter method that will allow the test to receive the device object

        Args:
            serial: The serial of the device allocated for the test.
        """
        self.serial = serial
        self.android_device = android_device.AndroidTestDevice(serial)