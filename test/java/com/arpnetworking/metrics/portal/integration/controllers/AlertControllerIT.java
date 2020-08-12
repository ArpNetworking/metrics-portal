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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import play.mvc.Http;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * Integration tests for {@code AlertController}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertControllerIT {
    @Test
    public void testQueryAlertsWorksAndIsEmpty() throws IOException, URISyntaxException {
        final URIBuilder uriBuilder = WebServerHelper.getUriBuilder("/v1/alerts/query");
        uriBuilder.setParameters(new BasicNameValuePair("limit", "1000"));
        uriBuilder.setParameters(new BasicNameValuePair("offset", "0"));
        final HttpUriRequest request = new HttpGet(uriBuilder.build());
        final JsonNode pageContainer;
        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertThat(response.getStatusLine().getStatusCode(), is(Http.Status.OK));
            pageContainer = WebServerHelper.readContentAsJson(response);
        }
        final JsonNode pagination = pageContainer.get("pagination");
        final int total = pagination.get("total").asInt();
        assertThat(total, is(0));
        final int offset = pagination.get("offset").asInt();
        assertThat(offset, is(0));
        final int size = pagination.get("size").asInt();
        assertThat(size, is(0));
        final JsonNode next = pagination.get("next");
        assertThat(next.isNull(), is(true));
        final JsonNode prev = pagination.get("previous");
        assertThat(prev.isNull(), is(true));

        final JsonNode data = pageContainer.get("data");
        assertThat(data, instanceOf(ArrayNode.class));
        assertThat(data.size(), is(0));

    }
}
