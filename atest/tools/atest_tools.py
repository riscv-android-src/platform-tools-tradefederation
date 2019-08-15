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
Atest tool functions.
"""

from __future__ import print_function

import logging
import os
import pickle
import shutil
import subprocess
import sys

import constants
import module_info

MAC_UPDB_SRC = os.path.join(os.path.dirname(__file__), 'updatedb_darwin.sh')
MAC_UPDB_DST = os.path.join(os.getenv(constants.ANDROID_HOST_OUT, ''), 'bin')
UPDATEDB = 'updatedb'
LOCATE = 'locate'
SEARCH_TOP = os.getenv(constants.ANDROID_BUILD_TOP, '')
MACOSX = 'Darwin'
OSNAME = os.uname()[0]

# The list was generated by command:
# find `gettop` -type d -wholename `gettop`/out -prune  -o -type d -name '.*'
# -print | awk -F/ '{{print $NF}}'| sort -u
PRUNENAMES = ['.abc', '.appveyor', '.azure-pipelines',
              '.bazelci', '.buildscript', '.ci', '.circleci', '.conan',
              '.externalToolBuilders',
              '.git', '.github', '.github-ci', '.google', '.gradle',
              '.idea', '.intermediates',
              '.jenkins',
              '.kokoro',
              '.libs_cffi_backend',
              '.mvn',
              '.prebuilt_info', '.private', '__pycache__',
              '.repo',
              '.semaphore', '.settings', '.static', '.svn',
              '.test', '.travis', '.tx',
              '.vscode']
# Running locate + grep consumes tremendous amount of time in MacOS. Running it
# with a physical script file can increase the performance.
TMPRUN = '/tmp/._'

def _mkdir_when_inexists(dirname):
    if not os.path.isdir(dirname):
        os.makedirs(dirname)

def _install_updatedb():
    """Install a customized updatedb for MacOS and ensure it is executable."""
    _mkdir_when_inexists(MAC_UPDB_DST)
    _mkdir_when_inexists(constants.INDEX_DIR)
    if OSNAME == MACOSX:
        shutil.copy2(MAC_UPDB_SRC, os.path.join(MAC_UPDB_DST, UPDATEDB))
        os.chmod(os.path.join(MAC_UPDB_DST, UPDATEDB), 0755)

def has_indexes():
    """Detect if all index files are all available.

    Returns:
        True if indexes exist, False otherwise.
    """
    indexes = (constants.CLASS_INDEX, constants.QCLASS_INDEX,
               constants.PACKAGE_INDEX, constants.CC_CLASS_INDEX)
    for index in indexes:
        if not os.path.isfile(index):
            return False
    return True

def has_command(cmd):
    """Detect if the command is available in PATH.

    shutil.which('cmd') is only valid in Py3 so we need to customise it.

    Args:
        cmd: A string of the tested command.

    Returns:
        True if found, False otherwise."""
    paths = os.getenv('PATH', '').split(':')
    for path in paths:
        if os.path.isfile(os.path.join(path, cmd)):
            return True
    return False

def run_updatedb(search_root=SEARCH_TOP, output_cache=constants.LOCATE_CACHE,
                 **kwargs):
    """Run updatedb and generate cache in $ANDROID_HOST_OUT/indexes/mlocate.db

    Args:
        search_root: The path of the search root(-U).
        output_cache: The filename of the updatedb cache(-o).
        kwargs: (optional)
            prunepaths: A list of paths unwanted to be searched(-e).
            prunenames: A list of dirname that won't be cached(-n).
    """
    prunenames = kwargs.pop('prunenames', ' '.join(PRUNENAMES))
    prunepaths = kwargs.pop('prunepaths', os.path.join(search_root, 'out'))
    if kwargs:
        raise TypeError('Unexpected **kwargs: %r' % kwargs)
    updatedb_cmd = [UPDATEDB, '-l0']
    updatedb_cmd.append('-U%s' % search_root)
    updatedb_cmd.append('-e%s' % prunepaths)
    updatedb_cmd.append('-n%s' % prunenames)
    updatedb_cmd.append('-o%s' % output_cache)
    try:
        _install_updatedb()
    except IOError as e:
        logging.error('Error installing updatedb: %s', e)

    if not has_command(UPDATEDB):
        return
    logging.debug('Running updatedb... ')
    try:
        full_env_vars = os.environ.copy()
        logging.debug('Executing: %s', updatedb_cmd)
        subprocess.check_call(updatedb_cmd, stderr=subprocess.STDOUT,
                              env=full_env_vars)
    except (KeyboardInterrupt, SystemExit):
        logging.error('Process interrupted or failure.')
    except subprocess.CalledProcessError as err:
        logging.error('Error executing: %s', updatedb_cmd)
        if err.output:
            logging.error(err.output)

def _dump_index(dump_file, output, output_re, key, value):
    """Dump indexed data with pickle.

    Args:
        dump_file: A string of absolute path of the index file.
        output: A string generated by locate and grep.
        output_re: An regex which is used for grouping patterns.
        key: A string for dictionary key, e.g. classname, package, cc_class, etc.
        value: A set of path.

    The data structure will be like:
    {
      'Foo': {'/path/to/Foo.java', '/path2/to/Foo.kt'},
      'Boo': {'/path3/to/Boo.java'}
    }
    """
    _dict = {}
    with open(dump_file, 'wb') as cache_file:
        for entry in output.splitlines():
            match = output_re.match(entry)
            if match:
                _dict.setdefault(match.group(key), set()).add(match.group(value))
        try:
            pickle.dump(_dict, cache_file, protocol=2)
        except IOError:
            os.remove(dump_file)
            logging.error('Failed in dumping %s', dump_file)

def _get_cc_result(locatedb=None):
    """Search all testable cc/cpp and grep TEST(), TEST_F() or TEST_P().

    Return:
        A string object generated by subprocess.
    """
    if not locatedb:
        locatedb = constants.LOCATE_CACHE
    cc_grep_re = r'^\s*TEST(_P|_F)?\s*\([[:alnum:]]+,'
    if OSNAME == MACOSX:
        find_cmd = (r"locate -d {0} '*.cpp' '*.cc' | grep -i test "
                    "| xargs egrep -sH '{1}' || true")
    else:
        find_cmd = (r"locate -d {0} / | egrep -i '/*.test.*\.(cc|cpp)$' "
                    "| xargs egrep -sH '{1}' || true")
    find_cc_cmd = find_cmd.format(locatedb, cc_grep_re)
    logging.debug('Probing CC classes:\n %s', find_cc_cmd)
    return subprocess.check_output('echo \"%s\" > %s; sh %s'
                                   % (find_cc_cmd, TMPRUN, TMPRUN), shell=True)

def _get_java_result(locatedb=None):
    """Search all testable java/kt and grep package.

    Return:
        A string object generated by subprocess.
    """
    if not locatedb:
        locatedb = constants.LOCATE_CACHE
    package_grep_re = r'^\s*package\s+[a-z][[:alnum:]]+[^{]'
    if OSNAME == MACOSX:
        find_cmd = r"locate -d%s '*.java' '*.kt'|grep -i test" % locatedb
    else:
        find_cmd = r"locate -d%s / | egrep -i '/*.test.*\.(java|kt)$'" % locatedb
    find_java_cmd = find_cmd + '| xargs egrep -sH \'%s\' || true' % package_grep_re
    logging.debug('Probing Java classes:\n %s', find_java_cmd)
    return subprocess.check_output('echo \"%s\" > %s; sh %s'
                                   % (find_java_cmd, TMPRUN, TMPRUN), shell=True)

def _index_testable_modules(index):
    """Dump testable modules read by tab completion.

    Args:
        index: A string path of the index file.
    """
    logging.debug('indexing testable modules.')
    testable_modules = module_info.ModuleInfo().get_testable_modules()
    with open(index, 'wb') as cache:
        try:
            pickle.dump(testable_modules, cache, protocol=2)
        except IOError:
            os.remove(cache)
            logging.error('Failed in dumping %s', cache)

def _index_cc_classes(output, index):
    """Index Java classes.

    The data structure is like:
    {
      'FooTestCase': {'/path1/to/the/FooTestCase.java',
                      '/path2/to/the/FooTestCase.kt'}
    }

    Args:
        output: A string object generated by _get_cc_result().
        index: A string path of the index file.
    """
    logging.debug('indexing CC classes.')
    _dump_index(dump_file=index, output=output,
                output_re=constants.CC_OUTPUT_RE,
                key='test_name', value='file_path')

def _index_java_classes(output, index):
    """Index Java classes.
    The data structure is like:
    {
        'FooTestCase': {'/path1/to/the/FooTestCase.java',
                        '/path2/to/the/FooTestCase.kt'}
    }

    Args:
        output: A string object generated by _get_java_result().
        index: A string path of the index file.
    """
    logging.debug('indexing Java classes.')
    _dump_index(dump_file=index, output=output,
                output_re=constants.CLASS_OUTPUT_RE,
                key='class', value='java_path')

def _index_packages(output, index):
    """Index Java packages.
    The data structure is like:
    {
        'a.b.c.d': {'/path1/to/a/b/c/d/',
                    '/path2/to/a/b/c/d/'
    }

    Args:
        output: A string object generated by _get_java_result().
        index: A string path of the index file.
    """
    logging.debug('indexing packages.')
    _dump_index(dump_file=index,
                output=output, output_re=constants.PACKAGE_OUTPUT_RE,
                key='package', value='java_dir')

def _index_qualified_classes(output, index):
    """Index Fully Qualified Java Classes(FQCN).
    The data structure is like:
    {
        'a.b.c.d.FooTestCase': {'/path1/to/a/b/c/d/FooTestCase.java',
                                '/path2/to/a/b/c/d/FooTestCase.kt'}
    }

    Args:
        output: A string object generated by _get_java_result().
        index: A string path of the index file.
    """
    logging.debug('indexing qualified classes.')
    _dict = {}
    with open(index, 'wb') as cache_file:
        for entry in output.split('\n'):
            match = constants.QCLASS_OUTPUT_RE.match(entry)
            if match:
                fqcn = match.group('package') + '.' + match.group('class')
                _dict.setdefault(fqcn, set()).add(match.group('java_path'))
        try:
            pickle.dump(_dict, cache_file, protocol=2)
        except (KeyboardInterrupt, SystemExit):
            logging.error('Process interrupted or failure.')
            os.remove(index)
        except IOError:
            logging.error('Failed in dumping %s', index)

def index_targets(output_cache=constants.LOCATE_CACHE, **kwargs):
    """The entrypoint of indexing targets.

    Utilise mlocate database to index reference types of CLASS, CC_CLASS,
    PACKAGE and QUALIFIED_CLASS. Testable module for tab completion is also
    generated in this method.

    Args:
        output_cache: A file path of the updatedb cache(e.g. /path/to/mlocate.db).
        kwargs: (optional)
            class_index: A path string of the Java class index.
            qclass_index: A path string of the qualified class index.
            package_index: A path string of the package index.
            cc_class_index: A path string of the CC class index.
            module_index: A path string of the testable module index.
            integration_index: A path string of the integration index.
    """
    class_index = kwargs.pop('class_index', constants.CLASS_INDEX)
    qclass_index = kwargs.pop('qclass_index', constants.QCLASS_INDEX)
    package_index = kwargs.pop('package_index', constants.PACKAGE_INDEX)
    cc_class_index = kwargs.pop('cc_class_index', constants.CC_CLASS_INDEX)
    module_index = kwargs.pop('module_index', constants.MODULE_INDEX)
    # Uncomment below if we decide to support INTEGRATION.
    #integration_index = kwargs.pop('integration_index', constants.INT_INDEX)
    if kwargs:
        raise TypeError('Unexpected **kwargs: %r' % kwargs)

    # Step 0: generate mlocate database prior to indexing targets.
    run_updatedb(SEARCH_TOP, constants.LOCATE_CACHE)
    if not has_command(LOCATE):
        return
    # Step 1: generate output string for indexing targets.
    logging.debug('Indexing targets... ')
    cc_result = _get_cc_result(output_cache)
    java_result = _get_java_result(output_cache)
    # Step 2: index Java and CC classes.
    _index_cc_classes(cc_result, cc_class_index)
    _index_java_classes(java_result, class_index)
    _index_qualified_classes(java_result, qclass_index)
    _index_packages(java_result, package_index)
    _index_testable_modules(module_index)
    if os.path.isfile(TMPRUN):
        os.remove(TMPRUN)

if __name__ == '__main__':
    if not os.getenv(constants.ANDROID_HOST_OUT, ''):
        sys.exit()
    index_targets()
