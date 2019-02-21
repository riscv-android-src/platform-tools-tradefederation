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

# Only use this in interactive mode.
[[ ! $- =~ 'i' ]] && return 0

# Not support completion on Darwin since the bash version of it
# is too old to fully support useful built-in commands/functions
# such as compopt, _get_comp_words_by_ref and __ltrim_colon_completions.
[[ "$(uname -s)" == "Darwin" ]] && return 0

# Use Py2 as the default interpreter. This script is aiming for being
# compatible with both Py2 and Py3.
PYTHON=
if [ -x "$(which python2)" ]; then
    PYTHON=$(which python2)
elif [ -x "$(which python3)" ]; then
    PYTHON=$(which python3)
else
    PYTHON="/usr/bin/env python"
fi

# Get testable module names from module_info.json.
# Will return null if module_info.json doesn't exist.
# TODO: move the python code into an appropriate module which
# 1. Doesn't have py2/3 compatibility issue(e.g. urllib vs urllib2)
# 2. Doesn't import too many atest modules that affect user experience.
fetch_testable_modules() {
    [ -z $ANDROID_PRODUCT_OUT ] && { exit 0; }
    $PYTHON - << END
import hashlib
import json
import os
import pickle
import sys

modules = set()
module_info = os.path.join(os.environ["ANDROID_PRODUCT_OUT"] ,"module-info.json")

def get_serialised_filename(mod_info):
    """Determine the serialised filename used for reading testable modules.

    mod_info: the path of module-info.json.

    Returns: a path string hashed with md5 of module-info.json.
            /dev/shm/atest_e89e37a2e8e45be71567520b8579ffb8 (Linux)
            /tmp/atest_e89e37a2e8e45be71567520b8579ffb8     (MacOSX)
    """
    serial_filename = "/tmp/atest_" if sys.platform == "darwin" else "/dev/shm/atest_"
    with open(mod_info, 'r') as mod_info_obj:
        serial_filename += hashlib.md5(mod_info_obj.read().encode('utf-8')).hexdigest()
    return serial_filename

def create_json_data(mod_info):
    with open(mod_info, 'r') as mod_info_obj:
        return json.load(mod_info_obj)

def create_serialised_file(serial_file):
    # TODO: logic below will be abandoned and utilise test_finder_utils.py
    # after aosp/736172 merged (b/112904944).
    '''
    Testable module names can be found by fulfilling both conditions:
    1. module_name == value['module_name']
    2. test_config has value OR auto_test_config has value
    '''
    for module_name, value in create_json_data(module_info).items():
        if module_name != value.get("module_name", ""):
            continue
        elif value.get("auto_test_config") or value.get("test_config"):
            modules.add(module_name)
    print("\n".join(modules))
    with open(serial_file, 'wb') as serial_file_obj:
        pickle.dump(modules, serial_file_obj, protocol=2)

if os.path.isfile(module_info):
    latest_serial_file = get_serialised_filename(module_info)
    # When module-info.json changes, recreate a serialisation file.
    if not os.path.exists(latest_serial_file):
        create_serialised_file(latest_serial_file)
    else:
        with open(latest_serial_file, 'rb') as serial_file_obj:
            print("\n".join(pickle.load(serial_file_obj)))
else:
    print("")
END
}

# This function invoke get_args() and return each item
# of the list for tab completion candidates.
fetch_atest_args() {
    [ -z $ANDROID_BUILD_TOP ] && { exit 0; }
    $PYTHON - << END
import os
import sys

atest_dir = os.path.join(os.environ['ANDROID_BUILD_TOP'], 'tools/tradefederation/core/atest')
sys.path.append(atest_dir)

import atest_arg_parser

parser = atest_arg_parser.AtestArgParser()
parser.add_atest_args()
print("\n".join(parser.get_args()))
END
}

# This function returns devices recognised by adb.
fetch_adb_devices() {
    while read dev; do echo $dev | awk '{print $1}'; done < <(adb devices | egrep -v "^List|^$"||true)
}

# This function returns all paths contain TEST_MAPPING.
fetch_test_mapping_files() {
    find -maxdepth 5 -type f -name TEST_MAPPING |sed 's/^.\///g'| xargs dirname 2>/dev/null
}

# The main tab completion function.
_atest() {
    local cur prev
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"
    _get_comp_words_by_ref -n : cur prev || true

    case "$cur" in
        -*)
            COMPREPLY=($(compgen -W "$(fetch_atest_args)" -- $cur))
            ;;
        */*)
            ;;
        *)
            local candidate_args=$(ls; fetch_testable_modules)
            COMPREPLY=($(compgen -W "$candidate_args" -- $cur))
            ;;
    esac

    case "$prev" in
        --serial|-s)
            local adb_devices="$(fetch_adb_devices)"
            if [ -n "$adb_devices" ]; then
                COMPREPLY=($(compgen -W "$(fetch_adb_devices)" -- $cur))
            else
                # Don't complete files/dirs when there'is no devices.
                compopt -o nospace
                COMPREPLY=("")
            fi ;;
        --generate-baseline|--generate-new-metrics)
            COMPREPLY=(5) ;;
        --test-mapping|-p)
            local mapping_files="$(fetch_test_mapping_files)"
            if [ -n "$mapping_files" ]; then
                COMPREPLY=($(compgen -W "$mapping_files" -- $cur))
            else
                # Don't complete files/dirs when TEST_MAPPING wasn't found.
                compopt -o nospace
                COMPREPLY=("")
            fi ;;
    esac
    __ltrim_colon_completions "$cur" "$prev" || true
    return 0
}

# Complete file/dir name first by using option "nosort".
# BASH version <= 4.3 doesn't have nosort option.
# Note that nosort has no effect for zsh.
comp_options="-o default -o nosort"
complete -F _atest $comp_options atest 2>/dev/null || \
complete -F _atest -o default atest
