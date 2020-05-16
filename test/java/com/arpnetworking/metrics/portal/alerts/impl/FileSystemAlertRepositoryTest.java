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
import junit.framework.TestCase;
import models.internal.Organization;
import models.internal.alerts.Alert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
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
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;

public class FileSystemAlertRepositoryTest {
    private final static UUID METADATA_ALERT = UUID.fromString("0eca730a-5f9a-49db-8711-29a49cac98ff");
    private static final Map<String, Object> EXPECTED_METADATA = ImmutableMap.of(
        "externalFieldA", "A",
        "externalFieldB", ImmutableMap.of(
                "externalFieldC", "C"
            )
    );

    private Organization _organization;
    private FileSystemAlertRepository _repository;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        final URL url = ResourceHelper.resourceURL(FileSystemAlertRepositoryTest.class, "Alerts");
        _organization = TestBeanFactory.createOrganization();
        _repository = new FileSystemAlertRepository(
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
    public void testInvalidPathOnOpen() {
        final FileSystemAlertRepository _badRepository = new FileSystemAlertRepository(
                SerializationTestUtils.getApiObjectMapper(),
                Paths.get("not-a-real-path"),
                _organization.getId()
        );

        try {
            _badRepository.open();
            TestCase.fail("Expected an exception while loading alerts.");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void testGetAlert() {
        final UUID uuid = UUID.fromString("a211febc-1d38-34f2-8628-d12cc013296c");
        final String name = "BarIsTooLow";
        final String description = "You've set the bar too low.";

        // Get an alert with a UUID computed at read-time
        final Alert alert = _repository.getAlert(uuid, _organization).get();
        assertThat(alert.getId(), equalTo(uuid));
        assertThat(alert.getName(), equalTo(name));
        assertThat(alert.getDescription(), equalTo(description));
        assertThat(alert.getOrganization(), equalTo(_organization));
        assertThat(alert.getQuery(), not(nullValue()));
        assertThat(alert.getAdditionalMetadata(), is(anEmptyMap()));

        // Get an alert with a UUID
        final Alert metadataAlert = _repository.getAlert(METADATA_ALERT, _organization).get();
        assertThat(metadataAlert.getId(), equalTo(METADATA_ALERT));
        assertThat(metadataAlert.getAdditionalMetadata(), equalTo(EXPECTED_METADATA));
    }

    @Test
    public void testQueryAlerts() {
        final List<? extends Alert> allAlerts = _repository.createAlertQuery(_organization).execute().values();
        assertThat(allAlerts, not(empty()));

        final List<? extends Alert> alertsMatching =
                _repository.createAlertQuery(_organization)
                        .contains(Optional.of("quick brown fox"))
                        .execute()
                        .values();
        assertThat(alertsMatching, hasSize(1));
        assertThat(alertsMatching.get(0).getDescription(), equalTo("The quick brown fox jumps over the lazy dog."));
    }

    @Test
    public void testGetAlertCount() {
        assertThat(_repository.getAlertCount(_organization), equalTo(6L));
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testDeleteAlert() {
        _repository.deleteAlert(METADATA_ALERT, _organization);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testAddOrUpdateAlert() {
        final Alert alert = _repository.getAlert(METADATA_ALERT, _organization).get();
        _repository.addOrUpdateAlert(alert, _organization);
    }
}