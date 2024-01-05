/*
 * Copyright 2019 Inscope Metrics
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
package com.arpnetworking.metrics.portal.integration.test;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.internal.core.type.codec.registry.DefaultCodecRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import org.cognitor.cassandra.migration.Database;
import org.cognitor.cassandra.migration.MigrationConfiguration;
import org.cognitor.cassandra.migration.MigrationRepository;
import org.cognitor.cassandra.migration.MigrationTask;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Helper to manage shared Cassandra references for integration testing.
 *
 * @author Ville Koskela (ville at inscopemetrics dot io)
 */
public final class CassandraServerHelper {

    /**
     * Create a new session to the Metrics Cassandra cluster.
     *
     * @return reference to the Metrics Cassandra cluster {@code Session}
     */
    public static synchronized CqlSession createSession() {
        @Nullable CqlSession session = CASSANDRA_SERVER_MAP.get(METRICS_DATABASE);
        if (session == null || session.isClosed()) {
            session = initializeCassandraServer(
                    METRICS_DATABASE,
                    METRICS_KEYSPACE,
                    getEnvOrDefault("CASSANDRA_HOST", "localhost"),
                    getEnvOrDefault("CASSANDRA_PORT", DEFAULT_CASSANDRA_PORT, Integer::parseInt));
        }
        return session;
    }

    private static CqlSession initializeCassandraServer(
            final String name,
            final String keyspace,
            final String hostname,
            final int port) {

        final CqlSession session;
        final CqlSessionBuilder sessionBuilder = new CqlSessionBuilder()
                .addContactPoints(Collections.singleton(InetSocketAddress.createUnresolved(hostname, port)))
                .withLocalDatacenter("datacenter1")
                .withCodecRegistry(new DefaultCodecRegistry("codec"));
        session = sessionBuilder
                .build();

        final MigrationConfiguration migrationConfiguration = new MigrationConfiguration().withKeyspaceName(keyspace).withExecutionProfile("migration");
        final Database database = new Database(session, migrationConfiguration);
        final MigrationRepository migrationRepository = new MigrationRepository("cassandra/migration/" + name);
        final MigrationTask migration = new MigrationTask(
                database,
                migrationRepository,
                true);

        LOGGER.info()
                .setMessage("Running cassandra migrations for test setup")
                .addData("databaseVersion", database.getVersion())
                .addData("migrationVersion", migrationRepository.getLatestVersion())
                .log();
        migration.migrate();
        LOGGER.info()
                .setMessage("Migration complete")
                .log();


        CASSANDRA_SERVER_MAP.put(name, session);
        return sessionBuilder.build();
    }

    private static String getEnvOrDefault(final String name, final String defaultValue) {
        return getEnvOrDefault(name, defaultValue, Function.identity());
    }

    private static <T> T getEnvOrDefault(final String name, final T defaultValue, final Function<String, T> map) {
        @Nullable final String value = System.getenv(name);
        return value == null ? defaultValue : map.apply(value);
    }

    private CassandraServerHelper() { }

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<Map<String, Object>>() { };
    private static final Map<String, CqlSession> CASSANDRA_SERVER_MAP = Maps.newHashMap();
    private static final String METRICS_DATABASE = "default";
    private static final String METRICS_KEYSPACE = "portal";
    private static final int DEFAULT_CASSANDRA_PORT = 9042;
    private static final ObjectNode DEFAULT_REPLICATION;
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraServerHelper.class);

    static {
        DEFAULT_REPLICATION = OBJECT_MAPPER.createObjectNode();
        DEFAULT_REPLICATION.put("class", "SimpleStrategy");
        DEFAULT_REPLICATION.put("replication_factor", "1");
    }
}
