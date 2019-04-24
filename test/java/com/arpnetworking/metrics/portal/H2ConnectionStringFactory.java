/*
 * Copyright 2016 Groupon.com
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
package com.arpnetworking.metrics.portal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for getting unique H2 jdbc urls.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public final class H2ConnectionStringFactory {

    /**
     * Generates the JDBC url with a unique port number and database name.  Deletes the file if it already exists.
     *
     * @return The JDBC url.
     */
    public static String generateJdbcUrl() {
        final int nextCounter = UNIQUE_COUNTER.getAndIncrement();
        final int port = BASE_PORT + nextCounter;
        final String path = "./target/h2/metrics_portal" + nextCounter;
        try {
            if (Files.exists(Paths.get(path + ".mv.db"))) {
                Files.delete(Paths.get(path + ".mv.db"));
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return "jdbc:h2:"
                + path
                + ";AUTO_SERVER=TRUE;AUTO_SERVER_PORT="
                + port
                + ";MODE=PostgreSQL;INIT=create schema if not exists portal\\;create schema if not exists akka;DB_CLOSE_DELAY=-1";
    }

    /**
     * Generates a map containing h2 config parameters.
     *
     * @return h2 config map
     */
    public static Map<String, Object> generateConfiguration() {
        final String url = generateJdbcUrl();
        return ImmutableMap.<String, Object>builder()
                .put("db.default.driver", "org.h2.Driver")
                .put("db.default.url", url)
                .put("db.metrics_portal_ddl.driver", "org.h2.Driver")
                .put("db.metrics_portal_ddl.url", url)
                .put("db.metrics_portal_ddl.migration.locations", Lists.newArrayList("common", "h2"))
                .put("db.akka.driver", "org.h2.Driver")
                .put("db.akka.migration.locations", Lists.newArrayList("common", "h2"))
                .put("db.akka.url", url)
                .put("db.akka.username", "sa")
                .put("db.akka.password", "secret")
                .build();
    }

    private H2ConnectionStringFactory() {}

    private static final AtomicInteger UNIQUE_COUNTER = new AtomicInteger(1);
    private static final int BASE_PORT = 50000;
}
