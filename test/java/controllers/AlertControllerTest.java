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
import com.arpnetworking.metrics.portal.config.impl.PassthroughConfigProvider;
import com.arpnetworking.metrics.portal.integration.test.WebServerHelper;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.portal.organizations.impl.DefaultOrganizationRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobExecutionRepository;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.MetricsQueryFormat;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultAlertEvaluationResult;
import models.internal.impl.DefaultMetricsQuery;
import models.view.alerts.AlertFiringState;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import play.mvc.Result;
import play.test.Helpers;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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
@RunWith(Enclosed.class)
public final class AlertControllerTest {
    private static final Instant LAST_INTERVAL = Instant.now();

    private static final List<UUID> FIRING_IDS = Stream.of(
            "1e7acce3-cd88-47be-8312-c61ccf88ba07",
            "d2b1ba49-0819-498c-8089-08b8ff15b5bc",
            "d98798e1-b50d-47c9-ae34-a6aee074dab4",
            "d4d4f795-55be-4a2a-8d71-22dc2eb237c2",
            "bf560372-a853-4a9c-948c-9fe9f0269ad7"
    ).map(UUID::fromString).collect(ImmutableList.toImmutableList());

    private static final List<UUID> NOT_FIRING_IDS = Stream.of(
            "c6e274d9-d8c6-40e3-aea8-d1146c813c0f",
            "20adb3d5-4c0a-4b60-a4c1-819a86798bb1",
            "11dead9e-e5cc-427d-a3a5-381259353b4d",
            "09d7212f-06b8-4b91-a6f9-384350190cfc",
            "ce7347cd-9d05-44d1-ac36-f868a14b7c49"
    ).map(UUID::fromString).collect(ImmutableList.toImmutableList());

    private static final Map<String, Object> MOCK_METADATA = ImmutableMap.of(
            "foo", "bar",
            "bar", ImmutableMap.of("baz", "qux"),
            "baz", 1
    );
    private static final ObjectMapper OBJECT_MAPPER = SerializationTestUtils.getApiObjectMapper();

    /**
     * Set up and tear down shared across all test cases.
     */
    public abstract static class SharedSetup {
        protected Organization _organization;
        protected AlertController _controller;

        @Before
        public void setUp() {
            final OrganizationRepository organizationRepository = new DefaultOrganizationRepository();
            organizationRepository.open();
            final QueryResult<Organization> queryResult = organizationRepository.query(organizationRepository.createQuery().limit(
                    1));
            _organization = queryResult.values().stream().findFirst().orElse(null);
            assertThat(_organization, is(notNullValue()));

            final AlertExecutionRepository alertExecutionRepository = new MapExecutionRepository();
            alertExecutionRepository.open();

            final ImmutableList<Alert> mockAlerts = populateAlertExecutions(alertExecutionRepository);
            final PassthroughConfigProvider configProvider = new PassthroughConfigProvider(
                    PluggableAlertRepository.AlertGroup.fromInternal(mockAlerts),
                    OBJECT_MAPPER
            );
            final AlertRepository alertRepository = new PluggableAlertRepository(
                    OBJECT_MAPPER,
                    configProvider,
                    _organization.getId()
            );
            alertRepository.open();

            _controller = new AlertController(
                    alertRepository,
                    alertExecutionRepository,
                    organizationRepository
            );
        }

        private ImmutableList<Alert> populateAlertExecutions(final AlertExecutionRepository alertExecutionRepository) {
            final ImmutableList.Builder<Alert> mockAlerts = new ImmutableList.Builder<>();
            for (UUID alertId : NOT_FIRING_IDS) {
                final Alert alert = mockAlert(alertId);
                mockAlerts.add(alert);
                final AlertEvaluationResult firingResult = new DefaultAlertEvaluationResult.Builder()
                        .setSeriesName(alert.getName())
                        .setFiringTags(ImmutableList.of())
                        .build();
                alertExecutionRepository.jobSucceeded(alert.getId(), _organization, LAST_INTERVAL, firingResult);
            }
            for (UUID alertId : FIRING_IDS) {
                final Alert alert = mockAlert(alertId);
                mockAlerts.add(alert);
                final AlertEvaluationResult firingResult = new DefaultAlertEvaluationResult.Builder()
                        .setSeriesName(alert.getName())
                        .setFiringTags(ImmutableList.of(ImmutableMap.of("tag", "value")))
                        .build();
                alertExecutionRepository.jobSucceeded(alert.getId(), _organization, LAST_INTERVAL, firingResult);
            }
            return mockAlerts.build();
        }

        private Alert mockAlert(final UUID alertId) {
            return new DefaultAlert.Builder()
                    .setId(alertId)
                    .setName("Fake name")
                    .setEnabled(true)
                    .setDescription("fake alert")
                    .setOrganization(_organization)
                    .setQuery(
                            new DefaultMetricsQuery.Builder()
                                    .setFormat(MetricsQueryFormat.KAIROS_DB)
                                    .setQuery("{\"test query\": \"not evaluated\"}")
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
            final UUID alertId = NOT_FIRING_IDS.get(0);
            final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(),
                    Helpers.contextComponents(),
                    () -> _controller.get(alertId));
            assertThat(result.status(), is(equalTo(Helpers.OK)));

            final models.view.alerts.Alert alert = WebServerHelper.readContentAs(result, models.view.alerts.Alert.class);
            assertThat(alert.getId(), is(equalTo(alertId)));
            assertThat(alert.getDescription(), is(notNullValue()));
            assertThat(alert.getName(), is(notNullValue()));
            assertThat(alert.getAdditionalMetadata(), is(equalTo(MOCK_METADATA)));

            assertThat(alert.getFiringState().isPresent(), is(true));
            final AlertFiringState firingState = alert.getFiringState().get();
            assertThat(firingState.getFiringTags(), is(empty()));
            assertThat(firingState.getLastEvaluatedAt().get(), is(greaterThan(LAST_INTERVAL)));
        }

        @Test
        public void testGetFiringAlert() throws IOException {
            final UUID alertId = FIRING_IDS.get(0);
            final Result result = Helpers.invokeWithContext(Helpers.fakeRequest(),
                    Helpers.contextComponents(),
                    () -> _controller.get(alertId));
            assertThat(result.status(), is(equalTo(Helpers.OK)));

            final models.view.alerts.Alert alert = WebServerHelper.readContentAs(result, models.view.alerts.Alert.class);
            assertThat(alert.getId(), is(equalTo(alertId)));
            assertThat(alert.getDescription(), is(notNullValue()));
            assertThat(alert.getName(), is(notNullValue()));
            assertThat(alert.getAdditionalMetadata(), is(equalTo(MOCK_METADATA)));

            assertThat(alert.getFiringState().isPresent(), is(true));
            final AlertFiringState firingState = alert.getFiringState().get();
            assertThat(firingState.getFiringTags(), is(not(empty())));
            assertThat(firingState.getLastEvaluatedAt().get(), is(greaterThan(LAST_INTERVAL)));
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
     * An AlertExecutionRepository backed by a {@link Map}.
     */
    private static class MapExecutionRepository extends MapJobExecutionRepository<AlertEvaluationResult>
            implements AlertExecutionRepository {}
}
