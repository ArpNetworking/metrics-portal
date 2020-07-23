/*
 * Copyright 2020 Dropbox, Inc.
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

import akka.actor.ActorSystem;
import com.arpnetworking.metrics.portal.alerts.AlertExecutionRepository;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.alerts.impl.PluggableAlertRepository;
import com.arpnetworking.metrics.portal.integration.controllers.KairosDbProxyControllerIT;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.portal.organizations.impl.DefaultOrganizationRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobExecutionRepository;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import models.internal.MetricsQueryFormat;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultMetricsQuery;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.mvc.Call;
import play.mvc.Http;
import play.test.Helpers;
import play.test.TestServer;
import play.test.WithServer;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Functional tests for {@code AlertController}.
 * <p>
 * This should be refactored into an integration test once we can expose an API for alert creation.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertControllerIT {
    private static final ObjectMapper objectMapper = SerializationTestUtils.getApiObjectMapper();
    private static final ActorSystem actorSystem = ActorSystem.create(KairosDbProxyControllerIT.class.getSimpleName());

    private Organization organization;
    private AlertController controller;
    private Application application;
    private TestServer server;
    private UUID alertId;

    @Before
    public void setUp() {
        final OrganizationRepository organizationRepository = new DefaultOrganizationRepository();
        organizationRepository.open();
        final QueryResult<Organization> queryResult = organizationRepository.query(organizationRepository.createQuery().limit(1));
        organization = queryResult.values().stream().findFirst().orElse(null);
        assertThat(organization, is(notNullValue()));

        alertId = UUID.randomUUID();

        final AlertExecutionRepository alertExecutionRepository = new MapExecutionRepository();
        final AlertRepository alertRepository = new PluggableAlertRepository(
                objectMapper,
                organization.getId(),
                ImmutableMap.of(
                        alertId, mockAlert(alertId)
                )
        );
        alertExecutionRepository.open();
        alertRepository.open();

        application = new GuiceApplicationBuilder()
//                .load(new MainModule())
                .overrides(binder -> {
                    binder.bind(OrganizationRepository.class).toInstance(organizationRepository);
                    binder.bind(AlertRepository.class).toInstance(alertRepository);
                    binder.bind(AlertExecutionRepository.class).toInstance(alertExecutionRepository);
                    binder.bind(AlertController.class).asEagerSingleton();
                })
                .configure(ImmutableMap.of(
                        "alerts.limit", 10
                ))
                .build();

        server = Helpers.testServer(application);
        server.start();
//        controller = new AlertController(
//                ConfigFactory.empty(),
//                alertRepository,
//                alertExecutionRepository,
//                new DefaultOrganizationRepository()
//        );
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testGetAlert() throws Exception {
        final Http.Request request = Helpers.fakeRequest(controllers.routes.AlertController.get(alertId)).build();

        try (WSClient ws = newClient()) {
            final WSResponse response = ws.url(request.uri()).get().toCompletableFuture().get();
            assertThat(response.getStatus(), is(equalTo(HttpStatus.SC_OK)));
        }
    }

    @Test
    public void testGetAlertNotFound() throws Exception {
        final UUID alertId = UUID.randomUUID();
        final Http.Request request = Helpers.fakeRequest(controllers.routes.AlertController.get(alertId)).build();

        try (WSClient ws = newClient()) {
            final WSResponse response = ws.url(request.uri()).get().toCompletableFuture().get();
            assertThat(response.getStatus(), is(equalTo(HttpStatus.SC_NOT_FOUND)));
        }
    }

    @Test
    public void testQueryAlerts() {

    }

    @Test
    public void testQueryAlertsFiltered() {

    }

    private WSClient newClient() {
        final int port = server.getRunningHttpPort().
                orElseGet(() -> {
                    return server
                            .getRunningHttpPort()
                            .orElseThrow(() -> new IllegalStateException("Could not find test server port"));
                });
        return play.test.WSTestClient.newClient(port);
    }

    private static class MapExecutionRepository extends MapJobExecutionRepository<AlertEvaluationResult>
            implements AlertExecutionRepository {}

    private Alert mockAlert(final UUID alertId) {
        return new DefaultAlert.Builder()
                .setId(alertId)
                .setName("Fake name")
                .setEnabled(true)
                .setDescription("fake alert")
                .setOrganization(organization)
                .setQuery(
                        new DefaultMetricsQuery.Builder()
                            .setFormat(MetricsQueryFormat.KAIROS_DB)
                            .setQuery("<not evaluated>")
                            .build()
                )
                .setAdditionalMetadata(ImmutableMap.of(
                        "foo", "bar"
                ))
                .build();
    }
}
