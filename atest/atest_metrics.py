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
        data = {'grouping_key': str(get_grouping_key()),
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
    meta_file = os.path.join(os.environ[constants.ANDROID_BUILD_TOP],
                             'tools/tradefederation/core/atest', constants.META_FILE)
    if os.path.isfile(meta_file):
        with open(meta_file) as f:
            try:
                return uuid.UUID(f.read(), version=4)
            except ValueError:
                logging.debug('malformed group_key in file, rewriting')
    key = uuid.uuid4()
    with open(meta_file, 'w+') as f:
        f.write(str(key))
    return key
