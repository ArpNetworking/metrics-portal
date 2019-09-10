/*
 * Copyright 2019 Dropbox, Inc.
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

/**
 * Tests for {@link OriginConfig}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
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
                        .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-\\d+"))
                        .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-\\d+"))
                        .setAdditionalHeaders(ImmutableMap.of("X-Extra-Header", "extra header value"))
                        .build(),
                MAPPER.readValue(
                        "{\n"
                                + "  \"allowedRequestPaths\": [\"/allowed-req-\\\\d+\"],\n"
                                + "  \"allowedNavigationPaths\": [\"/allowed-nav-\\\\d+\"],\n"
                                + "  \"additionalHeaders\": {\"X-Extra-Header\": \"extra header value\"}\n"
                                + "}",
                        OriginConfig.class
                )
        );
    }

    @Test
    public void testIsRequestAllowed() {
        final OriginConfig config = new OriginConfig.Builder()
                .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-\\d+"))
                .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-\\d+"))
                .build();

        assertFalse(config.isRequestAllowed(""));
        assertFalse(config.isRequestAllowed("/disallowed"));
        assertTrue(config.isRequestAllowed("/allowed-req-1"));
        assertTrue(config.isRequestAllowed("/allowed-nav-1"));

        assertFalse(config.isRequestAllowed("/sneaky-prefix/allowed-req-1"));
        assertFalse(config.isRequestAllowed("/allowed-req-1/sneaky-suffix"));
    }

    @Test
    public void testIsNavigationAllowed() {
        final OriginConfig config = new OriginConfig.Builder()
                .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-\\d+"))
                .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-\\d+"))
                .build();

        assertFalse(config.isNavigationAllowed(""));
        assertFalse(config.isNavigationAllowed("/disallowed"));
        assertFalse(config.isNavigationAllowed("/allowed-req-1"));
        assertTrue(config.isNavigationAllowed("/allowed-nav-1"));

        assertFalse(config.isRequestAllowed("/sneaky-prefix/allowed-nav-1"));
        assertFalse(config.isRequestAllowed("/allowed-nav-1/sneaky-suffix"));
    }


    private static final ObjectMapper MAPPER = ObjectMapperFactory.createInstance();
}
