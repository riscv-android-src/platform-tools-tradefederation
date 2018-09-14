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
import json
import os
import sys

modules = set()
module_info = os.path.join(os.environ["ANDROID_PRODUCT_OUT"] ,"module-info.json")

if os.path.isfile(module_info):
    json_data = json.load(open(module_info, 'r'))

    '''
    Testable module names can be found via either condition:
    1. auto_test_config == True
    2. AndroidTest.xml in the "path"
    '''
    for module_name, value in json_data.items():
        if value['auto_test_config']:
            modules.add(module_name)
        else:
           for path in value['path']:
               test_xml = os.path.join(os.environ["ANDROID_BUILD_TOP"], path, "AndroidTest.xml")
               if os.path.isfile(test_xml):
                   modules.add(module_name)
                   break

    for module in modules:
        print(module)

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
for arg in parser.get_args():
    print(arg)
END
}

# The main tab completion function.
_atest() {
    local current_word
    COMPREPLY=()
    current_word="${COMP_WORDS[COMP_CWORD]}"

    case "$current_word" in
        -*)
            COMPREPLY=($(compgen -W "$(fetch_atest_args)" -- $current_word))
            ;;
        *)
            local candidate_args=$(ls; fetch_testable_modules)
            COMPREPLY=($(compgen -W "$candidate_args" -- $current_word))
            ;;
    esac
    return 0
}

complete -F _atest -o default atest
