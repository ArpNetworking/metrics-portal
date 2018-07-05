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
package com.arpnetworking.metrics.portal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.Map;

/**
 * Creates test connections to an embedded cassandra node.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class CassandraConnectionFactory {
    public static Map<String, Object> generateConfiguration(
            final String clusterName,
            final String keyspace,
            final String host,
            final int port) {
        return new ImmutableMap.Builder<String, Object>()
                .put("cassandra.db.default.hosts", Lists.newArrayList(host))
                .put("cassandra.db.default.port", port)
                .put("cassandra.db.default.keyspace", keyspace)
                .put("cassandra.db.default.clusterName", clusterName)
                .put("cassandra.db.default.replication.class", "SimpleStrategy")
                .put("cassandra.db.default.replication.replication_factor", 1)
                .build();
    }
}
