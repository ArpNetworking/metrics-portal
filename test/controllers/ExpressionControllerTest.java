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

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.expressions.ExpressionRepository;
import com.arpnetworking.metrics.portal.expressions.impl.DatabaseExpressionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import models.internal.Expression;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import play.Application;
import play.Configuration;
import play.inject.Bindings;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.io.IOException;
import java.util.UUID;

/**
 * Tests <code>ExpressionController</code>.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class ExpressionControllerTest {

    @BeforeClass
    public static void instantiate() {
        Configuration configuration = Configuration.empty();
        app = new GuiceApplicationBuilder()
                .bindings(Bindings.bind(ExpressionController.class).toInstance(new ExpressionController(configuration, exprRepo)))
                .build();
        Helpers.start(app);
        exprRepo.open();
    }

    @AfterClass
    public static void shutdown() {
        exprRepo.close();
        if (app != null) {
            Helpers.stop(app);
        }
    }

    @Test
    public void testCreateValidCase() throws IOException {
        final JsonNode body = OBJECT_MAPPER.valueToTree(TestBeanFactory.createExpression());
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(body)
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        Result result = Helpers.route(request);
        Assert.assertEquals(Http.Status.OK, result.status());
    }

    @Test
    public void testCreateMissingBodyCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        Result result = Helpers.route(request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingIdCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingIdCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        Result result = Helpers.route(request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingClusterCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingClusterCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        Result result = Helpers.route(request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingMetricCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingMetricCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        Result result = Helpers.route(request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingScriptCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingScriptCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        Result result = Helpers.route(request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testCreateMissingServiceCase() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(readTree("testCreateMissingServiceCase"))
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        Result result = Helpers.route(request);
        Assert.assertEquals(Http.Status.BAD_REQUEST, result.status());
    }

    @Test
    public void testUpdateValidCase() throws IOException {
        final UUID uuid = UUID.randomUUID();
        Expression originalExpr = TestBeanFactory.createExpressionBuilder()
                .setId(uuid)
                .build();
        exprRepo.addOrUpdateExpression(originalExpr);
        final JsonNode body = OBJECT_MAPPER.valueToTree(TestBeanFactory.createExpressionBuilder().setId(uuid).build());
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("PUT")
                .bodyJson(body)
                .header("Content-Type", "application/json")
                .uri("/v1/expressions");
        Result result = Helpers.route(request);
        Assert.assertEquals(Http.Status.OK, result.status());
        Expression expectedExpr = exprRepo.get(originalExpr.getId()).get();
        Assert.assertEquals(OBJECT_MAPPER.valueToTree(expectedExpr), body);
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
    private static final ExpressionRepository exprRepo = new DatabaseExpressionRepository(new DatabaseExpressionRepository.GenericQueryGenerator());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CLASS_NAME = ExpressionControllerTest.class.getSimpleName();
}
