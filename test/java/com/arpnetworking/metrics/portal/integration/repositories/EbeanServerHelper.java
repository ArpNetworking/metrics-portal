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
package com.arpnetworking.metrics.portal.integration.repositories;

import com.google.common.collect.Maps;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ebean.EbeanServer;
import io.ebean.EbeanServerFactory;
import io.ebean.config.ServerConfig;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * Helper to manage shared {@code EbeanServer} references for integration testing.
 *
 * @author ville dot koskela at inscopemetrics dot io
 */
final class EbeanServerHelper {

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
                    DEFAULT_POSTGRES_PORT,
                    METRICS_DATABASE_NAME,
                    METRICS_DATABASE_USERNAME,
                    METRICS_DATABASE_PASSWORD,
                    true);
            EBEAN_SERVER_MAP.put(METRICS_DATABASE_NAME, ebeanServer);
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
        hikariConfig.setJdbcUrl(String.format(
                "jdbc:postgresql://%s:%d/%s?currentSchema=portal",
                hostname,
                port,
                database));
        hikariConfig.setMaximumPoolSize(DEFAULT_POOL_SIZE);
        hikariConfig.setPassword(password);
        hikariConfig.setPoolName(name);
        hikariConfig.setUsername(username);

        final ServerConfig serverConfig = new ServerConfig();
        serverConfig.setName(name);
        serverConfig.setDefaultServer(setAsDefault);
        serverConfig.setDataSource(new HikariDataSource(hikariConfig));
        return EbeanServerFactory.create(serverConfig);
    }

    private static String getEnvOrDefault(final String name, final String defaultValue) {
        @Nullable final String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private EbeanServerHelper() {}

    private static final Map<String, EbeanServer> EBEAN_SERVER_MAP = Maps.newHashMap();
    private static final String METRICS_DATABASE_NAME = "metrics";
    private static final String METRICS_DATABASE_USERNAME = "metrics_app";
    private static final String METRICS_DATABASE_PASSWORD = "metrics_app_password";
    private static final int DEFAULT_POSTGRES_PORT = 5432;
    private static final int DEFAULT_POOL_SIZE = 200;
}
