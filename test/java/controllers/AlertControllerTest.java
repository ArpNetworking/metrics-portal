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
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import play.mvc.Result;
import play.test.Helpers;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
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
@RunWith(Enclosed.class)
public final class AlertControllerTest {
    private static final int ALERT_PAGE_LIMIT = 1000;
    private static final Instant lastInterval = Instant.now();

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

    private static final Map<String, Object> MOCK_METADATA = ImmutableMap.of(
            "foo", "bar",
            "bar", ImmutableMap.of("baz", "qux"),
            "baz", 1
    );
    private static final ObjectMapper objectMapper = SerializationTestUtils.getApiObjectMapper();

    /**
     * Set up and tear down shared across all test cases.
     */
    public abstract static class SharedSetup {
        protected Organization organization;
        protected AlertController _controller;

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

    }

    /**
     * Tests for the {@link AlertController#get} endpoint.
     */
    public static final class TestGetAlert extends SharedSetup {
        @Test
        public void testGetNotFiringAlert() throws IOException {
            final UUID alertId = notFiringAlertIds.get(0);
            final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(),
                    Helpers.contextComponents(),
                    () -> _controller.get(alertId));
            assertThat(result.status(), is(equalTo(Helpers.OK)));

            final models.view.alerts.Alert alert = WebServerHelper.readContentAs(result, models.view.alerts.Alert.class);
            assertThat(alert.getId(), is(equalTo(alertId)));
            assertThat(alert.getDescription(), is(notNullValue()));
            assertThat(alert.getName(), is(notNullValue()));
            assertThat(alert.getAdditionalMetadata(), is(equalTo(MOCK_METADATA)));
            assertThat(alert.getFiringState(), is(notNullValue()));
            assertThat(alert.getFiringState().getFiringTags(), is(empty()));
            assertThat(alert.getFiringState().getLastEvaluatedAt(), is(greaterThan(lastInterval)));
        }

        @Test
        public void testGetFiringAlert() throws IOException {
            final UUID alertId = firingAlertIds.get(0);
            final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(),
                    Helpers.contextComponents(),
                    () -> _controller.get(alertId));
            assertThat(result.status(), is(equalTo(Helpers.OK)));

            final models.view.alerts.Alert alert = WebServerHelper.readContentAs(result, models.view.alerts.Alert.class);
            assertThat(alert.getId(), is(equalTo(alertId)));
            assertThat(alert.getDescription(), is(notNullValue()));
            assertThat(alert.getName(), is(notNullValue()));
            assertThat(alert.getAdditionalMetadata(), is(equalTo(MOCK_METADATA)));
            assertThat(alert.getFiringState(), is(notNullValue()));
            assertThat(alert.getFiringState().getFiringTags(), is(not(empty())));
            assertThat(alert.getFiringState().getLastEvaluatedAt(), is(greaterThan(lastInterval)));
        }

        @Test
        public void testGetAlertNotFound() {
            final UUID unknownId = UUID.randomUUID();
            final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(),
                    Helpers.contextComponents(),
                    () -> _controller.get(unknownId));
            assertThat(result.status(), is(equalTo(Helpers.NOT_FOUND)));
        }

    }

    /**
     * Tests for the {@link AlertController#query} endpoint.
     *
     * These tests are parameterized by the alert firing filter.
     */
    @RunWith(Parameterized.class)
    public static final class TestQueryAlerts extends SharedSetup {

        @Nullable
        private final Boolean _filter;
        private final int _expectedAlertCount;

        public TestQueryAlerts(
                @Nullable final Boolean filter,
                final int expectedAlertCount
        ) {
            _filter = filter;
            _expectedAlertCount = expectedAlertCount;
        }

        @Parameterized.Parameters(name = "Query firing={0}")
        public static Collection<Object[]> values() {
            return Arrays.asList(new Object[][]{
                    // FIXME: This should really have the filtered total as
                    // another parameter, but  right now we cannot know that value
                    // up front that since the filter / join is done on the fly
                    // within AlertController
                    {null, TOTAL_ALERT_COUNT},
                    {true, firingAlertIds.size()},
                    {false, notFiringAlertIds.size()},
            });
        }

        @Test
        public void testQueryAlertsSinglePage() throws Exception {
            final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(), Helpers.contextComponents(),
                    () -> _controller.query(ALERT_PAGE_LIMIT, 0, _filter)
            );
            assertThat(result.status(), is(equalTo(Helpers.OK)));
            final JsonNode page = WebServerHelper.readContentAsJson(result);
            assertThat(page.get("pagination").get("size").asInt(), is(equalTo(TOTAL_ALERT_COUNT)));
            assertThat(page.get("pagination").get("total").asInt(), is(equalTo(TOTAL_ALERT_COUNT)));

            final List<UUID> retrieved =
                    Stream.of(objectMapper.treeToValue(page.get("data"), models.view.alerts.Alert[].class))
                            .map(models.view.alerts.Alert::getId)
                            .collect(ImmutableList.toImmutableList());

            assertThat(retrieved.size(), is(equalTo(_expectedAlertCount)));
            if (_filter == null || _filter) {
                assertThat(firingAlertIds, everyItem(is(in(retrieved))));
            }
            if (_filter == null || !_filter) {
                assertThat(notFiringAlertIds, everyItem(is(in(retrieved))));
            }
        }

        @Test
        public void testQueryAlertsPaginating() throws IOException {
            final int pageSize = 2;
            final int numPages = TOTAL_ALERT_COUNT / pageSize + 1;

            final ImmutableSet.Builder<UUID> retrievedBuilder = new ImmutableSet.Builder<>();
            for (int pageNo = 0; pageNo < numPages; pageNo++) {
                final int offset = pageNo * pageSize;
                final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(), Helpers.contextComponents(),
                        () -> _controller.query(pageSize, offset, _filter)
                );
                final JsonNode page = WebServerHelper.readContentAsJson(result);
                assertThat(page.get("pagination").get("total").asInt(), is(equalTo(TOTAL_ALERT_COUNT)));
                assertThat(page.get("pagination").get("size").asInt(), is(lessThanOrEqualTo(pageSize)));
                Stream.of(objectMapper.treeToValue(page.get("data"), models.view.alerts.Alert[].class))
                        .map(models.view.alerts.Alert::getId)
                        .forEach(retrievedBuilder::add);
                assertThat(result.status(), is(equalTo(Helpers.OK)));
            }
            final ImmutableSet<UUID> retrieved = retrievedBuilder.build();
            assertThat(retrieved.size(), is(equalTo(_expectedAlertCount)));
            if (_filter == null || _filter) {
                assertThat(firingAlertIds, everyItem(is(in(retrieved))));
            }
            if (_filter == null || !_filter) {
                assertThat(notFiringAlertIds, everyItem(is(in(retrieved))));
            }
        }
    }

    /**
     * An AlertExecutionRepository backed by a {@link Map}.
     */
    private static class MapExecutionRepository extends MapJobExecutionRepository<AlertEvaluationResult>
            implements AlertExecutionRepository {}
}
