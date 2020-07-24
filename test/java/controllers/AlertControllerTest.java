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

import com.arpnetworking.metrics.portal.alerts.AlertExecutionRepository;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.alerts.impl.PluggableAlertRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.portal.organizations.impl.DefaultOrganizationRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobExecutionRepository;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
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
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Unit tests for {@code AlertController}.
 * <p>
 * This should be refactored into an integration/functional test once we can expose
 * an API for alert creation.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertControllerTest {
    private static final ObjectMapper objectMapper = SerializationTestUtils.getApiObjectMapper();

    private Organization organization;
    private UUID alertId;
    private AlertController _controller;

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

        _controller = new AlertController(
                ConfigFactory.parseMap(ImmutableMap.of(
                        "alerts.limit", 5
                )),
                alertRepository,
                alertExecutionRepository,
                organizationRepository
        );
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGetAlert() {
        final Http.RequestBuilder request = Helpers.fakeRequest(controllers.routes.AlertController.get(alertId));
        final Result result = Helpers.invokeWithContext(request, Helpers.contextComponents(), () -> _controller.get(alertId));
        assertThat(result.status(), is(equalTo(HttpStatus.SC_OK)));
    }

    @Test
    public void testGetAlertNotFound() {
        final UUID unknownId = UUID.randomUUID();
        final Http.RequestBuilder request = Helpers.fakeRequest(controllers.routes.AlertController.get(unknownId));
        final Result result = Helpers.invokeWithContext(request, Helpers.contextComponents(), () -> _controller.get(unknownId));
        assertThat(result.status(), is(equalTo(HttpStatus.SC_NOT_FOUND)));
    }

    @Test
    public void testQueryAlerts() {
        final Http.RequestBuilder request = Helpers.fakeRequest(controllers.routes.AlertController.get(alertId));
        final Result result = Helpers.invokeWithContext(request, Helpers.contextComponents(),
                () -> _controller.get(alertId)
        );
        assertThat(result.status(), is(equalTo(HttpStatus.SC_OK)));
    }

    @Test
    public void testQueryAlertsPaginating() {
        final Http.RequestBuilder request = Helpers.fakeRequest(controllers.routes.AlertController.get(alertId));
        final Result result = Helpers.invokeWithContext(request, Helpers.contextComponents(),
                () -> _controller.get(alertId)
        );
        assertThat(result.status(), is(equalTo(HttpStatus.SC_OK)));
    }

    @Test
    public void testQueryAlertsFiltered() {

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
