/*
 * Copyright 2015 Groupon.com
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

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.impl.DatabaseAlertRepository;
import com.arpnetworking.metrics.portal.integration.test.EbeanServerHelper;
import com.arpnetworking.metrics.portal.integration.test.WebServerHelper;
import com.arpnetworking.utility.ResourceHelper;
import io.ebean.EbeanServer;
import models.ebean.NagiosExtension;
import models.internal.Alert;
import models.internal.Context;
import models.internal.Operator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.mvc.Http;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Integration tests for {@code AlertController}.
 *
 * TODO(ville): Most of these test cases don't belong here as they test view model constraints.
 * ^ Such tests should be expressed on the view model as unit tests not as integration tests against the server.
 *
 * TODO(ville): Move controller integration test data from JSON files to random view models from the factory.
 * ^ This will allow controller tests to be run repeatedly w/o being influenced by data store state.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public final class AlertControllerIT {

    @Before
    public void setUp() {
        _ebeanServer = EbeanServerHelper.getMetricsDatabase();
        _alertRepo = new DatabaseAlertRepository(_ebeanServer);
        _alertRepo.open();
    }

    @After
    public void tearDown() {
        _alertRepo.close();
    }

    @Test
    public void testCreateValidCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateValidCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());

            final models.ebean.Alert alert = _ebeanServer.find(models.ebean.Alert.class)
                    .where()
                    .eq("uuid", UUID.fromString("88410734-aed7-11e1-8e54-00259060b612"))
                    .findOne();

            assertNotNull(alert);
            assertEquals(Context.CLUSTER, alert.getContext());
            assertEquals("test-cluster", alert.getCluster());
            assertEquals("test-name", alert.getName());
            assertEquals("test-metric", alert.getMetric());
            assertEquals("test-service", alert.getService());
            assertEquals(1, alert.getPeriod());
            assertEquals(Operator.EQUAL_TO, alert.getOperator());
            assertEquals(12, alert.getQuantityValue(), 0.01);
            assertEquals("MEGABYTE", alert.getQuantityUnit());

            final NagiosExtension extension = alert.getNagiosExtension();
            assertNotNull(extension);
            assertEquals("CRITICAL", extension.getSeverity());
            assertEquals("abc@example.com", extension.getNotify());
            assertEquals(3, extension.getMaxCheckAttempts());
            assertEquals(300, extension.getFreshnessThreshold());
        }
    }

    @Test
    public void testCreateMissingBodyCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingIdCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingIdCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingContextCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingContextCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateInvalidContextCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateInvalidContextCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingNameCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingNameCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingClusterCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingClusterCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingMetricCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingMetricCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingStatisticCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingStatisticCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingServiceCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingServiceCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingPeriodCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingPeriodCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateInvalidPeriodCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateInvalidPeriodCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingOperatorCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingOperatorCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateInvalidOperatorCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateInvalidOperatorCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingValueCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingValueCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.BAD_REQUEST, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateMissingExtensionsCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateMissingExtensionsCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateEmptyExtensionsCase() throws IOException {
        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testCreateEmptyExtensionsCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testUpdateValidCase() throws IOException {
        // TODO(ville): We need a strategy for testing against particular organizations!
        final UUID uuid = UUID.fromString("e62368dc-1421-11e3-91c1-00259069c2f0");
        final Alert originalAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        _alertRepo.addOrUpdateAlert(originalAlert, TestBeanFactory.getDefautOrganization());

        final HttpPut request = new HttpPut(WebServerHelper.getUri("/v1/alerts"));
        request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));
        request.setEntity(createEntity("testUpdateValidCase"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.NO_CONTENT, response.getStatusLine().getStatusCode());
        }

        // TODO(ville): We should validate that the update actually did something!
    }

    private HttpEntity createEntity(final String resourceSuffix) throws IOException {
        return new StringEntity(ResourceHelper.loadResource(getClass(), resourceSuffix));
    }

    private EbeanServer _ebeanServer;
    private DatabaseAlertRepository _alertRepo;
}
