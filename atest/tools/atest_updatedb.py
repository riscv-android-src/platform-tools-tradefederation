#!/usr/bin/env python
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
Atest updatedb functions.
"""

from __future__ import print_function

import logging
import os
import platform
import shutil
import subprocess

AND_HOSTOUT_DIR = os.getenv('ANDROID_HOST_OUT', '')
MAC_UPDB_SRC = os.path.join(os.path.dirname(__file__), 'updatedb_darwin.sh')
MAC_UPDB_DST = os.path.join(AND_HOSTOUT_DIR, 'bin')
UPDATEDB = 'updatedb'

# updatedb does not support ".*" so below are excluded explicitly.
_PRUNENAMES = ['.abc', '.appveyor', '.azure-pipelines',
               '.bazelci', '.buildscript',
               '.ci', '.circleci',
               '.conan',
               '.externalToolBuilders',
               '.git', '.github', '.google', '.gradle',
               '.idea', '.intermediates',
               '.kokoro',
               '.mvn',
               '.prebuilt_info', '.private', '__pycache__',
               '.repo',
               '.semaphore', '.settings',
               '.static', '.svn',
               '.test', '.travis', '.tx',
               '.vscode']
_CACHE = 'locate.database'

def _install_updatedb():
    """Install a customized updatedb for MacOS."""
    if platform.system() == 'Darwin':
        if not os.path.isdir(MAC_UPDB_DST):
            os.makedirs(MAC_UPDB_DST)
        shutil.copy2(MAC_UPDB_SRC, os.path.join(MAC_UPDB_DST, UPDATEDB))
        os.chmod(os.path.join(MAC_UPDB_DST, UPDATEDB), 0755)


def run_updatedb(**kwargs):
    """Run updatedb and generate cache in $ANDROID_HOST_OUT/locate.database

    Args:
        search_root: The path of the search root(-U).
        prunepaths: A list of paths unwanted to be searched(-e).
        prunenames: A list of dirname that won't be cached(-n).
        output_cache: The filename of the updatedb cache(-o).

    Returns:
        Boolean of the status of updatedb execution, True if update successfully,
        False otherwise.
    """
    repo_root = os.getenv('ANDROID_BUILD_TOP', '')
    search_root = kwargs.get('search_root', repo_root)
    prunepaths = kwargs.get('prunepaths', os.path.join(search_root, 'out'))
    prunenames = kwargs.get('prunenames', ' '.join(_PRUNENAMES))
    output_cache = kwargs.get('output_cache',
                              os.path.join(AND_HOSTOUT_DIR, _CACHE))
    if not os.path.exists(os.path.dirname(output_cache)):
        os.makedirs(os.path.dirname(output_cache))
    updatedb_cmd = [UPDATEDB, '-l0']
    updatedb_cmd.append('-U%s' % search_root)
    updatedb_cmd.append('-e%s' % prunepaths)
    updatedb_cmd.append('-n%s' % prunenames)
    updatedb_cmd.append('-o%s' % output_cache)
    try:
        _install_updatedb()
    except IOError as e:
        logging.error('Error installing updatedb: %s', e)
        return False
    print('Running updatedb for locate...')
    try:
        full_env_vars = os.environ.copy()
        logging.debug('Executing: %s', updatedb_cmd)
        subprocess.check_call(updatedb_cmd, stderr=subprocess.STDOUT,
                              env=full_env_vars)
        return True
    except subprocess.CalledProcessError as err:
        logging.error('Error executing: %s', updatedb_cmd)
        if err.output:
            logging.error(err.output)
        return False

if __name__ == '__main__':
    run_updatedb()
