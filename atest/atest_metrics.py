#!/usr/bin/env python
#
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

"""Simple Metrics Functions"""

import json
import logging
import os
import urllib2
import uuid

import constants

JSON_HEADERS = {'Content-Type': 'application/json'}

#pylint: disable=broad-except
def log_start_event():
    """Log that atest started."""
    try:
        try:
            key = str(get_grouping_key())
        except Exception:
            key = constants.DUMMY_UUID
        data = {'grouping_key': key,
                'run_id': str(uuid.uuid4())}
        data = json.dumps(data)
        request = urllib2.Request(constants.METRICS_URL, data=data, headers=JSON_HEADERS)
        response = urllib2.urlopen(request, timeout=constants.METRICS_TIMEOUT)
        content = response.read()
        if content != constants.METRICS_RESPONSE:
            raise Exception('Unexpected metrics response: %s' % content)
    except Exception as e:
        logging.debug('Exception sending metrics: %s', e)

def get_grouping_key():
    """Get grouping key. Returns UUID."""
    if os.path.isfile(constants.META_FILE):
        with open(constants.META_FILE) as f:
            try:
                return uuid.UUID(f.read(), version=4)
            except ValueError:
                logging.debug('malformed group_key in file, rewriting')
    # TODO: Delete get_old_key() on 11/17/2018
    key = get_old_key() or uuid.uuid4()
    dir_path = os.path.dirname(constants.META_FILE)
    if os.path.isfile(dir_path):
        os.remove(dir_path)
    try:
        os.makedirs(dir_path)
    except OSError as e:
        if not os.path.isdir(dir_path):
            raise e
    with open(constants.META_FILE, 'w+') as f:
        f.write(str(key))
    return key

def get_old_key():
    """Get key from old meta data file if exists, else return None."""
    old_file = os.path.join(os.environ[constants.ANDROID_BUILD_TOP],
                            'tools/tradefederation/core/atest', '.metadata')
    key = None
    if os.path.isfile(old_file):
        with open(old_file) as f:
            try:
                key = uuid.UUID(f.read(), version=4)
            except ValueError:
                logging.debug('error reading old key')
        os.remove(old_file)
    return key
