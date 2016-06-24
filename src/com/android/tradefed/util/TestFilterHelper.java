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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Helper class for filtering tests
 */
public class TestFilterHelper {

    /** The include filters of the test name to run */
    private Set<String> mIncludeFilters = new HashSet<>();

    /** The exclude filters of the test name to run */
    private Set<String> mExcludeFilters = new HashSet<>();

    /** The include annotations of the test to run */
    private Set<String> mIncludeAnnotation = new HashSet<>();

    /** The exclude annotations of the test to run */
    private Set<String> mExcludeAnnotation = new HashSet<>();

    public TestFilterHelper() {
    }

    public TestFilterHelper(Collection<String> includeFilters, Collection<String> excludeFilters,
            Collection<String> includeAnnotation, Collection<String> excludeAnnotation) {
        mIncludeFilters.addAll(includeFilters);
        mExcludeFilters.addAll(excludeFilters);
        mIncludeAnnotation.addAll(includeAnnotation);
        mExcludeAnnotation.addAll(excludeAnnotation);
    }

    /**
     * Adds a filter of which tests to include
     */
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /**
     * Adds the {@link Set} of filters of which tests to include
     */
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /**
     * Adds a filter of which tests to exclude
     */
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /**
     * Adds the {@link Set} of filters of which tests to exclude.
     */
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /**
     * Adds an include annotation of the test to run
     */
    public void addIncludeAnnotation(String annotation) {
        mIncludeAnnotation.add(annotation);
    }

    /**
     * Adds the {@link Set} of include annotation of the test to run
     */
    public void addAllIncludeAnnotation(Set<String> annotations) {
        mIncludeAnnotation.addAll(annotations);
    }

    /**
     * Adds an exclude annotation of the test to run
     */
    public void addExcludeAnnotation(String notAnnotation) {
        mExcludeAnnotation.add(notAnnotation);
    }

    /**
     * Adds the {@link Set} of exclude annotation of the test to run
     */
    public void addAllExcludeAnnotation(Set<String> notAnnotations) {
        mExcludeAnnotation.addAll(notAnnotations);
    }

    public Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    public Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }

    public Set<String> getIncludeAnnotation() {
        return mIncludeAnnotation;
    }

    public Set<String> getExcludeAnnotation() {
        return mExcludeAnnotation;
    }


    /**
     * Check if an element that has annotation passes the filter
     *
     * @param annotatedElement the element to filter
     * @return true if the test should run, false otherwise
     */
    public boolean shouldTestRun(AnnotatedElement annotatedElement) {
        if (!mExcludeAnnotation.isEmpty()) {
            for (Annotation a : annotatedElement.getAnnotations()) {
                if (mExcludeAnnotation.contains(a.annotationType().getName())) {
                    // If any of the method annotation match an ExcludeAnnotation, don't run it
                    CLog.i("Skipping %s, ExcludeAnnotation exclude it", annotatedElement);
                    return false;
                }
            }
        }
        if (!mIncludeAnnotation.isEmpty()) {
            Set<String> neededAnnotation = new HashSet<String>();
            neededAnnotation.addAll(mIncludeAnnotation);
            for (Annotation a : annotatedElement.getAnnotations()) {
                if (neededAnnotation.contains(a.annotationType().getName())) {
                    neededAnnotation.remove(a.annotationType().getName());
                }
            }
            if (neededAnnotation.size() != 0) {
                // The test needs to have all the include annotation to pass.
                CLog.i("Skipping %s, IncludeAnnotation filtered it", annotatedElement);
                return false;
            }
        }
        return true;
    }

    /**
     * Check if an element that has annotation passes the filter
     *
     * @param packageName name of the method's package
     * @param className name of the method's class
     * @method test method
     * @return true if the test method should run, false otherwise
     */
    public boolean shouldRun(String packageName, String className, Method method) {
        String methodName = String.format("%s#%s", className, method.getName());
        if (mExcludeFilters.contains(packageName)) {
            // Skip package because it was excluded
            CLog.i("Skip package because it was excluded");
            return false;
        }
        if (mExcludeFilters.contains(className)) {
            // Skip class because it was excluded
            CLog.i("Skip class because it was excluded");
            return false;
        }
        if (mExcludeFilters.contains(methodName)) {
            // Skip method because it was excluded
            CLog.i("Skip method because it was excluded");
            return false;
        }
        if (!shouldTestRun(method)) {
            return false;
        }
        return mIncludeFilters.isEmpty()
                || mIncludeFilters.contains(methodName)
                || mIncludeFilters.contains(className)
                || mIncludeFilters.contains(packageName);
    }
}
