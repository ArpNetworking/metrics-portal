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
import models.internal.Context;
import models.internal.NagiosExtension;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlertQuery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        assertFalse(_alertRepo.get(UUID.randomUUID(), TestBeanFactory.organizationFrom(UUID.randomUUID())).isPresent());
    }

    @Test
    public void testGetForNonexistentOrganizationId() {
        final models.ebean.Alert alert = TestBeanFactory.createEbeanAlert(_ebeanOrganization);
        _server.save(alert);

        assertFalse(_alertRepo.get(alert.getUuid(), TestBeanFactory.organizationFrom(UUID.randomUUID())).isPresent());
    }

    @Test
    public void testGetForNonexistentAlertId() {
        assertFalse(_alertRepo.get(UUID.randomUUID(), TestBeanFactory.organizationFrom(_ebeanOrganization)).isPresent());
    }

    @Test
    public void testGetForValidId() {
        final models.ebean.Alert alert = TestBeanFactory.createEbeanAlert(_ebeanOrganization);
        _server.save(alert);

        final Optional<Alert> actual = _alertRepo.get(alert.getUuid(), _organization);
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
        assertFalse(_alertRepo.get(uuid, _organization).isPresent());

        final Alert actualAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        _alertRepo.addOrUpdateAlert(actualAlert, _organization);

        final Optional<Alert> expected = _alertRepo.get(uuid, _organization);
        assertTrue(expected.isPresent());
        assertEquals(expected.get(), actualAlert);
    }

    @Test
    public void testAddAlertWithNoExtension() {
        final UUID uuid = UUID.randomUUID();
        final Alert alert = TestBeanFactory.createAlertBuilder()
                .setId(uuid)
                .setNagiosExtension(null)
                .build();
        _alertRepo.addOrUpdateAlert(alert, _organization);
        final Optional<Alert> expectedAlert = _alertRepo.get(uuid, _organization);
        assertTrue(expectedAlert.isPresent());
        assertNull(expectedAlert.get().getNagiosExtension());
    }

    @Test
    public void testQueryClauseWithClusterOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setCluster("my-test-cluster")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);

        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, _organization);
        successQuery.cluster(Optional.of("my-test-cluster"));

        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        assertEquals(1, successResult.total());

        final AlertQuery failQuery = new DefaultAlertQuery(_alertRepo, _organization);
        failQuery.cluster(Optional.of("some-random-cluster"));

        final QueryResult<Alert> failResult = _alertRepo.query(failQuery);
        assertEquals(0, failResult.total());
    }

    @Test
    public void testQueryClauseWithContextOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setContext(Context.CLUSTER)
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setContext(Context.HOST)
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);

        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, _organization);
        successQuery.context(Optional.of(Context.CLUSTER));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        assertEquals(1, successResult.total());
    }

    @Test
    public void testQueryClauseWithServiceOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setService("my-test-service")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);

        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, _organization);
        successQuery.service(Optional.of("my-test-service"));

        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        assertEquals(1, successResult.total());

        final AlertQuery failQuery = new DefaultAlertQuery(_alertRepo, _organization);
        failQuery.service(Optional.of("some-random-service"));

        final QueryResult<Alert> failResult = _alertRepo.query(failQuery);
        assertEquals(0, failResult.total());
    }

    @Test
    public void testQueryClauseWithContainsOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setService("my-contained-service")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setCluster("my-cluster")
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setName("my-contained-name")
                .build();
        final Alert alert4 = TestBeanFactory.createAlertBuilder()
                .setMetric("my-contained-metric")
                .build();
        final Alert alert5 = TestBeanFactory.createAlertBuilder()
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);
        _alertRepo.addOrUpdateAlert(alert3, _organization);
        _alertRepo.addOrUpdateAlert(alert4, _organization);
        _alertRepo.addOrUpdateAlert(alert5, _organization);

        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, _organization);
        successQuery.contains(Optional.of("contained"));

        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        assertEquals(3, successResult.total());
    }

    @Test
    public void testQueryClauseWithLimit() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);

        final AlertQuery query1 = new DefaultAlertQuery(_alertRepo, _organization);
        query1.service(Optional.of("my-test-service"));
        query1.cluster(Optional.of("my-test-cluster"));
        query1.limit(1);

        final QueryResult<Alert> result1 = _alertRepo.query(query1);
        assertEquals(1, result1.values().size());

        final AlertQuery query2 = new DefaultAlertQuery(_alertRepo, _organization);
        query2.service(Optional.of("my-test-service"));
        query2.cluster(Optional.of("my-test-cluster"));
        query2.limit(2);

        final QueryResult<Alert> result2 = _alertRepo.query(query2);
        assertEquals(2, result2.values().size());
    }

    @Test
    public void testQueryClauseWithOffsetAndLimit() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);
        _alertRepo.addOrUpdateAlert(alert3, _organization);

        final AlertQuery query = new DefaultAlertQuery(_alertRepo, _organization);
        query.service(Optional.of("my-test-service"));
        query.cluster(Optional.of("my-test-cluster"));
        query.offset(Optional.of(2));
        query.limit(2);

        final QueryResult<Alert> result = _alertRepo.query(query);
        assertEquals(1, result.values().size());
        assertEquals(alert3.getId(), result.values().get(0).getId());
    }

    @Test
    public void testQueryWithContainsAndClusterClause() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setMetric("my-contained-metric")
                .setCluster("my-cluster")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setService("my-contained-service")
                .setCluster("my-cluster")
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);
        _alertRepo.addOrUpdateAlert(alert3, _organization);

        final AlertQuery query = new DefaultAlertQuery(_alertRepo, _organization);
        query.contains(Optional.of("contained"));
        query.cluster(Optional.of("my-cluster"));

        final QueryResult<Alert> result = _alertRepo.query(query);
        assertEquals(2, result.values().size());
        assertEquals(alert1.getId(), result.values().get(0).getId());
        assertEquals(alert2.getId(), result.values().get(1).getId());
    }

    @Test
    public void testQueryWithContainsAndServiceClause() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setMetric("my-contained-metric")
                .setService("my-service")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setCluster("my-contained-cluster")
                .setService("my-service")
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .build();

        _alertRepo.addOrUpdateAlert(alert1, _organization);
        _alertRepo.addOrUpdateAlert(alert2, _organization);
        _alertRepo.addOrUpdateAlert(alert3, _organization);

        final AlertQuery query = new DefaultAlertQuery(_alertRepo, _organization);
        query.contains(Optional.of("contained"));
        query.service(Optional.of("my-service"));

        final QueryResult<Alert> result = _alertRepo.query(query);
        assertEquals(2, result.values().size());
        assertEquals(alert1.getId(), result.values().get(0).getId());
        assertEquals(alert2.getId(), result.values().get(1).getId());
    }

    private void assertAlertEbeanEquivalent(final Alert alert, final models.ebean.Alert ebeanAlert) {
        assertEquals(alert.getId(), ebeanAlert.getUuid());
        assertEquals(alert.getCluster(), ebeanAlert.getCluster());
        assertEquals(alert.getMetric(), ebeanAlert.getMetric());
        assertNagiosExtensionEbeanEquivalent(alert.getNagiosExtension(), ebeanAlert.getNagiosExtension());
        assertEquals(alert.getService(), ebeanAlert.getService());
        assertEquals(alert.getName(), ebeanAlert.getName());
        assertEquals(alert.getOperator(), ebeanAlert.getOperator());
        assertEquals(alert.getPeriod(), Duration.ofSeconds(ebeanAlert.getPeriod()));
        assertEquals(alert.getStatistic(), ebeanAlert.getStatistic());
        assertEquals(alert.getValue().getUnit(), Optional.of(ebeanAlert.getQuantityUnit()));
        assertEquals(alert.getValue().getValue(), ebeanAlert.getQuantityValue(), 0.001);
        assertEquals(alert.getContext(), ebeanAlert.getContext());
    }

    private static void assertNagiosExtensionEbeanEquivalent(
            @Nullable final NagiosExtension extension,
            @Nullable final models.ebean.NagiosExtension ebeanExtension) {
        if (extension == null && ebeanExtension == null) {
            return;
        } else if (extension == null || ebeanExtension == null) {
            fail("One of extension or ebeanExtension is null while the other is not");
        }
        assertEquals(extension.getSeverity(), ebeanExtension.getSeverity());
        assertEquals(extension.getNotify(), ebeanExtension.getNotify());
        assertEquals(extension.getMaxCheckAttempts(), ebeanExtension.getMaxCheckAttempts());
        assertEquals(extension.getFreshnessThreshold().getSeconds(), ebeanExtension.getFreshnessThreshold());
    }

    private EbeanServer _server;
    private DatabaseAlertRepository _alertRepo;
    private Organization _organization;
    private models.ebean.Organization _ebeanOrganization;
}
