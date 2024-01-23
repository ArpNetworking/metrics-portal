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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.inject.Inject;

/**
 * Configuration for the Pillar evolutions.
 *
 * @author Brandon Arp (brandon dot arp at smartseet dot com)
 */
public class Configuration {
    /**
     * Public constructor.
     *
     * @param configuration Play configuration
     */
    @Inject
    public Configuration(final Config configuration) {
        _datastores = Maps.newHashMap();

        final Config dbConfig = configuration.getConfig("cassandra.db");
        final Set<String> dbNames = dbConfig.root().keySet();
        for (final String dbName : dbNames) {
            _datastores.put(dbName, new DatastoreConfig(dbName, dbConfig.getConfig(dbName)));
        }
    }

    /**
     * Public constructor.
     *
     * @param datastores Mapping of datastore name to {@link DatastoreConfig}
     */
    public Configuration(final Map<String, DatastoreConfig> datastores) {
        _datastores = Maps.newHashMap(datastores);
    }

    public Map<String, DatastoreConfig> getDatastores() {
        // TODO(ville): This should be an immutable map.
        return _datastores;
    }

    private final Map<String, DatastoreConfig> _datastores;

    /**
     * Represents configuration for a single Cassandra datastore.
     *
     * @author Brandon Arp (brandon dot arp at smartseet dot com)
     */
    public static final class DatastoreConfig {
        public Path getDirectory() {
            return _directory;
        }

        public List<String> getHosts() {
            return _hosts;
        }

        public String getKeyspace() {
            return _keyspace;
        }

        public ObjectNode getReplication() {
            return _replication;
        }

        /**
         * Public constructor.
         *
         * @param directory migration directory
         * @param hosts list of hosts
         * @param keyspace keyspace name
         * @param replication replication configuration
         */
        public DatastoreConfig(
                final Path directory,
                final List<String> hosts,
                final String keyspace,
                final ObjectNode replication) {
            _directory = directory;
            _hosts = Lists.newArrayList(hosts);
            _keyspace = keyspace;
            _replication = replication;
        }

        private DatastoreConfig(final String dbName, final Config config) {
            final ConfigValue directory = config.root().get("migration.directory");
            if (directory != null && ConfigValueType.STRING.equals(directory.valueType())) {
                _directory = Paths.get((String) directory.unwrapped());
            } else {
                _directory = Paths.get("cassandra/migration/" + dbName);
            }
            _hosts = config.getStringList("hosts");
            _keyspace = config.getString("keyspace");
            _replication = MAPPER.valueToTree(config.getObject("replication").unwrapped());
        }

        private final Path _directory;
        private final List<String> _hosts;
        private final String _keyspace;
        private final ObjectNode _replication;

        private static final ObjectMapper MAPPER = ObjectMapperFactory.getInstance();
    }
}
