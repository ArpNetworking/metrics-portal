/**
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

import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.alerts.impl.DatabaseAlertRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ebean.Ebean;
import models.ebean.NagiosExtension;
import models.internal.Alert;
import models.internal.Organization;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import play.Application;
import play.inject.Bindings;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.io.IOException;
import java.util.UUID;

/**
 * Tests class <code>AlertController</code>.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class AlertControllerTest {

    @BeforeClass
    public static void instantiate() {
        alertRepo.open();
        app = new GuiceApplicationBuilder()
                .overrides(
                        Bindings.bind(AlertRepository.class).toInstance(alertRepo))
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
        Helpers.start(app);
    }

    @AfterClass
    public static void shutdown() {
        alertRepo.close();
        if (app != null) {
            Helpers.stop(app);
        }
    }

    @Test
    public void testCreateValidCase() throws IOException {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateValidCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.NO_CONTENT, result.status());
        final models.ebean.Alert alert = Ebean.find(models.ebean.Alert.class)
                .where()
                .eq("uuid", UUID.fromString("88410734-aed7-11e1-8e54-00259060b612"))
                .findUnique();

        Assert.assertNotNull(alert);
        Assert.assertEquals("test-name", alert.getName());
        Assert.assertEquals(60, alert.getPeriod());

        final NagiosExtension extension = alert.getNagiosExtension();
        Assert.assertNotNull(extension);
        Assert.assertEquals("CRITICAL", extension.getSeverity());
        Assert.assertEquals("abc@example.com", extension.getNotify());
        Assert.assertEquals(3, extension.getMaxCheckAttempts());
        Assert.assertEquals(300, extension.getFreshnessThreshold());
    }

    @Test
    public void testCreatePeriodTooSmallCase() throws IOException {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreatePeriodTooSmallCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.NO_CONTENT, result.status());
        final models.ebean.Alert alert = Ebean.find(models.ebean.Alert.class)
                .where()
                .eq("uuid", UUID.fromString("18410734-aed7-11e1-8e54-00259060b618"))
                .findUnique();

        Assert.assertNotNull(alert);
        Assert.assertEquals("test-name", alert.getName());
        Assert.assertEquals(60, alert.getPeriod());

        final NagiosExtension extension = alert.getNagiosExtension();
        Assert.assertNotNull(extension);
        Assert.assertEquals("CRITICAL", extension.getSeverity());
        Assert.assertEquals("abc@example.com", extension.getNotify());
        Assert.assertEquals(3, extension.getMaxCheckAttempts());
        Assert.assertEquals(300, extension.getFreshnessThreshold());
    }

    @Test
    public void testCreateMissingBodyCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingIdCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingIdCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingNameCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingNameCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingQueryCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingQueryCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingPeriodCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingPeriodCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateInvalidPeriodCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateInvalidPeriodCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingExtensionsCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingExtensionsCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.NO_CONTENT, result.status());
    }

    @Test
    public void testCreateEmptyExtensionsCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateEmptyExtensionsCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.NO_CONTENT, result.status());
    }

    @Test
    public void testUpdateValidCase() throws IOException {
        final UUID uuid = UUID.fromString("e62368dc-1421-11e3-91c1-00259069c2f0");
        Alert originalAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        alertRepo.addOrUpdateAlert(originalAlert, Organization.DEFAULT);
        final JsonNode body = readTree("testUpdateValidCase");
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(body)
                .header("Content-Type", "application/json")
                .uri("/v1/alerts");
        Result result = Helpers.route(app, request);
        Assert.assertEquals(Http.Status.NO_CONTENT, result.status());
    }

    private JsonNode readTree(final String resourceSuffix) {
        try {
            return OBJECT_MAPPER.readTree(getClass().getClassLoader().getResource("controllers/" + CLASS_NAME + "." + resourceSuffix + ".json"));
        } catch (final IOException e) {
            Assert.fail("Failed with exception: " + e);
            return null;
        }
    }

    private static Application app;
    private static final AlertRepository alertRepo = new DatabaseAlertRepository(new DatabaseAlertRepository.GenericQueryGenerator());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CLASS_NAME = AlertControllerTest.class.getSimpleName();
}
