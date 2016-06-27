/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Helper Class that provides the filtering for JUnit4 runner by extending the {@link Filter}.
 */
public class JUnit4TestFilter extends Filter {

    private TestFilterHelper mFilterHelper;

    public JUnit4TestFilter(TestFilterHelper filterHelper) {
        mFilterHelper = filterHelper;
    }

    final Annotation junit4TestAnnotation = new Annotation() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return org.junit.Test.class;
        }
    };

    /**
     * Filter function deciding whether a test should run or not.
     * TODO: Once the JUnit4 version is updated, the JUnit3 tests will have their annotations
     * available in {@link Description} for direct filtering.
     */
    @Override
    public boolean shouldRun(Description description) {
        if (description.getAnnotations().contains(junit4TestAnnotation)) {
            // if we are looking at a JUnit 4 Test in a JUnit4 Suite
            return mFilterHelper.shouldRun(description);
        } else {
            // Not the main use case but if we are looking at a JUnit 3 Test in a JUnit4 Suite
            // For Junit3 we have some extra work to do because the annotations are not
            // available in {@link Description}
            if (description.getMethodName() == null) {
                // Containers are always included
                return true;
            }

            Class<?> classObj = null;
            try {
                classObj = Class.forName(description.getClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(String.format("Could not load Test class %s",
                        classObj), e);
            }

            String packageName = classObj.getPackage().getName();
            Method[] methods = classObj.getMethods();
            for (Method method : methods) {
                if (description.getMethodName().equals(method.getName())) {
                    if (!Modifier.isPublic(method.getModifiers())
                            || !method.getReturnType().equals(Void.TYPE)
                            || method.getParameterTypes().length > 0
                            || !method.getName().startsWith("test")
                            || !mFilterHelper.shouldRun(packageName, classObj.getName(),
                                    method)) {
                        CLog.i("excluding %s", description);
                        return false;
                    } else {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public String describe() {
        return "This filter is based on annotations, regex filter, class and method name to decide "
                + "if a particular test should run.";
    }
}
