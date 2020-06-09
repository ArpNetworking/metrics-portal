/*
 * Copyright 2019 Dropbox
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

import com.arpnetworking.testing.SerializationTestUtils;
import com.google.common.collect.Maps;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ebean.EbeanServer;
import io.ebean.EbeanServerFactory;
import io.ebean.config.ServerConfig;
import org.flywaydb.core.Flyway;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Helper to manage shared {@code EbeanServer} references for integration testing.
 *
 * @author ville dot koskela at inscopemetrics dot io
 */
public final class EbeanServerHelper {

    /**
     * Obtain a reference to the shared Metrics database {@code EbeanServer}.
     *
     * @return reference to the shared Metrics database {@code EbeanServer}
     */
    public static synchronized EbeanServer getMetricsDatabase() {
        @Nullable EbeanServer ebeanServer = EBEAN_SERVER_MAP.get(METRICS_DATABASE_NAME);
        if (ebeanServer == null) {
            // TODO(ville): It should not be necessary to register this as the default database.
            // e.g.
            // Report.java:188 - The association is bound to the default server.
            ebeanServer = createEbeanServer(
                    getEnvOrDefault("PG_HOST", "localhost"),
                    getEnvOrDefault("PG_PORT", DEFAULT_POSTGRES_PORT, Integer::parseInt),
                    METRICS_DATABASE_NAME,
                    METRICS_DATABASE_USERNAME,
                    METRICS_DATABASE_PASSWORD,
                    true);
            migrateServer(
                    getEnvOrDefault("PG_HOST", "localhost"),
                    getEnvOrDefault("PG_PORT", DEFAULT_POSTGRES_PORT, Integer::parseInt),
                    METRICS_DATABASE_NAME,
                    METRICS_DATABASE_ADMIN_USERNAME,
                    METRICS_DATABASE_ADMIN_PASSWORD);

            EBEAN_SERVER_MAP.put(METRICS_DATABASE_NAME, ebeanServer);
        }
        return ebeanServer;
    }

    public static synchronized EbeanServer getAdminMetricsDatabase() {
        @Nullable EbeanServer ebeanServer = EBEAN_SERVER_MAP.get(METRICS_ADMIN_NAME);
        if (ebeanServer == null) {
            ebeanServer = createEbeanServer(
                    getEnvOrDefault("PG_HOST", "localhost"),
                    getEnvOrDefault("PG_PORT", DEFAULT_POSTGRES_PORT, Integer::parseInt),
                    METRICS_DATABASE_NAME,
                    METRICS_DATABASE_ADMIN_USERNAME,
                    METRICS_DATABASE_ADMIN_PASSWORD,
                    false);
            EBEAN_SERVER_MAP.put(METRICS_ADMIN_NAME, ebeanServer);
        }
        return ebeanServer;
    }

    private static EbeanServer createEbeanServer(
            final String hostname,
            final int port,
            final String database,
            final String username,
            final String password,
            final boolean setAsDefault) {
        final String name = database + "-" + UUID.randomUUID().toString();

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setJdbcUrl(createJdbcUrl(hostname, port, database));
        hikariConfig.setMaximumPoolSize(DEFAULT_POOL_SIZE);
        hikariConfig.setPassword(password);
        hikariConfig.setPoolName(name);
        hikariConfig.setUsername(username);

        final ServerConfig serverConfig = new ServerConfig();
        serverConfig.setName(name);
        serverConfig.setDefaultServer(setAsDefault);
        serverConfig.setDataSource(new HikariDataSource(hikariConfig));
        serverConfig.addPackage("models.ebean");
        serverConfig.setObjectMapper(SerializationTestUtils.getApiObjectMapper());
        return EbeanServerFactory.create(serverConfig);
    }

    private static void migrateServer(
            final String hostname,
            final int port,
            final String database,
            final String username,
            final String password) {
        final Flyway flyway = new Flyway();
        flyway.setDataSource(createJdbcUrl(hostname, port, database), username, password);
        flyway.setSchemas("portal");
        flyway.setValidateOnMigrate(true);
        flyway.setEncoding("UTF-8");
        flyway.setLocations("db/migration/metrics_portal_ddl");
        flyway.migrate();
    }

    private static String createJdbcUrl(final String hostname, final int port, final String database) {
        return String.format(
                "jdbc:postgresql://%s:%d/%s?currentSchema=portal",
                hostname,
                port,
                database);
    }

    private static String getEnvOrDefault(final String name, final String defaultValue) {
        return getEnvOrDefault(name, defaultValue, Function.identity());
    }

    private static <T> T getEnvOrDefault(final String name, final T defaultValue, final Function<String, T> map) {
        @Nullable final String value = System.getenv(name);
        return value == null ? defaultValue : map.apply(value);
    }

    private EbeanServerHelper() {}

    private static final Map<String, EbeanServer> EBEAN_SERVER_MAP = Maps.newHashMap();
    private static final String METRICS_DATABASE_NAME = "metrics";
    private static final String METRICS_ADMIN_NAME = "metrics_ddl";
    private static final String METRICS_DATABASE_USERNAME = "metrics_app";
    private static final String METRICS_DATABASE_PASSWORD = "metrics_app_password";
    private static final String METRICS_DATABASE_ADMIN_USERNAME = "metrics_dba";
    private static final String METRICS_DATABASE_ADMIN_PASSWORD = "metrics_dba_password";
    private static final int DEFAULT_POSTGRES_PORT = 6432;
    private static final int DEFAULT_POOL_SIZE = 50;
}
