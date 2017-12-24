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
package com.arpnetworking.metrics.portal.expressions.impl;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.internal.Expression;
import models.internal.ExpressionQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultExpressionQuery;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.PersistenceException;

/**
 * Tests <code>DatabaseExpressionRepository</code> class.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class DatabaseExpressionRepositoryTest extends WithApplication {

    @Before
    public void setup() {
        exprRepo.open();
    }

    @After
    public void teardown() {
        exprRepo.close();
    }

    @Test
    public void testGetForInvalidId() {
        Assert.assertFalse(exprRepo.get(UUID.randomUUID(), Organization.DEFAULT).isPresent());
    }

    @Test
    public void testGetForValidId() throws IOException {
        final UUID uuid = UUID.randomUUID();
        Assert.assertFalse(exprRepo.get(uuid, Organization.DEFAULT).isPresent());
        final models.ebean.Expression ebeanExpression = TestBeanFactory.createEbeanExpression();
        ebeanExpression.setUuid(uuid);
        ebeanExpression.setOrganization(models.ebean.Organization.findByOrganization(Organization.DEFAULT));
        try (Transaction transaction = Ebean.beginTransaction()) {
            Ebean.save(ebeanExpression);
            transaction.commit();
        }

        final Optional<Expression> expected = exprRepo.get(uuid, Organization.DEFAULT);
        Assert.assertTrue(expected.isPresent());
        Assert.assertTrue(isExpressionEbeanEquivalent(expected.get(), ebeanExpression));
    }

    @Test
    public void testGetExpressionCountWithNoExpr() {
        Assert.assertEquals(0, exprRepo.getExpressionCount(Organization.DEFAULT));
    }

    @Test
    public void testGetExpressionCountWithMultipleExpr() throws IOException {
        Assert.assertEquals(0, exprRepo.getExpressionCount(Organization.DEFAULT));
        try (Transaction transaction = Ebean.beginTransaction()) {
            final models.ebean.Expression ebeanExpression1 = TestBeanFactory.createEbeanExpression();
            ebeanExpression1.setUuid(UUID.randomUUID());
            ebeanExpression1.setOrganization(models.ebean.Organization.findByOrganization(Organization.DEFAULT));
            Ebean.save(ebeanExpression1);
            final models.ebean.Expression ebeanExpression2 = TestBeanFactory.createEbeanExpression();
            ebeanExpression2.setUuid(UUID.randomUUID());
            ebeanExpression2.setOrganization(models.ebean.Organization.findByOrganization(Organization.DEFAULT));
            Ebean.save(ebeanExpression2);
            transaction.commit();
        }
        Assert.assertEquals(2, exprRepo.getExpressionCount(Organization.DEFAULT));
    }

    @Test
    public void addOrUpdateExpressionAddCase() {
        final UUID uuid = UUID.randomUUID();
        Assert.assertFalse(exprRepo.get(uuid, Organization.DEFAULT).isPresent());
        final Expression actual = TestBeanFactory.createExpressionBuilder()
                .setId(uuid)
                .build();
        exprRepo.addOrUpdateExpression(actual, Organization.DEFAULT);
        final Optional<Expression> expected = exprRepo.get(uuid, Organization.DEFAULT);
        Assert.assertTrue(expected.isPresent());
        Assert.assertEquals(expected.get(), actual);
    }

    @Test
    public void addOrUpdateExpressionUpdateCase() throws IOException {
        final UUID uuid = UUID.randomUUID();
        final models.ebean.Organization organization;
        try (Transaction transaction = Ebean.beginTransaction()) {
            final models.ebean.Expression ebeanExpression = TestBeanFactory.createEbeanExpression();
            ebeanExpression.setUuid(uuid);
            organization = ebeanExpression.getOrganization();
            Ebean.save(ebeanExpression);
            transaction.commit();
        }
        final Expression actual = TestBeanFactory.createExpressionBuilder()
                .setId(uuid)
                .setCluster("new-cluster")
                .build();
        exprRepo.addOrUpdateExpression(actual, TestBeanFactory.organizationFrom(organization));
        final Expression expected = exprRepo.get(uuid, TestBeanFactory.organizationFrom(organization)).get();
        Assert.assertEquals(expected, actual);
    }

    @Test(expected = PersistenceException.class)
    public void testThrowsExceptionWhenQueryFails() {
        final UUID uuid = UUID.randomUUID();
        exprRepo.addOrUpdateExpression(TestBeanFactory.createExpressionBuilder()
                .setId(uuid)
                .setCluster("new-cluster")
                .build(), Organization.DEFAULT);
        models.ebean.Expression ebeanExpression1 = Ebean.find(models.ebean.Expression.class)
                .where()
                .eq("uuid", uuid)
                .findOne();
        models.ebean.Expression ebeanExpression2 = Ebean.find(models.ebean.Expression.class)
                .where()
                .eq("uuid", uuid)
                .findOne();
        try (Transaction transaction = Ebean.beginTransaction()) {
            ebeanExpression1.setCluster("new-cluster1");
            ebeanExpression2.setCluster("new-cluster2");
            Ebean.save(ebeanExpression2);
            Ebean.save(ebeanExpression1);
            transaction.commit();
        }
    }

    @Test
    public void testQueryClauseWithClusterOnly() {
        final Expression expr1 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setCluster("my-test-cluster")
                .build();
        final Expression expr2 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .build();
        exprRepo.addOrUpdateExpression(expr1, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr2, Organization.DEFAULT);
        final ExpressionQuery successQuery = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        successQuery.cluster(Optional.of("my-test-cluster"));
        final QueryResult<Expression> successResult = exprRepo.query(successQuery);
        Assert.assertEquals(1, successResult.total());
        final ExpressionQuery failQuery = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        failQuery.cluster(Optional.of("some-random-cluster"));
        final QueryResult<Expression> failResult = exprRepo.query(failQuery);
        Assert.assertEquals(0, failResult.total());
    }

    @Test
    public void testQueryClauseWithServiceOnly() {
        final Expression expr1 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .build();
        final Expression expr2 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .build();
        exprRepo.addOrUpdateExpression(expr1, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr2, Organization.DEFAULT);
        final ExpressionQuery successQuery = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        successQuery.service(Optional.of("my-test-service"));
        final QueryResult<Expression> successResult = exprRepo.query(successQuery);
        Assert.assertEquals(1, successResult.total());
        final ExpressionQuery failQuery = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        failQuery.cluster(Optional.of("some-random-service"));
        final QueryResult<Expression> failResult = exprRepo.query(failQuery);
        Assert.assertEquals(0, failResult.total());
    }

    @Test
    public void testQueryClauseWithLimit() {
        final Expression expr1 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Expression expr2 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        exprRepo.addOrUpdateExpression(expr1, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr2, Organization.DEFAULT);
        final ExpressionQuery query1 = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        query1.service(Optional.of("my-test-service"));
        query1.cluster(Optional.of("my-test-cluster"));
        query1.limit(1);
        final QueryResult<Expression> result1 = exprRepo.query(query1);
        Assert.assertEquals(1, result1.values().size());
        final ExpressionQuery query2 = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        query2.service(Optional.of("my-test-service"));
        query2.cluster(Optional.of("my-test-cluster"));
        query2.limit(2);
        final QueryResult<Expression> result2 = exprRepo.query(query2);
        Assert.assertEquals(2, result2.values().size());
    }

    @Test
    public void testQueryClauseWithOffsetAndLimit() {
        final Expression expr1 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Expression expr2 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Expression expr3 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        exprRepo.addOrUpdateExpression(expr1, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr2, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr3, Organization.DEFAULT);
        final ExpressionQuery query = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        query.service(Optional.of("my-test-service"));
        query.cluster(Optional.of("my-test-cluster"));
        query.offset(Optional.of(2));
        query.limit(2);
        final QueryResult<Expression> result = exprRepo.query(query);
        Assert.assertEquals(1, result.values().size());
        Assert.assertEquals(expr3.getId(), result.values().get(0).getId());
    }

    @Test
    public void testQueryWithOnlyContainsClause() {
        final Expression expr1 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setMetric("my-contained-metric")
                .build();
        final Expression expr2 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setScript("my-contained-script")
                .build();
        final Expression expr3 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setCluster("my-contained-cluster")
                .build();
        final Expression expr4 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .build();
        exprRepo.addOrUpdateExpression(expr1, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr2, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr3, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr4, Organization.DEFAULT);
        final ExpressionQuery query = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        query.contains(Optional.of("contained"));
        final QueryResult<Expression> result = exprRepo.query(query);
        Assert.assertEquals(3, result.values().size());
        Assert.assertEquals(expr1.getId(), result.values().get(0).getId());
        Assert.assertEquals(expr2.getId(), result.values().get(1).getId());
        Assert.assertEquals(expr3.getId(), result.values().get(2).getId());
    }

    @Test
    public void testQueryWithContainsAndClusterClause() {
        final Expression expr1 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setMetric("my-contained-metric")
                .setCluster("my-cluster")
                .build();
        final Expression expr2 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setScript("my-contained-script")
                .setCluster("my-cluster")
                .build();
        final Expression expr3 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .build();
        exprRepo.addOrUpdateExpression(expr1, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr2, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr3, Organization.DEFAULT);
        final ExpressionQuery query = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        query.contains(Optional.of("contained"));
        query.cluster(Optional.of("my-cluster"));
        final QueryResult<Expression> result = exprRepo.query(query);
        Assert.assertEquals(2, result.values().size());
        Assert.assertEquals(expr1.getId(), result.values().get(0).getId());
        Assert.assertEquals(expr2.getId(), result.values().get(1).getId());
    }

    @Test
    public void testQueryWithContainsAndServiceClause() {
        final Expression expr1 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setMetric("my-contained-metric")
                .setService("my-service")
                .build();
        final Expression expr2 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .setScript("my-contained-script")
                .setService("my-service")
                .build();
        final Expression expr3 = TestBeanFactory.createExpressionBuilder()
                .setId(UUID.randomUUID())
                .build();
        exprRepo.addOrUpdateExpression(expr1, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr2, Organization.DEFAULT);
        exprRepo.addOrUpdateExpression(expr3, Organization.DEFAULT);
        final ExpressionQuery query = new DefaultExpressionQuery(exprRepo, Organization.DEFAULT);
        query.contains(Optional.of("contained"));
        query.service(Optional.of("my-service"));
        final QueryResult<Expression> result = exprRepo.query(query);
        Assert.assertEquals(2, result.values().size());
        Assert.assertEquals(expr1.getId(), result.values().get(0).getId());
        Assert.assertEquals(expr2.getId(), result.values().get(1).getId());
    }

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }

    private boolean isExpressionEbeanEquivalent(
            final Expression expression,
            final models.ebean.Expression ebeanExpression) {
        return Objects.equals(expression.getId(), ebeanExpression.getUuid())
                && Objects.equals(expression.getCluster(), ebeanExpression.getCluster())
                && Objects.equals(expression.getMetric(), ebeanExpression.getMetric())
                && Objects.equals(expression.getScript(), ebeanExpression.getScript())
                && Objects.equals(expression.getService(), ebeanExpression.getService());
    }

    private final DatabaseExpressionRepository.ExpressionQueryGenerator queryGenerator = new DatabaseExpressionRepository.GenericQueryGenerator();
    private final DatabaseExpressionRepository exprRepo = new DatabaseExpressionRepository(queryGenerator);
}
