/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.lite;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;
import org.junit.runners.Suite.SuiteClasses;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Implements some useful utility methods for running host tests.
 *
 * <p>This implements a few methods for finding tests on the host and faking execution of JUnit
 * tests so we can "dry run" them.
 */
public final class HostUtils {

    private HostUtils() {}

    /**
     * Takes a description of a JUnit test and fakes its execution.
     *
     * <p>It takes a description of the test unit, then recursively calls the listener methods using
     * that description to make the listener believe the test ran, but without actually executing
     * the test.
     *
     * @param desc
     * @param listener
     * @throws IOException
     */
    public static void fakeExecution(Description desc, RunListener listener) throws IOException {
        if (desc.getMethodName() == null || !desc.getChildren().isEmpty()) {
            for (Description child : desc.getChildren()) {
                fakeExecution(child, listener);
            }
        } else {
            try {
                listener.testStarted(desc);
                listener.testFinished(desc);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                // Something surprising happened.
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Gets JUnit4 test cases from provided classnames and jar paths.
     *
     * @param classNames Classes that exist in the current class path to check for JUnit tests
     * @param jarAbsPaths Jars to search for classes with the test annotations.
     * @return a list of class objects that are test classes to execute.
     * @throws IllegalArgumentException
     */
    public static final List<Class<?>> getJUnit4Classes(
            List<String> classNames, List<String> jarAbsPaths) throws IllegalArgumentException {
        Set<String> outputNames = new HashSet<String>();
        List<Class<?>> output = new ArrayList<>();

        for (String className : classNames) {
            if (outputNames.contains(className)) {
                continue;
            }
            try {
                Class<?> klass = Class.forName(className, true, HostUtils.class.getClassLoader());
                outputNames.add(className);
                output.add(klass);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(
                        String.format("Could not load test class %s", className), e);
            }
        }

        for (String jarName : jarAbsPaths) {
            JarFile jarFile = null;
            try {
                File file = new File(jarName);
                jarFile = new JarFile(file);
                Iterator<JarEntry> i = jarFile.entries().asIterator();
                URL[] urls = {new URL(String.format("jar:file:%s!/", file.getAbsolutePath()))};
                URLClassLoader cl = URLClassLoader.newInstance(urls);

                while (i.hasNext()) {
                    JarEntry je = i.next();
                    if (je.isDirectory()
                            || !je.getName().endsWith(".class")
                            || je.getName().contains("$")) {
                        continue;
                    }
                    String className = getClassName(je.getName());
                    if (outputNames.contains(className)) {
                        continue;
                    }
                    try {
                        Class<?> cls = cl.loadClass(className);
                        int modifiers = cls.getModifiers();
                        if (hasJUnit4Annotation(cls)
                                && !Modifier.isStatic(modifiers)
                                && !Modifier.isPrivate(modifiers)
                                && !Modifier.isProtected(modifiers)
                                && !Modifier.isInterface(modifiers)
                                && !Modifier.isAbstract(modifiers)) {
                            outputNames.add(className);
                            output.add(cls);
                        }
                    } catch (UnsupportedClassVersionError ucve) {
                        throw new IllegalArgumentException(
                                String.format(
                                        "Could not load class %s from jar %s. Reason:\n%s",
                                        className, jarName, ucve.toString()));
                    } catch (ClassNotFoundException cnfe) {
                        throw new IllegalArgumentException(
                                String.format("Cannot find test class %s", className));
                    } catch (IllegalAccessError | NoClassDefFoundError err) {
                        // IllegalAccessError can happen when the class or one of its super
                        // class/interfaces are package-private. We can't load such class from
                        // here (= outside of the package). Since our intention is not to load
                        // all classes in the jar, but to find our the main test classes, this
                        // can be safely skipped.
                        // NoClassDefFoundError is also okay because certain CTS test cases
                        // might statically link to a jar library (e.g. tools.jar from JDK)
                        // where certain internal classes in the library are referencing
                        // classes that are not available in the jar. Again, since our goal here
                        // is to find test classes, this can be safely skipped.
                        continue;
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            } finally {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    throw new RuntimeException(
                            String.format("Something went wrong closing the jarfile: " + jarName));
                }
            }
        }
        return output;
    }

    /**
     * Checks whether a class has a JUnit4 annotation
     *
     * @param classObj Class to examine for the annotation
     * @return whether the class object has the JUnit4 test annotation
     */
    public static boolean hasJUnit4Annotation(Class<?> classObj) {
        if (classObj.isAnnotationPresent(SuiteClasses.class)) {
            return true;
        }
        for (Method m : classObj.getMethods()) {
            if (m.isAnnotationPresent(org.junit.Test.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the ".class" file extension from a filename to get the classname.
     *
     * @param name The filename from which to strip the extension
     * @return The name of the class contained in the file
     */
    private static String getClassName(String name) {
        // -6 because of .class
        return name.substring(0, name.length() - 6).replace('/', '.');
    }
}
