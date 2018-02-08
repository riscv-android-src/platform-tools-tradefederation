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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;

/**
 * Class representing information about a test case. Extends ddmlib TestIdentifer for compatibility
 * with our current result reporting interfaces, but this class will aim at completely replacing it
 * in order to provide more powerful use cases.
 *
 * <p>TODO: Remove the underlying dependency on {@link TestIdentifier}.
 */
public class TestDescription extends TestIdentifier implements Serializable {

    private Annotation[] mAnnotations;

    /**
     * Constructor
     *
     * @param className The name of the class holding the test.
     * @param testName The test (method) name.
     */
    public TestDescription(String className, String testName) {
        super(className, testName);
        mAnnotations = new Annotation[0];
    }

    /**
     * Constructor
     *
     * @param className The name of the class holding the test.
     * @param testName The test (method) name.
     * @param annotations List of {@link Annotation} associated with the test case.
     */
    public TestDescription(String className, String testName, Annotation... annotations) {
        this(className, testName);
        mAnnotations = annotations;
    }

    /**
     * Constructor
     *
     * @param className The name of the class holding the test.
     * @param testName The test (method) name.
     * @param annotations Collection of {@link Annotation} associated with the test case.
     */
    public TestDescription(String className, String testName, Collection<Annotation> annotations) {
        this(className, testName, annotations.toArray(new Annotation[annotations.size()]));
    }

    /**
     * @return the annotation of type annotationType that is attached to this description node, or
     *     null if none exists
     */
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        for (Annotation each : mAnnotations) {
            if (each.annotationType().equals(annotationType)) {
                return annotationType.cast(each);
            }
        }
        return null;
    }

    /** @return all of the annotations attached to this description node */
    public Collection<Annotation> getAnnotations() {
        return Arrays.asList(mAnnotations);
    }

    /**
     * Create a {@link TestDescription} from a {@link TestIdentifier}. Used for ease of conversion
     * from one to another.
     *
     * @param testId The {@link TestIdentifier} to convert.
     * @return the created {@link TestDescription} with the TestIdentifier values.
     */
    public static TestDescription createFromTestIdentifier(TestIdentifier testId) {
        return new TestDescription(testId.getClassName(), testId.getTestName());
    }
}
