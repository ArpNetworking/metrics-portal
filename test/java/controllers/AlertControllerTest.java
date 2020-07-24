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
import com.arpnetworking.metrics.portal.integration.test.WebServerHelper;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.portal.organizations.impl.DefaultOrganizationRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobExecutionRepository;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.ConfigFactory;
import models.internal.MetricsQueryFormat;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultAlertEvaluationResult;
import models.internal.impl.DefaultMetricsQuery;
import models.view.PagedContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.mvc.Result;
import play.test.Helpers;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Unit tests for {@code AlertController}.
 * <p>
 * This should be refactored into an integration/functional test once we can expose an API for alert creation.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertControllerTest {
    private static final int ALERT_PAGE_LIMIT = 10;
    private static final Instant lastInterval = Instant.now();
    private static final ObjectMapper objectMapper = SerializationTestUtils.getApiObjectMapper();
    private static final List<UUID> firingAlertIds = Stream.of(
            "1e7acce3-cd88-47be-8312-c61ccf88ba07",
            "d2b1ba49-0819-498c-8089-08b8ff15b5bc",
            "d98798e1-b50d-47c9-ae34-a6aee074dab4",
            "d4d4f795-55be-4a2a-8d71-22dc2eb237c2",
            "bf560372-a853-4a9c-948c-9fe9f0269ad7"
    ).map(UUID::fromString).collect(ImmutableList.toImmutableList());
    private static final List<UUID> notFiringAlertIds = Stream.of(
            "c6e274d9-d8c6-40e3-aea8-d1146c813c0f",
            "20adb3d5-4c0a-4b60-a4c1-819a86798bb1",
            "11dead9e-e5cc-427d-a3a5-381259353b4d",
            "09d7212f-06b8-4b91-a6f9-384350190cfc",
            "ce7347cd-9d05-44d1-ac36-f868a14b7c49"
    ).map(UUID::fromString).collect(ImmutableList.toImmutableList());
    private static final int TOTAL_ALERT_COUNT = firingAlertIds.size() + notFiringAlertIds.size();
    final TypeReference<PagedContainer<models.view.alerts.Alert>> PAGE_TYPE_REFERENCE =
            new TypeReference<PagedContainer<models.view.alerts.Alert>>() {};
    private final Map<String, Object> MOCK_METADATA = ImmutableMap.of(
            "foo", "bar",
            "bar", ImmutableMap.of("baz", "qux"),
            "baz", 1
    );
    private Organization organization;
    private AlertController _controller;

    @Before
    public void setUp() {
        final OrganizationRepository organizationRepository = new DefaultOrganizationRepository();
        organizationRepository.open();
        final QueryResult<Organization> queryResult = organizationRepository.query(organizationRepository.createQuery().limit(
                1));
        organization = queryResult.values().stream().findFirst().orElse(null);
        assertThat(organization, is(notNullValue()));

        final AlertExecutionRepository alertExecutionRepository = new MapExecutionRepository();
        alertExecutionRepository.open();

        final Map<UUID, Alert> mockAlerts = populateAlertDatabases(alertExecutionRepository);
        final AlertRepository alertRepository = new PluggableAlertRepository(
                objectMapper,
                organization.getId(),
                mockAlerts
        );
        alertRepository.open();

        _controller = new AlertController(
                ConfigFactory.parseMap(ImmutableMap.of(
                        "alerts.limit", ALERT_PAGE_LIMIT
                )),
                alertRepository,
                alertExecutionRepository,
                organizationRepository
        );
    }

    private Map<UUID, Alert> populateAlertDatabases(final AlertExecutionRepository alertExecutionRepository) {
        final Map<UUID, Alert> mockAlerts =
                Stream.concat(firingAlertIds.stream(), notFiringAlertIds.stream())
                        .map(this::mockAlert)
                        .collect(ImmutableMap.toImmutableMap(
                                Alert::getId,
                                Function.identity()
                        ));
        for (UUID firingId : notFiringAlertIds) {
            final Alert alert = mockAlerts.get(firingId);
            final AlertEvaluationResult firingResult = new DefaultAlertEvaluationResult.Builder()
                    .setSeriesName(alert.getName())
                    .setFiringTags(ImmutableList.of())
                    .build();
            alertExecutionRepository.jobSucceeded(alert.getId(), organization, lastInterval, firingResult);
        }
        for (UUID firingId : firingAlertIds) {
            final Alert alert = mockAlerts.get(firingId);
            final AlertEvaluationResult firingResult = new DefaultAlertEvaluationResult.Builder()
                    .setSeriesName(alert.getName())
                    .setFiringTags(ImmutableList.of(ImmutableMap.of("tag", "value")))
                    .build();
            alertExecutionRepository.jobSucceeded(alert.getId(), organization, lastInterval, firingResult);
        }
        return mockAlerts;
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGetAlert() {
        final UUID alertId = notFiringAlertIds.get(0);
        final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(),
                Helpers.contextComponents(),
                () -> _controller.get(alertId));
        assertThat(result.status(), is(equalTo(Helpers.OK)));
    }

    @Test
    public void testGetAlertNotFound() {
        final UUID unknownId = UUID.randomUUID();
        final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(),
                Helpers.contextComponents(),
                () -> _controller.get(unknownId));
        assertThat(result.status(), is(equalTo(Helpers.NOT_FOUND)));
    }

    @Test
    public void testQueryAlertsNoFilter() throws Exception {
        final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(), Helpers.contextComponents(),
                () -> _controller.query(TOTAL_ALERT_COUNT, 0, null)
        );
        assertThat(result.status(), is(equalTo(Helpers.OK)));
        final JsonNode page = WebServerHelper.readContentAsJson(result);
        assertThat(page.get("pagination").get("size").asInt(), is(equalTo(TOTAL_ALERT_COUNT)));
        assertThat(page.get("pagination").get("total").asInt(), is(equalTo(TOTAL_ALERT_COUNT)));

        final List<UUID> retrieved =
                Stream.of(objectMapper.treeToValue(page.get("data"), models.view.alerts.Alert[].class))
                        .map(models.view.alerts.Alert::getId)
                        .collect(ImmutableList.toImmutableList());

        assertThat(retrieved.size(), is(equalTo(TOTAL_ALERT_COUNT)));
        assertThat(notFiringAlertIds, everyItem(is(in(retrieved))));
        assertThat(firingAlertIds, everyItem(is(in(retrieved))));
    }

    @Test
    public void testQueryAlertsFilterFiring() throws IOException {
        final int FIRING_ALERT_COUNT = firingAlertIds.size();
        final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(), Helpers.contextComponents(),
                () -> _controller.query(TOTAL_ALERT_COUNT, 0, true)
        );
        assertThat(result.status(), is(equalTo(Helpers.OK)));
        final JsonNode page = WebServerHelper.readContentAsJson(result);
        assertThat(page.get("pagination").get("size").asInt(), is(equalTo(TOTAL_ALERT_COUNT)));
        // FIXME: Should be only firing count.
        assertThat(page.get("pagination").get("total").asInt(), is(equalTo(TOTAL_ALERT_COUNT)));

        final List<UUID> retrieved =
                Stream.of(objectMapper.treeToValue(page.get("data"), models.view.alerts.Alert[].class))
                        .map(models.view.alerts.Alert::getId)
                        .collect(ImmutableList.toImmutableList());

        assertThat(retrieved.size(), is(equalTo(FIRING_ALERT_COUNT)));
        assertThat(notFiringAlertIds, everyItem(is(not(in(retrieved)))));
        assertThat(firingAlertIds, everyItem(is(in(retrieved))));
    }

    @Test
    public void testQueryAlertsPaginating() throws IOException {
        final int pageSize = 2;
        final int numPages = TOTAL_ALERT_COUNT / pageSize + 1;

        final ImmutableSet.Builder<UUID> retrievedBuilder = new ImmutableSet.Builder<>();
        for (int pageNo = 0; pageNo < numPages; pageNo++) {
            final int offset = pageNo * pageSize;
            final int expectedPageSize = offset >= TOTAL_ALERT_COUNT ? 0 : pageSize;
            final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(), Helpers.contextComponents(),
                    () -> _controller.query(pageSize, offset, null)
            );
            final JsonNode page = WebServerHelper.readContentAsJson(result);
            assertThat(page.get("pagination").get("total").asInt(), is(equalTo(TOTAL_ALERT_COUNT)));
            assertThat(page.get("pagination").get("size").asInt(), is(equalTo(expectedPageSize)));
            Stream.of(objectMapper.treeToValue(page.get("data"), models.view.alerts.Alert[].class))
                    .map(models.view.alerts.Alert::getId)
                    .forEach(retrievedBuilder::add);
            assertThat(result.status(), is(equalTo(Helpers.OK)));
        }
        final ImmutableSet<UUID> retrieved = retrievedBuilder.build();
        assertThat(retrieved.size(), is(equalTo(TOTAL_ALERT_COUNT)));
        assertThat(notFiringAlertIds, everyItem(is(in(retrieved))));
        assertThat(firingAlertIds, everyItem(is(in(retrieved))));
    }

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
                .setAdditionalMetadata(MOCK_METADATA)
                .build();
    }

    private static class MapExecutionRepository extends MapJobExecutionRepository<AlertEvaluationResult>
            implements AlertExecutionRepository {}
}
