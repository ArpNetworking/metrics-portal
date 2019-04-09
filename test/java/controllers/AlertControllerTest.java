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
package controllers;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.alerts.impl.DatabaseAlertRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import io.ebean.Ebean;
import models.ebean.NagiosExtension;
import models.internal.Alert;
import models.internal.Context;
import models.internal.Operator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import play.test.WithApplication;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Tests class <code>AlertController</code>.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class AlertControllerTest extends WithApplication {

    @BeforeClass
    public static void instantiate() {
        ALERT_REPOSITORY.open();
    }

    @AfterClass
    public static void shutdown() {
        ALERT_REPOSITORY.close();
    }

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .loadConfig(ConfigFactory.load("portal.application.conf"))
                .configure("alertRepository.type", DatabaseAlertRepository.class.getName())
                .configure("alertRepository.expressionQueryGenerator.type", DatabaseAlertRepository.GenericQueryGenerator.class.getName())
                .configure("play.modules.disabled", Arrays.asList("play.core.ObjectMapperModule", "global.PillarModule"))
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }


    @Test
    public void testCreateValidCase() throws IOException {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateValidCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.NO_CONTENT, result.status());
        final models.ebean.Alert alert = Ebean.find(models.ebean.Alert.class)
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

    @Test
    public void testCreateMissingBodyCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingIdCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingIdCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingContextCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingContextCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateInvalidContextCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateInvalidContextCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingNameCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingNameCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingClusterCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingClusterCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingMetricCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingMetricCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingStatisticCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingStatisticCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingServiceCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingServiceCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingPeriodCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingPeriodCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateInvalidPeriodCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateInvalidPeriodCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingOperatorCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingOperatorCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateInvalidOperatorCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateInvalidOperatorCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingValueCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingValueCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingExtensionsCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingExtensionsCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.NO_CONTENT, result.status());
    }

    @Test
    public void testCreateEmptyExtensionsCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateEmptyExtensionsCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.NO_CONTENT, result.status());
    }

    @Test
    public void testUpdateValidCase() throws IOException {
        final UUID uuid = UUID.fromString("e62368dc-1421-11e3-91c1-00259069c2f0");
        final Alert originalAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        ALERT_REPOSITORY.addOrUpdateAlert(originalAlert, TestBeanFactory.getDefautOrganization());
        final JsonNode body = readTree("testUpdateValidCase");
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(body)
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        final Result result = Helpers.route(app, request);
        assertEquals(Http.Status.NO_CONTENT, result.status());
    }

    private JsonNode readTree(final String resourceSuffix) {
        try {
            return OBJECT_MAPPER.readTree(getClass().getClassLoader().getResource(
                    "controllers/" + CLASS_NAME + "." + resourceSuffix + ".json"));
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }

    private static final AlertRepository ALERT_REPOSITORY =
            new DatabaseAlertRepository(new DatabaseAlertRepository.GenericQueryGenerator());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CLASS_NAME = AlertControllerTest.class.getSimpleName();
}
