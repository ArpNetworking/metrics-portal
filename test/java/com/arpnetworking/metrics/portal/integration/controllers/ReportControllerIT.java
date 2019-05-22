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
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.integration.test.WebServerHelper;
import com.arpnetworking.utility.ResourceHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import models.view.reports.Report;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
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
    public void testCreateValid() throws IOException {
        final Report report = Report.fromInternal(TestBeanFactory.createReportBuilder().build());
        final HttpPut putRequest = new HttpPut(WebServerHelper.getUri("/v1/reports"));
        putRequest.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        putRequest.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(report)));
        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(putRequest)) {
            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());
        }

        final String reportId = report.getId().toString();
        final HttpGet getRequest = new HttpGet(WebServerHelper.getUri("/v1/reports/" + reportId));
        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(getRequest)) {
            assertEquals(Http.Status.OK, response.getStatusLine().getStatusCode());
            final Report returnedReport = OBJECT_MAPPER.readValue(response.getEntity().getContent(), models.view.reports.Report.class);
            assertEquals(report, returnedReport);
        }

    }

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
