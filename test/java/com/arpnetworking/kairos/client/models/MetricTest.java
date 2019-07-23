/*
 * Copyright 2019 Dropbox, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.kairos.client.models;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ebeaninternal.util.IOUtils;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for {@link Metric}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class MetricTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final String CLASS_NAME = MetricTest.class.getSimpleName();

    @Test
    public void testPreservesArbitraryFields() throws Exception {
        final ObjectNode original = OBJECT_MAPPER.readValue(readResource("testPreservesArbitraryFields"), ObjectNode.class);
        final Metric metric = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(original), Metric.class);
        final ObjectNode reserialized = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(metric), ObjectNode.class);

        assertEquals(original, reserialized);
    }


    private String readResource(final String resourceSuffix) {
        try {
            return IOUtils.readUtf8(getClass()
                    .getClassLoader()
                    .getResourceAsStream("com/arpnetworking/kairos/client/models/" + CLASS_NAME + "." + resourceSuffix + ".json"));
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }
}
