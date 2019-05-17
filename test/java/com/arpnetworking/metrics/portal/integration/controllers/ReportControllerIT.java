/*
 * Copyright 2019 Dropbox Inc.
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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.integration.test.WebServerHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Test;
import play.mvc.Http;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Integration tests for {@code ReportController}.

 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ReportControllerIT {

    @Test
    public void testCreateValidCase() throws IOException {
        HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/reports"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(new StringEntity(loadResource("testCreateValidCase")));
        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());
        }

        final String reportId = OBJECT_MAPPER
                .readValue(loadResource("testCreateValidCase"), models.view.reports.Report.class)
                .getId()
                .toString();
        request = new HttpPut(WebServerHelper.getUri("/v1/reports/" + reportId));
        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());
        }

    }

    private String loadResource(final String suffix) {
        final String resourcePath = "com/arpnetworking/metrics/portal/integration/controllers/"
                + CLASS_NAME
                + "."
                + suffix
                + ".json";
        final URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IllegalArgumentException(String.format("Resource not found: %s", resourcePath));
        }
        try {
            return Resources.toString(resourceUrl, Charsets.UTF_8);
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }
    private HttpEntity createEntity(final String resourceSuffix) {
        try {
            return new StringEntity(loadResource(resourceSuffix));
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }

    private static final String CLASS_NAME = ReportControllerIT.class.getSimpleName();
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
