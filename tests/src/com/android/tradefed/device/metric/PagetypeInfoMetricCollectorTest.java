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
package com.android.tradefed.device.metric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.android.tradefed.invoker.IInvocationContext;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/** Unit tests for {@link PagetypeInfoMetricCollector}. */
//TODO(b/71868090): Consolidate all the individual metric collector tests into one common tests.
@RunWith(JUnit4.class)
public class PagetypeInfoMetricCollectorTest {
    @Mock IInvocationContext mContext;

    @Spy PagetypeInfoMetricCollector pagetypeInfoMetricCollector;

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        doNothing()
                .when(pagetypeInfoMetricCollector)
                .saveProcessOutput(anyString(), any(File.class));

        doReturn(tempFolder.newFolder()).when(pagetypeInfoMetricCollector).createTempDir();
    }

    @Test
    public void testCollect() throws Exception {
        DeviceMetricData runData = new DeviceMetricData(mContext);
        when(pagetypeInfoMetricCollector.getFileSuffix()).thenReturn("1");

        pagetypeInfoMetricCollector.collect(runData);

        Map<String, String> metricsCollected = new HashMap<String, String>();
        runData.addToMetrics(metricsCollected);

        assertEquals(metricsCollected.size(), 1);
        assertTrue(metricsCollected.containsKey("pagetypeinfo-1"));
    }
}
