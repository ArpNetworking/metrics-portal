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

import akka.actor.ActorSystem;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.impl.DatabaseAlertExecutionRepository;
import com.arpnetworking.metrics.portal.integration.test.WebServerHelper;
import com.arpnetworking.metrics.portal.reports.RecipientType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSetMultimap;
import models.internal.impl.HtmlReportFormat;
import models.view.reports.Report;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Test;
import play.mvc.Http;

import java.io.IOException;
import java.net.URI;

import static org.junit.Assert.assertEquals;

/**
 * Integration tests for {@code AlertController}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertControllerIT {
    private static final ActorSystem AKKA_ACTOR_SYSTEM = ActorSystem.create(KairosDbProxyControllerIT.class.getSimpleName());

    public void setUp() {
//        final DatabaseAlertExecutionRepository repo = new DatabaseAlertExecutionRepository(
//
//        )
    }

    public void tearDown() {

    }

    public void testGetAlert() {

    }

    public void testGetAlertNotFound() {

    }

    public void testQueryAlerts() {

    }

    public void testQueryAlertsFiltered() {

    }

//    @Test
//    public void testCreateValid() throws IOException {
//        final Report report = Report.fromInternal(TestBeanFactory.createReportBuilder()
//                .setReportSource(TestBeanFactory.createWebPageReportSourceBuilder().setUri(URI.create("https://example.com/")).build())
//                .setRecipients(ImmutableSetMultimap.of(
//                        new HtmlReportFormat.Builder().build(),
//                        TestBeanFactory.createRecipientBuilder().setType(RecipientType.EMAIL).setAddress("alice@example.com").build()
//                ))
//                .build()
//        );
//        final HttpPut putRequest = new HttpPut(WebServerHelper.getUri("/v1/reports"));
//        putRequest.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
//        putRequest.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(report)));
//        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(putRequest)) {
//            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());
//        }
//
//        final String reportId = report.getId().toString();
//        final HttpGet getRequest = new HttpGet(WebServerHelper.getUri("/v1/reports/" + reportId));
//        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(getRequest)) {
//            assertEquals(Http.Status.OK, response.getStatusLine().getStatusCode());
//            final Report returnedReport = WebServerHelper.readContentAs(response, Report.class);
//            assertEquals(report, returnedReport);
//        }
//    }
//
//    @Test
//    public void testCreateInvalid() throws IOException {
//        final Report invalidReport = Report.fromInternal(
//                TestBeanFactory.createReportBuilder()
//                        .setRecipients(ImmutableSetMultimap.of(
//                                new HtmlReportFormat.Builder().build(),
//                                TestBeanFactory.createRecipientBuilder().setAddress("not a valid email address").build()
//                        ))
//                        .build());
//        final HttpPut putRequest = new HttpPut(WebServerHelper.getUri("/v1/reports"));
//        putRequest.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
//        putRequest.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(invalidReport)));
//        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(putRequest)) {
//            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
//        }
//
//        final String reportId = invalidReport.getId().toString();
//        final HttpGet getRequest = new HttpGet(WebServerHelper.getUri("/v1/reports/" + reportId));
//        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(getRequest)) {
//            assertEquals(Http.Status.NOT_FOUND, response.getStatusLine().getStatusCode());
//        }
//    }
//
//    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
