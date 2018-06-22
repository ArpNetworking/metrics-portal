/**
 * Copyright 2017 Smartsheet
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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for creating Akka networking config to prevent port collisions.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class AkkaClusteringConfigFactory {

    /**
     * Generates Akka config with a unique network port.
     *
     */
    public static Map<String, Object> generateConfiguration() {
        final int nextCounter = UNIQUE_COUNTER.getAndIncrement();
        final int port = BASE_PORT + nextCounter;
        return ImmutableMap.of(
                "akka.cluster.seed-nodes", Collections.singletonList(String.format("akka.tcp://mportal@127.0.0.1:%d", port)),
                "akka.remote.netty.tcp.hostname", "127.0.0.1",
                "akka.remote.netty.tcp.port", port);
    }

    private static final AtomicInteger UNIQUE_COUNTER = new AtomicInteger(1);
    private static final int BASE_PORT = 20000;
}
