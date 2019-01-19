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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.expressions.ExpressionRepository;
import com.arpnetworking.metrics.portal.expressions.impl.DatabaseExpressionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import models.internal.Expression;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Tests <code>ExpressionController</code>.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class ExpressionControllerTest {

    @BeforeClass
    public static void instantiate() {
        EXPRESSION_REPOSITORY.open();
        gApplication = new GuiceApplicationBuilder()
                .loadConfig(ConfigFactory.load("portal.application.conf"))
                .configure("expressionRepository.type", DatabaseExpressionRepository.class.getName())
                .configure(
                        "expressionRepository.expressionQueryGenerator.type",
                        DatabaseExpressionRepository.GenericQueryGenerator.class.getName())
                .configure("play.modules.disabled", Arrays.asList("play.core.ObjectMapperModule", "global.PillarModule"))
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
        Helpers.start(gApplication);
    }

    @AfterClass
    public static void shutdown() {
        EXPRESSION_REPOSITORY.close();
        if (gApplication != null) {
            Helpers.stop(gApplication);
        }
    }

    @Test
    public void testCreateValidCase() throws IOException {
        final JsonNode body = OBJECT_MAPPER.valueToTree(TestBeanFactory.createExpression());
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(body)
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        final Result result = Helpers.route(gApplication, request);
        assertEquals(Http.Status.NO_CONTENT, result.status());
    }

    @Test
    public void testCreateMissingBodyCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        final Result result = Helpers.route(gApplication, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingIdCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingIdCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        final Result result = Helpers.route(gApplication, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingClusterCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingClusterCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        final Result result = Helpers.route(gApplication, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingMetricCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingMetricCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        final Result result = Helpers.route(gApplication, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingScriptCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingScriptCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        final Result result = Helpers.route(gApplication, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingServiceCase() {
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingServiceCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        final Result result = Helpers.route(gApplication, request);
        assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testUpdateValidCase() throws IOException {
        final UUID uuid = UUID.randomUUID();
        final Expression originalExpr = TestBeanFactory.createExpressionBuilder()
                .setId(uuid)
                .build();
        EXPRESSION_REPOSITORY.addOrUpdateExpression(originalExpr, TestBeanFactory.getDefautOrganization());
        final JsonNode body = OBJECT_MAPPER.valueToTree(TestBeanFactory.createExpressionBuilder().setId(uuid).build());
        final Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(body)
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        final Result result = Helpers.route(gApplication, request);
        assertEquals(Http.Status.NO_CONTENT, result.status());
        final Expression expectedExpr = EXPRESSION_REPOSITORY.get(originalExpr.getId(), TestBeanFactory.getDefautOrganization()).get();
        assertEquals(OBJECT_MAPPER.valueToTree(expectedExpr), body);
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

    private static Application gApplication;
    private static final ExpressionRepository EXPRESSION_REPOSITORY =
            new DatabaseExpressionRepository(new DatabaseExpressionRepository.GenericQueryGenerator());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CLASS_NAME = ExpressionControllerTest.class.getSimpleName();
}
