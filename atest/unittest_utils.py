#!/usr/bin/env python
#
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

"""Utility functions for unit tests."""


def assert_strict_equal(test_class, first, second):
    """Check for strict equality and strict equality of nametuple elements.

    assertEqual considers types equal to their subtypes, but we want to
    not consider set() and frozenset() equal for testing.
    """
    test_class.assertEqual(first, second)
    # allow byte and unicode string equality.
    if not (isinstance(first, basestring) and
            isinstance(second, basestring)):
        test_class.assertIsInstance(first, type(second))
        test_class.assertIsInstance(second, type(first))
    # Recursively check elements of namedtuples for strict equals.
    if isinstance(first, tuple) and hasattr(first, '_fields'):
        # pylint: disable=invalid-name
        for f in first._fields:
            assert_strict_equal(test_class, getattr(first, f),
                                getattr(second, f))
