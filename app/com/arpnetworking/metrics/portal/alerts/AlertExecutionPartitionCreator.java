/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.alerts;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.CallableSql;
import io.ebean.EbeanServer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class AlertExecutionPartitionCreator extends AbstractActorWithTimers {
    /* package private */ static final Object TICK = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertExecutionPartitionCreator.class);
    private static final Duration TICK_INTERVAL = Duration.ofMinutes(1);
    private final EbeanServer _ebeanServer;
    private final PeriodicMetrics _periodicMetrics;
    private final int _lookahead;

    private AlertExecutionPartitionCreator(
            final EbeanServer ebeanServer,
            final PeriodicMetrics periodicMetrics,
            final int lookahead
    ) {
        _ebeanServer = ebeanServer;
        _periodicMetrics = periodicMetrics;
        _lookahead = lookahead;
    }

    /**
     * Creates a {@link Props} for this actor.
     *
     * @param ebeanServer the ebean server
     * @param periodicMetrics metrics instance to use
     * @param lookahead maximum number of partitions to create in advance
     * @return A new Props.
     */
    public static Props props(
            final EbeanServer ebeanServer,
            final PeriodicMetrics periodicMetrics,
            final int lookahead
    ) {
        return Props.create(AlertExecutionPartitionCreator.class, ebeanServer, periodicMetrics, lookahead);
    }

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .matchEquals(TICK, msg -> tick())
                .build();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        getSelf().tell(TICK, getSelf());
        getTimers().startPeriodicTimer("PERIODIC_TICK", TICK, TICK_INTERVAL);
    }

    private void recordCounter(final String metricName, final long value) {
        _periodicMetrics.recordCounter("alerts/executions/partition_manager/" + metricName, value);
    }

    private void tick() {
        recordCounter("tick", 1);

        // TODO(cbriones): Only create the partitions if they don't already exist.
        // TODO(cbriones): Perhaps this should be configurable by table and lookahead.
        // TODO(cbriones): Only bind when the repository is bound.
        final CallableSql sql = _ebeanServer.createCallableSql("{ call create_daily_partition(?, ?, ?) }");
        sql.bind(0, "portal.alert_executions");
        sql.bind(0, Instant.now());
        sql.bind(1, Instant.now().plus(_lookahead, ChronoUnit.DAYS));
        _ebeanServer.execute(sql);
    }
}
