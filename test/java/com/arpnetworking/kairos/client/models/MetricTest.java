package com.arpnetworking.kairos.client.models;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import io.ebeaninternal.util.IOUtils;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
