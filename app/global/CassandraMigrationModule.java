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
package global;

import com.arpnetworking.pillar.CassandraMigrationInitializer;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import play.Environment;
import play.inject.ApplicationLifecycle;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Module for Pillar evolutions plugin.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class CassandraMigrationModule extends AbstractModule {
    /**
     * Public constructor.
     *
     * @param environment Play environment
     * @param configuration Play configuration
     */
    @Inject
    public CassandraMigrationModule(final Environment environment, final Config configuration) {
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

        bind(CassandraMigrationInitializer.class).asEagerSingleton();

        final ConfigObject cassConfig = dbConfig.root();
        final Set<String> dbNames = cassConfig.keySet();
        final Provider<MutableCodecRegistry> registryProvider = binder().getProvider(MutableCodecRegistry.class);
        final Provider<ApplicationLifecycle> lifecycle = binder().getProvider(ApplicationLifecycle.class);
        for (final String name : dbNames) {
            bind(CqlSession.class).annotatedWith(Names.named(name))
                    .toProvider(new CassandraClusterProvider(cassConfig.toConfig().getConfig(name), registryProvider, lifecycle))
                    .in(Scopes.SINGLETON);
        }

        if (dbNames.contains("default")) {
            bind(CqlSession.class).toProvider(binder().getProvider(Key.get(CqlSession.class, Names.named("default"))));
        }

        bind(CassandraMigrationInitializer.class).asEagerSingleton();
    }

    private final Config _configuration;

    private static final class CassandraClusterProvider implements Provider<CqlSession> {
        CassandraClusterProvider(
                final Config config,
                final Provider<MutableCodecRegistry> registryProvider,
                final Provider<ApplicationLifecycle> lifecycleProvider) {
            _config = config;
            _registryProvider = registryProvider;
            _lifecycleProvider = lifecycleProvider;
        }

        @Override
        public CqlSession get() {
            final String clusterName = _config.getString("clusterName");
            final int port;
            if (_config.hasPath("port")) {
                port = _config.getInt("port");
            } else {
                port = DEFAULT_CASSANDRA_PORT;
            }
            final List<InetSocketAddress> hosts = _config.getStringList("hosts")
                    .stream()
                    .map(host -> {
                        try {
                            return InetSocketAddress.createUnresolved(host, port);
                            // CHECKSTYLE.OFF: IllegalCatch - Cannot throw checked exceptions inside .map
                        } catch (final Exception e) {
                            // CHECKSTYLE.ON: IllegalCatch
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList());
            final MutableCodecRegistry registry = _registryProvider.get();
            final CqlSession session = new CqlSessionBuilder()
                    .addContactPoints(hosts)
                    .withCodecRegistry(registry)
                    .build();
            _lifecycleProvider.get().addStopHook(() -> {
                final CompletableFuture<Void> done = new CompletableFuture<>();
                session.closeAsync().whenCompleteAsync((v, t) -> { done.complete(null); }, MoreExecutors.directExecutor());
                return done;
            });
            return session;
        }

        private final Config _config;
        private final Provider<MutableCodecRegistry> _registryProvider;
        private final Provider<ApplicationLifecycle> _lifecycleProvider;

        private static final int DEFAULT_CASSANDRA_PORT = 9042;
    }

}
