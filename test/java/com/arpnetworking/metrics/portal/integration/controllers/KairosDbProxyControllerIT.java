/*
 * Copyright 2019 Inscope Metrics
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

/**
 * Integration tests for {@code KairosDbProxyController}.
 *
 * @author Ville Koskela (ville at inscopemetrics dot io)
 */
public final class KairosDbProxyControllerIT {

    @Test
    public void testHealthStatus() throws IOException {
        final HttpGet request = new HttpGet(WebServerHelper.getUri("/api/v1/health/status"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.OK, response.getStatusLine().getStatusCode());
            assertEquals(ContentType.APPLICATION_JSON.getMimeType(), response.getLastHeader(HttpHeaders.CONTENT_TYPE).getValue());

            final JsonNode pingJson = WebServerHelper.readContentAsJson(response);
            assertEquals("JVM-Thread-Deadlock: OK", pingJson.get(0).asText());
            assertEquals("Datastore-Query: OK", pingJson.get(1).asText());
        }
    }
}
