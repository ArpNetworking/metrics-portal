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

package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.testing.SerializationTestUtils;
import com.arpnetworking.utility.test.ResourceHelper;
import com.google.common.collect.ImmutableMap;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.impl.DefaultOrganization;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.junit.Assert.fail;

/**
 * Unit Tests for {@link FileAlertRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class FileAlertRepositoryTest {
    private static final UUID ORGANIZATION_ID = UUID.fromString("fb04046e-689a-49a1-9468-e15c44587b4f");
    private static final UUID METADATA_ALERT_ID = UUID.fromString("0eca730a-5f9a-49db-8711-29a49cac98ff");

    private Organization _organization;
    private FileAlertRepository _repository;

    @Before
    public void setUp() throws URISyntaxException {
        final URL url = ResourceHelper.resourceURL(FileAlertRepositoryTest.class, "Alerts");

        // Organization is fixed because alerts IDs are namespaced by org.
        _organization = new DefaultOrganization.Builder()
                .setId(ORGANIZATION_ID)
                .build();
        _repository = new FileAlertRepository(
                SerializationTestUtils.getApiObjectMapper(),
                Paths.get(url.toURI()),
                _organization.getId()
        );
        _repository.open();
    }

    @After
    public void tearDown() {
        _repository.close();
    }

    @Test
    public void testUnknownOrganization() {
        final Organization org = TestBeanFactory.newOrganization();

        final Optional<Alert> alert = _repository.getAlert(METADATA_ALERT_ID, org);
        assertThat(alert, equalTo(Optional.empty()));

        final long alertCount = _repository.getAlertCount(org);
        assertThat(alertCount, equalTo(0L));

        final QueryResult<Alert> queryResult = _repository.createAlertQuery(org).execute();
        assertThat(queryResult.values(), empty());
        assertThat(queryResult.total(), equalTo(0L));
    }

    @Test
    public void testInvalidPathOnOpen() throws Exception {
        final Path path = Paths.get("valid_but_nonexistent");

        final FileAlertRepository badRepository = new FileAlertRepository(
                SerializationTestUtils.getApiObjectMapper(),
                path,
                _organization.getId()
        );

        try {
            badRepository.open();
            fail("Expected an exception while loading alerts.");
            // CHECKSTYLE.OFF: IllegalCatch
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            // expected
        }
    }

    @Test
    public void testGetAlert() {
        final UUID uuid = UUID.fromString("998ddc83-218b-5d46-9b02-cadc7389ed91");
        final String name = "BarIsTooLow";
        final String description = "You've set the bar too low.";
        final Map<String, Object> expectedMetadata = ImmutableMap.of(
                "externalFieldA", "A",
                "externalFieldB", ImmutableMap.of(
                        "externalFieldC", "C"
                )
        );

        // Get an alert with a UUID computed at read-time
        final Alert alert = _repository.getAlert(uuid, _organization).get();
        assertThat(alert.getId(), equalTo(uuid));
        assertThat(alert.getName(), equalTo(name));
        assertThat(alert.getDescription(), equalTo(description));
        assertThat(alert.getOrganization(), equalTo(_organization));
        assertThat(alert.getQuery(), not(nullValue()));
        assertThat(alert.getAdditionalMetadata(), is(anEmptyMap()));

        // Get an alert with a UUID
        final Alert metadataAlert = _repository.getAlert(METADATA_ALERT_ID, _organization).get();
        assertThat(metadataAlert.getId(), equalTo(METADATA_ALERT_ID));
        assertThat(metadataAlert.getAdditionalMetadata(), equalTo(expectedMetadata));
    }

    @Test
    public void testQueryAlertsContains() {
        final List<? extends Alert> alertsMatching =
                _repository.createAlertQuery(_organization)
                        .contains("quick brown fox")
                        .execute()
                        .values();
        assertThat(alertsMatching, hasSize(1));
        assertThat(alertsMatching.get(0).getDescription(), equalTo("The quick brown fox jumps over the lazy dog."));
    }

    @Test
    public void testQueryAlertsPaginate() {
        final int alertCount = 6;
        final int pageSize = 2;
        final int expectedPageCount = alertCount / pageSize;

        final List<? extends Alert> allAlerts = _repository.createAlertQuery(_organization).execute().values();
        assertThat(allAlerts, not(empty()));

        final List<Alert> paginatedAlerts = new ArrayList<>();
        for (int pageNo = 0; pageNo < expectedPageCount; pageNo++) {
            final int offset = pageNo * pageSize;

            final List<? extends Alert> alerts =
                    _repository.createAlertQuery(_organization)
                            .offset(offset)
                            .limit(pageSize)
                            .execute()
                            .values();

            assertThat(alerts, hasSize(pageSize));
            paginatedAlerts.addAll(alerts);
        }

        final List<? extends Alert> emptyPage =
                _repository.createAlertQuery(_organization)
                        .offset(expectedPageCount * pageSize)
                        .execute()
                        .values();
        assertThat(emptyPage, empty());

        assertThat(paginatedAlerts, containsInAnyOrder(allAlerts.toArray()));
    }

    @Test
    public void testGetAlertCount() {
        assertThat(_repository.getAlertCount(_organization), equalTo(6L));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDeleteAlert() {
        _repository.deleteAlert(METADATA_ALERT_ID, _organization);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddOrUpdateAlert() {
        final Alert alert = _repository.getAlert(METADATA_ALERT_ID, _organization).get();
        _repository.addOrUpdateAlert(alert, _organization);
    }
}
