package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OriginConfigTest {
    @Test
    public void testDeserialization() throws IOException {
        assertEquals(
                new OriginConfig.Builder()
                        .setAdditionalHeaders(ImmutableMap.of())
                        .setAllowedNavigationPaths(ImmutableSet.of())
                        .setAllowedRequestPaths(ImmutableSet.of())
                        .build(),
                MAPPER.readValue(
                        "{}",
                        OriginConfig.class
                )
        );

        assertEquals(
                new OriginConfig.Builder()
                        .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-.*"))
                        .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-.*"))
                        .setAdditionalHeaders(ImmutableMap.of("X-Extra-Header", "extra header value"))
                        .build(),
                MAPPER.readValue(
                        "{\n"
                                + "  \"allowedRequestPaths\": [\"/allowed-req-.*\"],\n"
                                + "  \"allowedNavigationPaths\": [\"/allowed-nav-.*\"],\n"
                                + "  \"additionalHeaders\": {\"X-Extra-Header\": \"extra header value\"}\n"
                                + "}",
                        OriginConfig.class
                )
        );
    }

    @Test
    public void testIsRequestAllowed() {
        final OriginConfig config = new OriginConfig.Builder()
                .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-.*"))
                .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-.*"))
                .build();

        assertFalse(config.isRequestAllowed(""));
        assertFalse(config.isRequestAllowed("/disallowed"));
        assertTrue(config.isRequestAllowed("/allowed-req-1"));
        assertTrue(config.isRequestAllowed("/allowed-nav-1"));
    }

    @Test
    public void testIsNavigationAllowed() {
        final OriginConfig config = new OriginConfig.Builder()
                .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-.*"))
                .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-.*"))
                .build();

        assertFalse(config.isNavigationAllowed(""));
        assertFalse(config.isNavigationAllowed("/disallowed"));
        assertFalse(config.isNavigationAllowed("/allowed-req-1"));
        assertTrue(config.isNavigationAllowed("/allowed-nav-1"));
    }


    private static final ObjectMapper MAPPER = ObjectMapperFactory.createInstance();
}
