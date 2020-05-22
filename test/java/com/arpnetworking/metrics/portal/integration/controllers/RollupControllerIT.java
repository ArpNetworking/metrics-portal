/*
 * Copyright 2020 Dropbox Inc.
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
import com.arpnetworking.utility.test.ResourceHelper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Test;
import play.mvc.Http;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Integration tests for {@link controllers.RollupController}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class RollupControllerIT {

    @Test
    public void testConsistencyCheckValid() throws IOException {
        final HttpPost request = new HttpPost(WebServerHelper.getUri("/v1/rollups/check_consistency"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testConsistencyCheck.valid"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testConsistencyCheckInvalid() throws IOException {
        final HttpPost request = new HttpPost(WebServerHelper.getUri("/v1/rollups/check_consistency"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testConsistencyCheck.invalid"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    private HttpEntity createEntity(final String resourceSuffix) throws IOException {
        return new StringEntity(ResourceHelper.loadResource(getClass(), resourceSuffix));
    }
}
