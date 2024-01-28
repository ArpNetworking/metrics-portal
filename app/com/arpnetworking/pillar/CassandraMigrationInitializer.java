/*
 * Copyright 2017 Smartsheet.com
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
package com.arpnetworking.pillar;

import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import jakarta.inject.Inject;
import org.cognitor.cassandra.migration.Database;
import org.cognitor.cassandra.migration.MigrationConfiguration;
import org.cognitor.cassandra.migration.MigrationRepository;
import org.cognitor.cassandra.migration.MigrationTask;
import play.Environment;
import play.core.WebCommands;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Initializer for the Pillar play module.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class CassandraMigrationInitializer {
    /**
     * Public constructor.
     *
     * @param configuration Configuration for Cassandra datastores.
     * @param environment Play environment
     * @param webCommands Play web commands
     * @param injector Injector
     */
    @Inject
    public CassandraMigrationInitializer(
            final com.arpnetworking.pillar.Configuration configuration,
            final Environment environment,
            final WebCommands webCommands,
            final Injector injector) {
        _configuration = configuration;
        _environment = environment;
        _injector = injector;

        runMigrations();
    }

    private void runMigrations() {
        for (final Map.Entry<String, Configuration.DatastoreConfig> entry : _configuration.getDatastores().entrySet()) {
            final String dbName = entry.getKey();
            final Configuration.DatastoreConfig config = entry.getValue();

            final Path directory = config.getDirectory();
            LOGGER.debug().setMessage("Looking for Cassandra migrations").addData("path", directory.toString()).log();
            @Nullable final URL resource = _environment.resource(directory.toString());
            if (resource != null) {
                LOGGER.info()
                        .setMessage("Running migrations for Cassandra database")
                        .addData("database", dbName)
                        .log();
                try {
                    final File file = new File(resource.toURI());

                    final CqlSession session = _injector.getInstance(Key.get(CqlSession.class, Names.named(dbName)));

                    final Database database = new Database(session, new MigrationConfiguration().withKeyspaceName(config.getKeyspace()));
                    final MigrationTask migration = new MigrationTask(database, new MigrationRepository(file.getAbsolutePath()), true);
                    migration.migrate();

                } catch (final URISyntaxException e) {
                    LOGGER.error()
                            .setMessage("Unable to run migrations")
                            .addData("resource", resource)
                            .setThrowable(e)
                            .log();
                }
            } else {
                LOGGER.info()
                        .setMessage("Could not find migrations directory")
                        .addData("directory", directory)
                        .addData("database", dbName)
                        .log();
            }
        }
    }

    private final com.arpnetworking.pillar.Configuration _configuration;
    private final Environment _environment;
    private final Injector _injector;

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMigrationInitializer.class);
}
