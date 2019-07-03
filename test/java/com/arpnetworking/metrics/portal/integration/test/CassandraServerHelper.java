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

import akka.japi.Option;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.chrisomeara.pillar.CassandraMigrator;
import com.chrisomeara.pillar.Migrator;
import com.chrisomeara.pillar.Registry;
import com.chrisomeara.pillar.ReplicationOptions;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.Session;
import com.datastax.driver.extras.codecs.enums.EnumNameCodec;
import com.datastax.driver.extras.codecs.jdk8.InstantCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import models.internal.Context;
import models.internal.Operator;
import scala.Predef;
import scala.collection.JavaConverters;

import java.io.File;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
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
    public static synchronized Session createSession() {
        @Nullable Cluster cluster = CASSANDRA_SERVER_MAP.get(METRICS_DATABASE);
        if (cluster == null) {
            cluster = initializeCassandraServer(
                    METRICS_DATABASE,
                    METRICS_KEYSPACE,
                    getEnvOrDefault("CASSANDRA_HOST", "localhost"),
                    getEnvOrDefault("CASSANDRA_PORT", DEFAULT_CASSANDRA_PORT, Integer::parseInt));
        }
        return cluster.newSession();
    }

    private static Cluster initializeCassandraServer(
            final String name,
            final String keyspace,
            final String hostname,
            final int port) {

        final Cluster cluster;
        try {
            cluster = Cluster.builder()
                    .addContactPoints(Collections.singleton(InetAddress.getByName(hostname)))
                    .withClusterName(name)
                    .withPort(port)
                    .withCodecRegistry(CodecRegistry.DEFAULT_INSTANCE)
                    .build();
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
        cluster.getConfiguration().getCodecRegistry().register(InstantCodec.instance);
        cluster.getConfiguration().getCodecRegistry().register(new EnumNameCodec<>(Operator.class));
        cluster.getConfiguration().getCodecRegistry().register(new EnumNameCodec<>(Context.class));

        final Session session = cluster.newSession();

        final scala.collection.immutable.Map<String, Object> replication =
                JavaConverters.<String, Object>mapAsScalaMapConverter(
                        OBJECT_MAPPER.convertValue(DEFAULT_REPLICATION, MAP_TYPE_REFERENCE))
                        .asScala()
                        .toMap(Predef.conforms());

        //Registry registry = null;
        //try {
        final Registry registry = Registry.fromDirectory(new File("./conf/cassandra/migration/" + name));
            /*
                    new File(
                            CassandraServerHelper.class.getClassLoader().getResource("cassandra/migration/" + name).toURI()));
            if (registry.all().isEmpty()) {
                registry = Registry.fromDirectory(
                        new File(
                                CassandraServerHelper.class.getClassLoader().getResource("cassandra/migration/" + name).toExternalForm()));
            }
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }*/

        final Migrator migrator = new CassandraMigrator(registry);
        session.init();
        migrator.initialize(session, keyspace, new ReplicationOptions(replication));
        session.execute("USE " + keyspace);
        migrator.migrate(session, Option.<Date>none().asScala());

        CASSANDRA_SERVER_MAP.put(name, cluster);
        return cluster;
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
    private static final Map<String, Cluster> CASSANDRA_SERVER_MAP = Maps.newHashMap();
    private static final String METRICS_DATABASE = "default";
    private static final String METRICS_KEYSPACE = "portal";
    private static final int DEFAULT_CASSANDRA_PORT = 9042;
    private static final ObjectNode DEFAULT_REPLICATION;

    static {
        DEFAULT_REPLICATION = OBJECT_MAPPER.createObjectNode();
        DEFAULT_REPLICATION.put("class", "SimpleStrategy");
        DEFAULT_REPLICATION.put("replication_factor", "1");
    }
}
