/**
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

import akka.japi.Option;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.chrisomeara.pillar.CassandraMigrator;
import com.chrisomeara.pillar.Migrator;
import com.chrisomeara.pillar.Registry;
import com.chrisomeara.pillar.ReplicationOptions;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import play.Environment;
import play.core.WebCommands;
import scala.Predef;
import scala.collection.JavaConverters;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Initializer for the Pillar play module.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class PillarInitializer {
    /**
     * Public constructor.
     *
     * @param configuration Configuration for Cassandra datastores.
     * @param environment Play environment
     * @param webCommands Play web commands
     * @param injector Injector
     */
    @Inject
    public PillarInitializer(
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
            @Nullable final URL resource = _environment.resource(directory.toString());
            if (resource != null) {
                LOGGER.info()
                        .setMessage("Running migrations for Cassandra database")
                        .addData("database", dbName)
                        .log();
                try {
                    final scala.collection.immutable.Map<String, Object> replication =
                            JavaConverters.<String, Object>mapAsScalaMapConverter(
                                    MAPPER.convertValue(config.getReplication(), MAP_TYPE_REFERENCE))
                                    .asScala()
                                    .toMap(Predef.conforms());
                    final File file = new File(resource.toURI());
                    final Registry registry = Registry.fromDirectory(file);
                    final Migrator migrator = new CassandraMigrator(registry);
                    final Session session = _injector.getInstance(Key.get(Session.class, Names.named(dbName)));
                    session.init();
                    migrator.initialize(session, config.getKeyspace(), new ReplicationOptions(replication));
                    session.execute("USE " + config.getKeyspace());
                    migrator.migrate(session, Option.<Date>none().asScala());
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
                        .addData("database" , dbName)
                        .log();
            }
        }
    }

    private final com.arpnetworking.pillar.Configuration _configuration;
    private final Environment _environment;
    private final Injector _injector;

    private static final Logger LOGGER = LoggerFactory.getLogger(PillarInitializer.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<Map<String, Object>>() { };
    private static final ObjectMapper MAPPER = ObjectMapperFactory.getInstance();
}
