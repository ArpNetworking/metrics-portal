/*
 * Copyright 2019 Dropbox
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
package com.arpnetworking.metrics.portal.integration.controllers;

import com.arpnetworking.metrics.portal.integration.test.WebServerHelper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.junit.Test;
import play.mvc.Http;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@code MetaController}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class MetaControllerIT {

    @Test
    public void testPing() throws IOException {
        final HttpGet request = new HttpGet(WebServerHelper.getUri("/ping"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.OK, response.getStatusLine().getStatusCode());
            assertEquals(ContentType.APPLICATION_JSON.getMimeType(), response.getLastHeader(HttpHeaders.CONTENT_TYPE).getValue());

            final JsonNode pingJson = WebServerHelper.readContentAsJson(response);
            assertEquals("HEALTHY", pingJson.get("status").asText());
        }
    }

    @Test
    public void testStatus() throws IOException {
        final HttpGet request = new HttpGet(WebServerHelper.getUri("/status"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.OK, response.getStatusLine().getStatusCode());
            assertEquals(ContentType.APPLICATION_JSON.getMimeType(), response.getLastHeader(HttpHeaders.CONTENT_TYPE).getValue());

            final JsonNode statusJson = WebServerHelper.readContentAsJson(response);
            assertEquals("Metrics Portal", statusJson.get("name").asText());
            assertTrue(statusJson.get("version").asText().matches("[\\d]+\\.[\\d]+\\.[\\d]+(-SNAPSHOT)?"));
            assertTrue(statusJson.get("sha").asText().matches("[a-f0-9]{40}"));
            assertTrue(statusJson.get("isLeader").asBoolean());
            assertTrue(statusJson.get("members").isArray());
            assertEquals(1, statusJson.get("members").size());
            assertNotNull(statusJson.get("clusterLeader"));
            assertNotNull(statusJson.get("localAddress"));
        }
    }

    @Test
    public void testVersion() throws IOException {
        final HttpGet request = new HttpGet(WebServerHelper.getUri("/version"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.OK, response.getStatusLine().getStatusCode());
            assertEquals(ContentType.APPLICATION_JSON.getMimeType(), response.getLastHeader(HttpHeaders.CONTENT_TYPE).getValue());

            final JsonNode versionJson = WebServerHelper.readContentAsJson(response);
            assertEquals("Metrics Portal", versionJson.get("name").asText());
            assertTrue(versionJson.get("version").asText().matches("[\\d]+\\.[\\d]+\\.[\\d]+(-SNAPSHOT)?"));
            assertTrue(versionJson.get("sha").asText().matches("[a-f0-9]{40}"));
        }
    }
}
