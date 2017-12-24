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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.NagiosExtension;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlertQuery;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.PersistenceException;

/**
 * Tests class <code>DatabaseAlertRepository</code>.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class DatabaseAlertRepositoryTest extends WithApplication {

    @Before
    public void setup() {
        alertRepo.open();
    }

    @After
    public void teardown() {
        alertRepo.close();
    }

    @Test
    public void testGetForInvalidId() {
        Assert.assertFalse(alertRepo.get(UUID.randomUUID(), Organization.DEFAULT).isPresent());
    }

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }

    @Test
    public void testGetForValidId() throws IOException {
        final UUID uuid = UUID.randomUUID();
        final models.ebean.Alert ebeanAlert = TestBeanFactory.createEbeanAlert();
        final Organization org = TestBeanFactory.organizationFrom(ebeanAlert.getOrganization());
        Assert.assertFalse(alertRepo.get(uuid, org).isPresent());
        ebeanAlert.setUuid(uuid);
        try (Transaction transaction = Ebean.beginTransaction()) {
            Ebean.save(ebeanAlert);
            transaction.commit();
        }
        final Optional<Alert> expected = alertRepo.get(uuid, org);
        Assert.assertTrue(expected.isPresent());
        assertAlertEbeanEquivalent(expected.get(), ebeanAlert);
    }

    @Test
    public void testGetAlertCountWithNoAlert() {
        Assert.assertEquals(0, alertRepo.getAlertCount(Organization.DEFAULT));
    }

    @Test
    public void testGetAlertCountWithMultipleAlert() throws JsonProcessingException {
        Assert.assertEquals(0, alertRepo.getAlertCount(Organization.DEFAULT));
        final Transaction transaction = Ebean.beginTransaction();
        final Organization org;
        try {
            final models.ebean.Alert ebeanAlert1 = TestBeanFactory.createEbeanAlert();
            ebeanAlert1.setUuid(UUID.randomUUID());
            Ebean.save(ebeanAlert1);
            final models.ebean.Alert ebeanAlert2 = TestBeanFactory.createEbeanAlert();
            ebeanAlert2.setUuid(UUID.randomUUID());
            ebeanAlert2.setOrganization(ebeanAlert1.getOrganization());
            Ebean.save(ebeanAlert2);
            org = TestBeanFactory.organizationFrom(ebeanAlert1.getOrganization());
            transaction.commit();
        } finally {
            transaction.end();
        }
        Assert.assertEquals(2, alertRepo.getAlertCount(org));
    }

    @Test
    public void testAddOrUpdateAlertAddCase() {
        final UUID uuid = UUID.randomUUID();
        Assert.assertFalse(alertRepo.get(uuid, Organization.DEFAULT).isPresent());
        final Alert actualAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        alertRepo.addOrUpdateAlert(actualAlert, Organization.DEFAULT);
        final Optional<Alert> expected = alertRepo.get(uuid, Organization.DEFAULT);
        Assert.assertTrue(expected.isPresent());
        Assert.assertEquals(expected.get(), actualAlert);
    }

    @Test
    public void testAddAlertWithNoExtension() {
        final UUID uuid = UUID.randomUUID();
        final Alert alert = TestBeanFactory.createAlertBuilder()
                .setId(uuid)
                .setNagiosExtension(null)
                .build();
        alertRepo.addOrUpdateAlert(alert, Organization.DEFAULT);
        final Alert expectedAlert = alertRepo.get(uuid, Organization.DEFAULT).get();
        Assert.assertNull(expectedAlert.getNagiosExtension());
    }

    @Test(expected = PersistenceException.class)
    public void testThrowsExceptionWhenQueryFails() throws IOException {
        final UUID uuid = UUID.randomUUID();
        alertRepo.addOrUpdateAlert(TestBeanFactory.createAlertBuilder()
                .setId(uuid)
                .setQuery("select my_metric")
                .build(), Organization.DEFAULT);
        models.ebean.Alert ebeanAlert1 = Ebean.find(models.ebean.Alert.class)
                .where()
                .eq("uuid", uuid)
                .findOne();
        models.ebean.Alert ebeanAlert2 = Ebean.find(models.ebean.Alert.class)
                .where()
                .eq("uuid", uuid)
                .findOne();
        try (Transaction transaction = Ebean.beginTransaction()) {
            ebeanAlert1.setQuery("select other_metric");
            ebeanAlert2.setQuery("select this_metric");
            Ebean.save(ebeanAlert2);
            Ebean.save(ebeanAlert1);
            transaction.commit();
        }
    }

    @Test
    public void testQueryClauseWithContainsOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setQuery("select metric_contained_name")
                .setId(UUID.randomUUID())
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setName("my-contained-name")
                .setId(UUID.randomUUID())
                .build();
        final Alert alert4 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert4, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        successQuery.contains(Optional.of("contained"));
        final QueryResult<Alert> successResult = alertRepo.query(successQuery);
        Assert.assertEquals(2, successResult.total());
    }

    @Test
    public void testQueryClauseWithLimit() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery query1 = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        query1.limit(1);
        final QueryResult<Alert> result1 = alertRepo.query(query1);
        Assert.assertEquals(1, result1.values().size());
        final AlertQuery query2 = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        query2.limit(2);
        final QueryResult<Alert> result2 = alertRepo.query(query2);
        Assert.assertEquals(2, result2.values().size());
    }

    @Test
    public void testQueryClauseWithOffsetAndLimit() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        final AlertQuery query = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        query.offset(Optional.of(2));
        query.limit(2);
        final QueryResult<Alert> result = alertRepo.query(query);
        Assert.assertEquals(1, result.values().size());
        Assert.assertEquals(alert3.getId(), result.values().get(0).getId());
    }

    private void assertAlertEbeanEquivalent(final Alert alert, final models.ebean.Alert ebeanAlert) {
        Assert.assertEquals(alert.getId(), ebeanAlert.getUuid());
        assertNagiosExtensionEbeanEquivalent(alert.getNagiosExtension(), ebeanAlert.getNagiosExtension());
        Assert.assertEquals(alert.getName(), ebeanAlert.getName());
        Assert.assertEquals(alert.getPeriod(), Period.seconds(ebeanAlert.getPeriod()).normalizedStandard());
    }

    private static void assertNagiosExtensionEbeanEquivalent(
            final NagiosExtension extension,
            final models.ebean.NagiosExtension ebeanExtension) {
        Assert.assertEquals(extension.getSeverity(), ebeanExtension.getSeverity());
        Assert.assertEquals(extension.getNotify(), ebeanExtension.getNotify());
        Assert.assertEquals(extension.getMaxCheckAttempts(), ebeanExtension.getMaxCheckAttempts());
        Assert.assertEquals(extension.getFreshnessThreshold().getStandardSeconds(), ebeanExtension.getFreshnessThreshold());
    }

    private final DatabaseAlertRepository.AlertQueryGenerator queryGenerator = new DatabaseAlertRepository.GenericQueryGenerator();
    private final DatabaseAlertRepository alertRepo = new DatabaseAlertRepository(queryGenerator);
}
