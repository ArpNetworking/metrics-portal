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
package global;

import com.arpnetworking.pillar.PillarInitializer;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.MappingManager;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import play.Environment;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Module for Pillar evolutions plugin.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class PillarModule extends AbstractModule {
    /**
     * Public constructor.
     *
     * @param environment Play environment
     * @param configuration Play configuration
     */
    @Inject
    public PillarModule(final Environment environment, final Config configuration) {
        _configuration = configuration;
    }

    @Override
    protected void configure() {
        final Config dbConfig;
        try {
            dbConfig = _configuration.getConfig("cassandra.db");
        } catch (final ConfigException.Missing ignored) {
            return;
        }

        bind(PillarInitializer.class).asEagerSingleton();

        final ConfigObject cassConfig = dbConfig.root();
        final Set<String> dbNames = cassConfig.keySet();
        final Provider<CodecRegistry> registryProvider = binder().getProvider(CodecRegistry.class);
        for (final String name : dbNames) {
            bind(Session.class).annotatedWith(Names.named(name))
                    .toProvider(new CassandraSessionProvider(cassConfig.toConfig().getConfig(name), registryProvider));
            bind(MappingManager.class).annotatedWith(Names.named(name))
                    .toProvider(new CassandraMappingProvider(binder().getProvider(Key.get(Session.class, Names.named(name)))));
        }

        if (dbNames.contains("default")) {
            bind(Session.class).toProvider(binder().getProvider(Key.get(Session.class, Names.named("default"))));
            bind(MappingManager.class).toProvider(binder().getProvider(Key.get(MappingManager.class, Names.named("default"))));
        }

        bind(PillarInitializer.class).asEagerSingleton();
    }
    private final Config _configuration;

    private static final class CassandraSessionProvider implements Provider<Session> {
        CassandraSessionProvider(final Config config, final Provider<CodecRegistry> registryProvider) {
            _config = config;
            _registryProvider = registryProvider;
        }

        @Override
        public Session get() {
            final String clusterName = _config.getString("clusterName");
            final int port;
            if (_config.hasPath("port")) {
                port = _config.getInt("port");
            } else {
                port = DEFAULT_CASSANDRA_PORT;
            }
            final List<InetAddress> hosts = _config.getStringList("hosts")
                    .stream()
                    .map(host -> {
                        try {
                            return InetAddress.getByName(host);
                        // CHECKSTYLE.OFF: IllegalCatch - Cannot throw checked exceptions inside .map
                        } catch (final Exception e) {
                        // CHECKSTYLE.ON: IllegalCatch
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList());
            return Cluster.builder()
                    .addContactPoints(hosts)
                    .withClusterName(clusterName)
                    .withPort(port)
                    .withCodecRegistry(_registryProvider.get())
                    .build()
                    .newSession();
        }

        private final Config _config;
        private final Provider<CodecRegistry> _registryProvider;

        private static final int DEFAULT_CASSANDRA_PORT = 9042;
    }

    private static final class CassandraMappingProvider implements Provider<MappingManager> {
        CassandraMappingProvider(final com.google.inject.Provider<Session> sessionProvider) {
            _sessionProvider = sessionProvider;
        }

        @Override
        public MappingManager get() {
            return new MappingManager(_sessionProvider.get());
        }

        private final com.google.inject.Provider<Session> _sessionProvider;
    }
}
