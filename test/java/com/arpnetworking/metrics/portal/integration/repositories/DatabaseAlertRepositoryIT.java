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
package com.arpnetworking.metrics.portal.integration.repositories;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.impl.DatabaseAlertRepository;
import com.arpnetworking.metrics.portal.integration.test.EbeanServerHelper;
import io.ebean.EbeanServer;
import io.ebean.Transaction;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlertQuery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@code DatabaseAlertRepository}.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public final class DatabaseAlertRepositoryIT {

    @Before
    public void setUp() {
        _server = EbeanServerHelper.getMetricsDatabase();
        _alertRepo = new DatabaseAlertRepository(_server);
        _alertRepo.open();

        _ebeanOrganization = TestBeanFactory.createEbeanOrganization();
        _server.save(_ebeanOrganization);
        _organization = TestBeanFactory.organizationFrom(_ebeanOrganization);
    }

    @After
    public void tearDown() {
        _alertRepo.close();
    }

    @Test
    public void testGetForNonexistentAlertAndOrganizationId() {
        assertFalse(_alertRepo.getAlert(UUID.randomUUID(), TestBeanFactory.organizationFrom(UUID.randomUUID())).isPresent());
    }

    @Test
    public void testGetForNonexistentOrganizationId() {
        final models.ebean.Alert alert = TestBeanFactory.createEbeanAlert(_ebeanOrganization);
        _server.save(alert);

        assertFalse(_alertRepo.getAlert(alert.getUuid(), TestBeanFactory.organizationFrom(UUID.randomUUID())).isPresent());
    }

    @Test
    public void testGetForNonexistentAlertId() {
        assertFalse(_alertRepo.getAlert(UUID.randomUUID(), TestBeanFactory.organizationFrom(_ebeanOrganization)).isPresent());
    }

    @Test
    public void testGetForValidId() {
        final models.ebean.Alert alert = TestBeanFactory.createEbeanAlert(_ebeanOrganization);
        _server.save(alert);

        final Optional<Alert> actual = _alertRepo.getAlert(alert.getUuid(), _organization);
        assertTrue(actual.isPresent());
        assertAlertEbeanEquivalent(actual.get(), alert);
    }

    @Test
    public void testGetAlertCount() {
        assertEquals(0, _alertRepo.getAlertCount(_organization));

        try (Transaction transaction = _server.beginTransaction()) {
            _server.save(TestBeanFactory.createEbeanAlert(_ebeanOrganization));
            _server.save(TestBeanFactory.createEbeanAlert(_ebeanOrganization));
            transaction.commit();
        }

        assertEquals(2, _alertRepo.getAlertCount(_organization));
    }

    @Test
    public void testAddOrUpdateAlertAddCase() {
        final UUID uuid = UUID.randomUUID();
        assertFalse(_alertRepo.getAlert(uuid, _organization).isPresent());

        final Alert actualAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        _alertRepo.addOrUpdateAlert(actualAlert, _organization);

        final Optional<Alert> expected = _alertRepo.getAlert(uuid, _organization);
        assertTrue(expected.isPresent());
        assertEquals(expected.get(), actualAlert);
    }

    @Test
    public void testQueryClauseWithContainsOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setQuery("select metric_contained_name")
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setName("my-contained-name")
                .build();
        final Alert alert4 = TestBeanFactory.createAlertBuilder()
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);
        _alertRepo.addOrUpdateAlert(alert3, _organization);
        _alertRepo.addOrUpdateAlert(alert4, _organization);

        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, _organization);
        successQuery.contains(Optional.of("contained"));

        final QueryResult<Alert> successResult = _alertRepo.queryAlerts(successQuery);
        assertEquals(2, successResult.total());
    }

    @Test
    public void testQueryClauseWithLimit() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);

        final AlertQuery query1 = new DefaultAlertQuery(_alertRepo, _organization);
        query1.limit(1);

        final QueryResult<Alert> result1 = _alertRepo.queryAlerts(query1);
        assertEquals(1, result1.values().size());

        final AlertQuery query2 = new DefaultAlertQuery(_alertRepo, _organization);
        query2.limit(2);

        final QueryResult<Alert> result2 = _alertRepo.queryAlerts(query2);
        assertEquals(2, result2.values().size());
    }

    @Test
    public void testQueryClauseWithOffsetAndLimit() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);
        _alertRepo.addOrUpdateAlert(alert3, _organization);

        final AlertQuery query = new DefaultAlertQuery(_alertRepo, _organization);
        query.offset(Optional.of(2));
        query.limit(2);

        final QueryResult<Alert> result = _alertRepo.queryAlerts(query);
        assertEquals(1, result.values().size());
        assertEquals(alert3.getId(), result.values().get(0).getId());
    }

    private void assertAlertEbeanEquivalent(final Alert alert, final models.ebean.Alert ebeanAlert) {
        assertEquals(alert.getId(), ebeanAlert.getUuid());
        assertEquals(alert.getName(), ebeanAlert.getName());
        assertEquals(alert.getCheckInterval(), Duration.ofSeconds(ebeanAlert.getPeriod()));
    }

    private EbeanServer _server;
    private DatabaseAlertRepository _alertRepo;
    private Organization _organization;
    private models.ebean.Organization _ebeanOrganization;
}
