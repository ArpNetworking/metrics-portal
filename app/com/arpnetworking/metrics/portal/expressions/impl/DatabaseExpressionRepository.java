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

import com.arpnetworking.metrics.portal.expressions.ExpressionRepository;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import io.ebean.Ebean;
import io.ebean.ExpressionList;
import io.ebean.Junction;
import io.ebean.PagedList;
import io.ebean.Query;
import io.ebean.Transaction;
import models.ebean.ExpressionEtags;
import models.internal.Expression;
import models.internal.ExpressionQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultExpression;
import models.internal.impl.DefaultExpressionQuery;
import models.internal.impl.DefaultQueryResult;
import play.Environment;
import play.db.ebean.EbeanDynamicEvolutions;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.persistence.PersistenceException;

/**
 * Implementation of <code>ExpressionRepository</code> using SQL database.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class DatabaseExpressionRepository implements ExpressionRepository {

    /**
     * Public constructor.
     *
     * @param environment Play's <code>Environment</code> instance.
     * @param config Play's <code>Configuration</code> instance.
     * @param ignored ignored, used as dependency injection ordering
     * @throws Exception If the configuration is invalid.
     */
    @Inject
    public DatabaseExpressionRepository(
            final Environment environment,
            final Config config,
            final EbeanDynamicEvolutions ignored) throws Exception {
        this(
                ConfigurationHelper.<ExpressionQueryGenerator>getType(
                        environment,
                        config,
                        "expressionRepository.expressionQueryGenerator.type")
                        .newInstance());
    }

    /**
     * Public constructor.
     *
     * @param expressionQueryGenerator Instance of <code>ExpressionQueryGenerator</code>.
     */
    public DatabaseExpressionRepository(final ExpressionQueryGenerator expressionQueryGenerator) {
        _expressionQueryGenerator = expressionQueryGenerator;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening expression repository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing expression repository").log();
        _isOpen.set(false);
    }

    @Override
    public Optional<Expression> get(final UUID identifier, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting expression")
                .addData("expressionId", identifier)
                .addData("organization", organization)
                .log();

        final models.ebean.Expression ebeanExpression = Ebean.find(models.ebean.Expression.class)
                .where()
                .eq("uuid", identifier)
                .eq("organization.uuid", organization.getId())
                .findOne();
        if (ebeanExpression == null) {
            return Optional.empty();
        }

        return Optional.of(convertFromEbeanExpression(ebeanExpression));
    }

    @Override
    public ExpressionQuery createQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultExpressionQuery(this, organization);
    }

    @Override
    public QueryResult<Expression> query(final ExpressionQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();

        // Create the base query
        final PagedList<models.ebean.Expression> pagedExpressions = _expressionQueryGenerator.createExpressionQuery(query);

        // Compute the etag
        // TODO(deepika): Obfuscate the etag [ISSUE-7]
        final Long etag = _expressionQueryGenerator.getEtag(query.getOrganization());

        // Transform the results
        return new DefaultQueryResult<>(
                pagedExpressions.getList()
                        .stream()
                        .map(expression -> convertFromEbeanExpression(expression))
                        .collect(Collectors.toList()),
                pagedExpressions.getTotalCount(),
                etag.toString());
    }

    @Override
    public long getExpressionCount(final Organization organization) {
        assertIsOpen();
        return Ebean.find(models.ebean.Expression.class)
                .where()
                .eq("organization.uuid", organization.getId())
                .findCount();
    }

    @Override
    public void addOrUpdateExpression(final Expression expression, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Upserting expression")
                .addData("expression", expression)
                .addData("organization", organization)
                .log();

        try (Transaction transaction = Ebean.beginTransaction()) {
            models.ebean.Expression ebeanExpression = Ebean.find(models.ebean.Expression.class)
                    .where()
                    .eq("uuid", expression.getId())
                    .eq("organization.uuid", organization.getId())
                    .findOne();
            boolean isNewExpression = false;
            if (ebeanExpression == null) {
                ebeanExpression = new models.ebean.Expression();
                isNewExpression = true;
            }

            ebeanExpression.setCluster(expression.getCluster());
            ebeanExpression.setUuid(expression.getId());
            ebeanExpression.setMetric(expression.getMetric());
            ebeanExpression.setScript(expression.getScript());
            ebeanExpression.setService(expression.getService());
            ebeanExpression.setOrganization(models.ebean.Organization.findByOrganization(organization));
            _expressionQueryGenerator.saveExpression(ebeanExpression);
            transaction.commit();

            LOGGER.info()
                    .setMessage("Upserted expression")
                    .addData("expression", expression)
                    .addData("isCreated", isNewExpression)
                    .addData("organization", organization)
                    .log();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            LOGGER.error()
                    .setMessage("Failed to upsert expression")
                    .addData("expression", expression)
                    .addData("organization", organization)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Expression repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private Expression convertFromEbeanExpression(final models.ebean.Expression ebeanExpression) {
        return new DefaultExpression.Builder()
                .setCluster(ebeanExpression.getCluster())
                .setId(ebeanExpression.getUuid())
                .setMetric(ebeanExpression.getMetric())
                .setService(ebeanExpression.getService())
                .setScript(ebeanExpression.getScript())
                .build();
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final ExpressionQueryGenerator _expressionQueryGenerator;

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseExpressionRepository.class);

    /**
     * Inteface for database query generation.
     */
    public interface ExpressionQueryGenerator {

        /**
         * Translate the <code>ExpressionQuery</code> to an Ebean <code>Query</code>.
         *
         * @param query The repository agnostic <code>ExpressionQuery</code>.
         * @return The database specific <code>PagedList</code> query result.
         */
        PagedList<models.ebean.Expression> createExpressionQuery(ExpressionQuery query);

        /**
         * Save the <code>Expression</code> to the database. This needs to be executed in a transaction.
         *
         * @param expression The <code>Expression</code> model instance to save.
         */
        void saveExpression(models.ebean.Expression expression);

        /**
         * Gets the etag for the alerts table.
         *
         * @param organization The organization owning the expression.
         * @return The etag for the table.
         */
        long getEtag(Organization organization);
    }

    /**
     * RDBMS agnostic query for expressions using 'like'.
     */
    public static final class GenericQueryGenerator implements ExpressionQueryGenerator {

        @Override
        public PagedList<models.ebean.Expression> createExpressionQuery(final ExpressionQuery query) {
            ExpressionList<models.ebean.Expression> ebeanExpressionList = Ebean.find(models.ebean.Expression.class).where();
            ebeanExpressionList = ebeanExpressionList.eq("organization.uuid", query.getOrganization().getId());
            if (query.getCluster().isPresent()) {
                ebeanExpressionList = ebeanExpressionList.eq("cluster", query.getCluster().get());
            }
            if (query.getService().isPresent()) {
                ebeanExpressionList = ebeanExpressionList.eq("service", query.getService().get());
            }
            //TODO(deepika): Add full text search [ISSUE-11]
            if (query.getContains().isPresent()) {
                final Junction<models.ebean.Expression> junction = ebeanExpressionList.disjunction();
                if (!query.getCluster().isPresent()) {
                    ebeanExpressionList = junction.contains("cluster", query.getContains().get());
                }
                if (!query.getService().isPresent()) {
                    ebeanExpressionList = junction.contains("service", query.getContains().get());
                }
                ebeanExpressionList = junction.contains("metric", query.getContains().get());
                ebeanExpressionList = junction.contains("script", query.getContains().get());
                ebeanExpressionList = ebeanExpressionList.endJunction();
            }
            final Query<models.ebean.Expression> ebeanQuery = ebeanExpressionList.query();

            int offset = 0;
            if (query.getOffset().isPresent()) {
                offset = query.getOffset().get();
            }
            return ebeanQuery.setFirstRow(offset).setMaxRows(query.getLimit()).findPagedList();
        }

        @Override
        public void saveExpression(final models.ebean.Expression expression) {
            Ebean.save(expression);
        }

        @Override
        public long getEtag(final Organization organization) {
            return ExpressionEtags.getEtagByOrganization(organization);
        }
    }
}
