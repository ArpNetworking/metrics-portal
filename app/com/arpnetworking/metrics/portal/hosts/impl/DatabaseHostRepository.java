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
package com.arpnetworking.metrics.portal.hosts.impl;

import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import io.ebean.Ebean;
import io.ebean.ExpressionList;
import io.ebean.PagedList;
import io.ebean.Query;
import io.ebean.RawSql;
import io.ebean.RawSqlBuilder;
import io.ebean.Transaction;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.MetricsSoftwareState;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultHost;
import models.internal.impl.DefaultHostQuery;
import models.internal.impl.DefaultQueryResult;
import play.Environment;
import play.db.ebean.EbeanDynamicEvolutions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Implementation of <code>HostRepository</code> using SQL database.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class DatabaseHostRepository implements HostRepository {

    /**
     * Public constructor.
     *
     * @param environment Play's <code>Environment</code> instance.
     * @param config Play's <code>Configuration</code> instance.
     * @param ignored ignored, used as dependency injection ordering
     * @throws Exception If the configuration is invalid.
     */
    @Inject
    public DatabaseHostRepository(
            final Environment environment,
            final Config config,
            final EbeanDynamicEvolutions ignored) throws Exception {
        this(
                ConfigurationHelper.<HostQueryGenerator>getType(
                        environment,
                        config,
                        "hostRepository.hostQueryGenerator.type")
                .newInstance());
    }

    /**
     * Public constructor.
     *
     * @param hostQueryGenerator Instance of <code>HostQueryGenerator</code>.
     */
    public DatabaseHostRepository(final HostQueryGenerator hostQueryGenerator) {
        _hostQueryGenerator = hostQueryGenerator;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening host repository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing host repository").log();
        _isOpen.set(false);
    }

    @Override
    public void addOrUpdateHost(final Host host, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Adding or updating host")
                .addData("host", host)
                .addData("organization", organization)
                .log();

        final Transaction transaction = Ebean.beginTransaction();
        try {
            models.ebean.Host ebeanHost = Ebean.find(models.ebean.Host.class)
                    .where()
                    .eq("organization.uuid", organization.getId())
                    .eq("name", host.getHostname())
                    .findOne();
            boolean isNewHost = false;
            if (ebeanHost == null) {
                ebeanHost = new models.ebean.Host();
                isNewHost = true;
            }
            ebeanHost.setCluster(host.getCluster().orElse(null));
            ebeanHost.setMetricsSoftwareState(host.getMetricsSoftwareState().toString());
            ebeanHost.setName(host.getHostname());
            ebeanHost.setOrganization(models.ebean.Organization.findByOrganization(organization));
            _hostQueryGenerator.saveHost(ebeanHost);
            transaction.commit();

            LOGGER.info()
                    .setMessage("Upserted host")
                    .addData("host", host)
                    .addData("organization", organization)
                    .addData("isCreated", isNewHost)
                    .log();
        } finally {
            transaction.end();
        }
    }

    @Override
    public void deleteHost(final String hostname, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Deleting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();
        final models.ebean.Host ebeanHost = Ebean.find(models.ebean.Host.class)
                .where()
                .eq("name", hostname)
                .eq("organization.uuid", organization.getId())
                .findOne();
        if (ebeanHost != null) {
            Ebean.delete(ebeanHost);
            LOGGER.info()
                    .setMessage("Deleted host")
                    .addData("hostname", hostname)
                    .addData("organization", organization)
                    .log();
        } else {
            LOGGER.info()
                    .setMessage("Host not found")
                    .addData("hostname", hostname)
                    .addData("organization", organization)
                    .log();
        }
    }

    @Override
    public HostQuery createQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultHostQuery(this, organization);
    }

    @Override
    public QueryResult<Host> query(final HostQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();
        final Organization organization = query.getOrganization();

        // Create the base query
        final PagedList<models.ebean.Host> pagedHosts = _hostQueryGenerator.createHostQuery(query, organization);

        // Compute the etag
        // NOTE: Another way to do this would be to use the version field and hash those together.
        final String etag = Long.toHexString(pagedHosts.getList().stream()
                .map(host -> host.getUpdatedAt().after(host.getCreatedAt()) ? host.getUpdatedAt() : host.getCreatedAt())
                .max(Timestamp::compareTo)
                .orElse(new Timestamp(0))
                .getTime());

        // Transform the results
        return new DefaultQueryResult<>(
                pagedHosts.getList()
                        .stream()
                        .map(host -> new DefaultHost.Builder()
                                .setCluster(host.getCluster())
                                .setHostname(host.getName())
                                .setMetricsSoftwareState(MetricsSoftwareState.valueOf(host.getMetricsSoftwareState()))
                                .build())
                .collect(Collectors.toList()),
                pagedHosts.getTotalCount(),
                etag);
    }

    @Override
    public long getHostCount(final Organization organization) {
        assertIsOpen();
        return Ebean.find(models.ebean.Host.class)
                .where()
                .eq("organization.uuid", organization.getId())
                .findCount();
    }

    @Override
    public long getHostCount(final MetricsSoftwareState metricsSoftwareState, final Organization organization) {
        assertIsOpen();
        return Ebean.find(models.ebean.Host.class)
                .where()
                .eq("organization.uuid", organization.getId())
                .eq("metrics_software_state", metricsSoftwareState.toString())
                .findCount();
    }

    private static String mapField(final HostQuery.Field field) {
        switch (field) {
            case HOSTNAME:
                return "name";
            case METRICS_SOFTWARE_STATE:
                return "metrics_software_state";
            default:
                throw new UnsupportedOperationException(String.format("Unrecognized field; field=%s", field));
        }
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Host repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final HostQueryGenerator _hostQueryGenerator;

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseHostRepository.class);

    /**
     * Inteface for database query generation.
     */
    public interface HostQueryGenerator {

        /**
         * Translate the <code>HostQuery</code> to an Ebean <code>Query</code>.
         *
         * @param query The repository agnostic <code>HostQuery</code>.
         * @param organization The organization to query in.
         * @return The database specific <code>PagedList</code> query result.
         */
        PagedList<models.ebean.Host> createHostQuery(HostQuery query, Organization organization);

        /**
         * Save the <code>Host</code> to the database. This needs to be executed in a transaction.
         *
         * @param host The <code>Host</code> model instance to save.
         */
        void saveHost(models.ebean.Host host);
    }

    /**
     * RDBMS agnostic query for hosts using 'like'.
     */
    public static final class GenericQueryGenerator implements HostQueryGenerator {

        @Override
        public PagedList<models.ebean.Host> createHostQuery(final HostQuery query, final Organization organization) {
            ExpressionList<models.ebean.Host> ebeanExpressionList = Ebean.find(models.ebean.Host.class).where();
            ebeanExpressionList = ebeanExpressionList.eq("organization.uuid", organization.getId());
            if (query.getCluster().isPresent()) {
                ebeanExpressionList = ebeanExpressionList.eq("cluster", query.getCluster().get());
            }
            if (query.getMetricsSoftwareState().isPresent()) {
                ebeanExpressionList = ebeanExpressionList.eq("metrics_software_state", query.getMetricsSoftwareState().get().toString());
            }
            if (query.getPartialHostname().isPresent()) {
                ebeanExpressionList = ebeanExpressionList.like("name", query.getPartialHostname().get() + "%");
            }

            Query<models.ebean.Host> ebeanQuery = ebeanExpressionList.query();
            if (query.getSortBy().isPresent()) {
                ebeanQuery = ebeanQuery.orderBy().asc(mapField(query.getSortBy().get()));
            }

            int offset = 0;
            if (query.getOffset().isPresent()) {
                offset = query.getOffset().get();
            }
            return ebeanQuery.setFirstRow(offset).setMaxRows(query.getLimit()).findPagedList();
        }

        @Override
        public void saveHost(final models.ebean.Host host) {
            Ebean.save(host);
        }
    }

    /**
     * Postgresql specific full text index to query for hosts.
     */
    public static final class PostgresqlHostQueryGenerator implements HostQueryGenerator {

        @Override
        public PagedList<models.ebean.Host> createHostQuery(final HostQuery query, final Organization organization) {
            final StringBuilder selectBuilder = new StringBuilder(
                    "select t0.id, t0.version, t0.created_at, t0.updated_at, "
                            + "t0.name, t0.cluster, t0.metrics_software_state "
                            + "from portal.hosts t0");
            final StringBuilder whereBuilder = new StringBuilder();
            final StringBuilder orderBuilder = new StringBuilder();
            final Map<String, Object> parameters = Maps.newHashMap();

            // Add the partial host name clause using the postgresql full text index
            if (query.getPartialHostname().isPresent() && !query.getPartialHostname().get().isEmpty()) {
                final List<String> queryTokens = Arrays.asList(query.getPartialHostname().get().split(" "));
                final String prefixExpression = queryTokens
                        .stream()
                        .map(s -> s + ":*")
                        .reduce((s1, s2) -> s1 + " & " + s2)
                        .orElse(null);
                final String termExpression = queryTokens
                        .stream()
                        .reduce((s1, s2) -> s1 + " & " + s2)
                        .orElse(null);
                if (prefixExpression != null && termExpression != null) {
                    parameters.put("prefixQuery", prefixExpression);
                    parameters.put("termQuery", termExpression);
                    selectBuilder.append(", to_tsquery('simple',:prefixQuery) prefixQuery, to_tsquery('simple',:termQuery) termQuery");
                    whereBuilder.append("where (t0.name_idx_col @@ prefixQuery or t0.name_idx_col @@ termQuery)");
                    orderBuilder.append("order by ts_rank(t0.name_idx_col, prefixQuery) * ts_rank(t0.name_idx_col, termQuery) "
                            + "/ char_length(t0.name) DESC, name ASC");
                } else {
                    // The user enters only removable tokens (e.g. space, period, etc.)
                    LOGGER.debug()
                            .setMessage("Skipping partial host name query clause")
                            .addData("organization", organization)
                            .addData("partialHostName", query.getPartialHostname().get())
                            .addData("prefixExpression", prefixExpression)
                            .addData("termExpression", termExpression)
                            .log();
                }
            }

            // Add the cluster name clause
            if (query.getCluster().isPresent()) {
                beginOrExtend(whereBuilder, "where ", " and ");
                whereBuilder.append("t0.cluster = :cluster");
                parameters.put("cluster", query.getCluster().get());
            }

            // Add the metrics software state clause
            if (query.getMetricsSoftwareState().isPresent()) {
                beginOrExtend(whereBuilder, "where ", " and ");
                whereBuilder.append("t0.metrics_software_state = :metrics_software_state");
                parameters.put("metrics_software_state", query.getMetricsSoftwareState().get().toString());
            }

            // Add the sort order
            if (query.getSortBy().isPresent()) {
                // NOTE: Replace the ordering (if any) with the user specified one
                orderBuilder.setLength(0);
                orderBuilder.append("order by ")
                        .append(mapField(query.getSortBy().get()))
                        .append(" ASC");
            }

            // Compute the page offset
            int offset = 0;
            if (query.getOffset().isPresent()) {
                offset = query.getOffset().get();
            }

            // Create and execute the raw parameterized query
            return createParameterizedHostQueryFromRawSql(
                    selectBuilder.toString() + " " + whereBuilder.toString() + " " + orderBuilder.toString(),
                    parameters)
                    .setFirstRow(offset)
                    .setMaxRows(query.getLimit())
                    .findPagedList();
        }

        @Override
        public void saveHost(final models.ebean.Host host) {
            final String hostname = host.getName();
            final String labels = hostname.replace('.', ' ');
            final String words = labels.replace('-', ' ');
            final String alnum = tokenize(labels)
                    .stream()
                    .reduce((s1, s2) -> s1 + " " + s2)
                    .orElse("");

            Ebean.save(host);
            Ebean.createSqlUpdate(
                    "UPDATE portal.hosts SET name_idx_col = "
                            + "setweight(to_tsvector('simple', coalesce(:hostname,'')), 'A')"
                            + "|| setweight(to_tsvector('simple', coalesce(:labels,'')), 'B')"
                            + "|| setweight(to_tsvector('simple', coalesce(:words,'')), 'C')"
                            + "|| setweight(to_tsvector('simple', coalesce(:alnum,'')), 'D')"
                            + "WHERE id = :id")
                    .setParameter("id", host.getId())
                    .setParameter("hostname", hostname)
                    .setParameter("labels", labels)
                    .setParameter("words", words)
                    .setParameter("alnum", alnum)
                    .execute();
        }

        // NOTE: Package private for testing
        /* package private */ static List<String> tokenize(final String word) {
            final List<String> tokens = new ArrayList<>();
            for (final String token : word.split("([^\\p{Alnum}])|((?<=\\p{Alpha})(?=\\p{Digit})|(?<=\\p{Digit})(?=\\p{Alpha}))")) {
                if (!token.isEmpty()) {
                    tokens.add(token.toLowerCase(Locale.getDefault()));
                }
            }
            return tokens;
        }

        private static Query<models.ebean.Host> createParameterizedHostQueryFromRawSql(
                final String sql,
                final Map<String, Object> parameters) {
            final RawSql rawSql = RawSqlBuilder.parse(sql)
                    .columnMapping("t0.id", "id")
                    .columnMapping("t0.version", "version")
                    .columnMapping("t0.created_at", "createdAt")
                    .columnMapping("t0.updated_at", "updatedAt")
                    .columnMapping("t0.name", "name")
                    .columnMapping("t0.cluster", "cluster")
                    .columnMapping("t0.metrics_software_state", "metricsSoftwareState")
                    .create();
            final Query<models.ebean.Host> ebeanQuery = Ebean.find(models.ebean.Host.class).setRawSql(rawSql);
            for (final Map.Entry<String, Object> parameter : parameters.entrySet()) {
                ebeanQuery.setParameter(parameter.getKey(), parameter.getValue());
            }
            return ebeanQuery;
        }

        private static void beginOrExtend(final StringBuilder stringBuilder, final String beginning, final String continuation) {
            if (stringBuilder.length() == 0) {
                stringBuilder.append(beginning);
            } else {
                stringBuilder.append(continuation);
            }
        }
    }
}
