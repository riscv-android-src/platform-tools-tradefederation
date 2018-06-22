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

fetch_testable_modules() {
    local PYTHON
    if [ -x "$(which python3)" ]; then
        PYTHON=$(which python3)
    elif [ -x "$(which python2)" ]; then
        PYTHON=$(which python2)
    else
        PYTHON="/usr/bin/env python"
    fi
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

_atest() {
    local current_word
    local options operands all_options
    COMPREPLY=()
    current_word="${COMP_WORDS[COMP_CWORD]}"
    options=" -b --build \
              -i --install \
              -t --test \
              -s --serial \
              -d --disable-teardown \
              -m --rebuild-module-info \
              -w --wait-for-debugger \
              -v --verbose \
              -h --help"
    operands="--generate-baseline \
              --generate-new-metrics \
              --detect-regression \
              --"
    all_options=$(echo $options $operands)
    # TODO: if the current_word is a path/filename, don't invoke
    # fetch_testable_modules.
    case "$current_word" in
        -*)
            COMPREPLY=($(compgen -W "$all_options" -- $current_word))
            ;;
        *)
            COMPREPLY=($(compgen -W "$(fetch_testable_modules)" -- $current_word))
            ;;
    esac
    return 0
}

complete -F _atest -o default atest
