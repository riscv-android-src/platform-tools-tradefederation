/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.testtype.suite.params;

import com.android.tradefed.testtype.suite.params.multiuser.RunOnSecondaryUserParameterHandler;
import com.android.tradefed.testtype.suite.params.multiuser.RunOnWorkProfileParameterHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Helper to get the {@link IModuleParameterHandler} associated with the parameter. */
public class ModuleParametersHelper {

    private static Map<ModuleParameters, IModuleParameterHandler> sHandlerMap = new HashMap<>();

    static {
        sHandlerMap.put(ModuleParameters.INSTANT_APP, new InstantAppHandler());
        sHandlerMap.put(ModuleParameters.NOT_INSTANT_APP, new NegativeHandler());

        sHandlerMap.put(ModuleParameters.MULTI_ABI, new NegativeHandler());
        sHandlerMap.put(ModuleParameters.NOT_MULTI_ABI, new NotMultiAbiHandler());

        sHandlerMap.put(
                ModuleParameters.RUN_ON_WORK_PROFILE, new RunOnWorkProfileParameterHandler());
        sHandlerMap.put(
                ModuleParameters.RUN_ON_SECONDARY_USER, new RunOnSecondaryUserParameterHandler());

        sHandlerMap.put(ModuleParameters.NO_FOLDABLE_STATES, new NegativeHandler());
        sHandlerMap.put(ModuleParameters.ALL_FOLDABLE_STATES, new FoldableExpandingHandler());
    }

    private static Map<ModuleParameters, Set<ModuleParameters>> sGroupMap = new HashMap<>();

    static {
        sGroupMap.put(
                ModuleParameters.MULTIUSER,
                Set.of(
                        ModuleParameters.RUN_ON_WORK_PROFILE,
                        ModuleParameters.RUN_ON_SECONDARY_USER));
    }

    /**
     * Optional parameters are params that will not automatically be created when the module
     * parameterization is enabled. They will need to be explicitly enabled. They represent a second
     * set of parameterization that is less commonly requested to run. They could be upgraded to
     * main parameters in the future by moving them above.
     */
    private static Map<ModuleParameters, IModuleParameterHandler> sOptionalHandlerMap = new HashMap<>();

    static {
        sOptionalHandlerMap.put(ModuleParameters.SECONDARY_USER, new SecondaryUserHandler());
        sOptionalHandlerMap.put(ModuleParameters.NOT_SECONDARY_USER, new NegativeHandler());
    }

    private static Map<ModuleParameters, Set<ModuleParameters>> sOptionalGroupMap = new HashMap<>();

    static {
    }

    /**
     * Get the all {@link ModuleParameters} which are sub-params of a given {@link
     * ModuleParameters}.
     *
     * <p>This will recursively resolve sub-groups and will only return {@link ModuleParameters}
     * which are not groups.
     *
     * <p>If {@code param} is not a group then a singleton set containing {@code param} will be
     * returned itself, regardless of {@code withOptional}.
     *
     * @param withOptional Whether or not to also check optional param groups.
     */
    public static Map<ModuleParameters, IModuleParameterHandler> resolveParam(
            ModuleParameters param, boolean withOptional) {
        Set<ModuleParameters> mappedParams = sGroupMap.get(param);
        if (mappedParams == null && withOptional) {
            mappedParams = sOptionalGroupMap.get(param);
        }
        if (mappedParams == null) {
            IModuleParameterHandler handler = getParameterHandler(param, withOptional);
            if (handler == null) {
                // If the handler is not supported yet (for example, optional params) skip the
                // param.
                return new HashMap<>();
            }
            return Map.of(param, getParameterHandler(param, withOptional));
        }
        // If the parameter is a group, expand it.
        Map<ModuleParameters, IModuleParameterHandler> resolvedParams = new HashMap<>();
        for (ModuleParameters moduleParameters : mappedParams) {
            resolvedParams.put(moduleParameters, sHandlerMap.get(moduleParameters));
        }
        return resolvedParams;
    }

    /**
     * Returns the {@link IModuleParameterHandler} associated with the requested parameter.
     *
     * @param withOptional Whether or not to also check optional params.
     */
    private static IModuleParameterHandler getParameterHandler(
            ModuleParameters param, boolean withOptional) {
        IModuleParameterHandler value = sHandlerMap.get(param);
        if (value == null && withOptional) {
            return sOptionalHandlerMap.get(param);
        }
        return value;
    }
}
