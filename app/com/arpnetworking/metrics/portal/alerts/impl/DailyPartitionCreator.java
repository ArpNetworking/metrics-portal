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

package com.arpnetworking.metrics.portal.alerts.impl;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.CallableSql;
import io.ebean.EbeanServer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.persistence.PersistenceException;

/**
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DailyPartitionCreator extends AbstractActorWithTimers {
    /* package private */ static final Object TICK = new Object();
    /* package private */ static final Object EXECUTE = new Object();

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyPartitionCreator.class);
    private static final Duration TICK_INTERVAL = Duration.ofMinutes(1);
    private final EbeanServer _ebeanServer;
    private final PeriodicMetrics _periodicMetrics;

    private final int _lookahead;
    private final String _schema;
    private final String _table;

    private Optional<Instant> _lastRun;
    private final Schedule _schedule;

    private DailyPartitionCreator(
            final EbeanServer ebeanServer,
            final PeriodicMetrics periodicMetrics,
            final String schema,
            final String table,
            final Duration scheduleOffset,
            final int lookahead
    ) {
        _ebeanServer = ebeanServer;
        _periodicMetrics = periodicMetrics;
        _lookahead = lookahead;
        _schedule = new PeriodicSchedule.Builder()
                .setOffset(scheduleOffset)
                .setPeriod(ChronoUnit.DAYS)
                .setRunAtAndAfter(Instant.MIN)
                .setZone(ZoneOffset.UTC)
                .build();
        _lastRun = Optional.empty();
        _schema = schema;
        _table = table;
    }

    /**
     * Creates a {@link Props} for this actor.
     *
     * @param ebeanServer the ebean server
     * @param periodicMetrics metrics instance to use
     * @param schema The database schema name
     * @param table The parent table name
     * @param scheduleOffset Execution offset from midnight
     * @param lookahead maximum number of partitions to create in advance
     * @return A new Props.
     */
    public static Props props(
            final EbeanServer ebeanServer,
            final PeriodicMetrics periodicMetrics,
            final String schema,
            final String table,
            final Duration scheduleOffset,
            final int lookahead
    ) {
        return Props.create(
                DailyPartitionCreator.class,
                ebeanServer,
                periodicMetrics,
                schema,
                table,
                scheduleOffset,
                lookahead);
    }

    /**
     * Ask the actor referenced by {@code ref} to execute synchronously.
     *
     * @param ref an {@code AlertExecutionPartitionCreator}.
     * @param timeout timeout for the operation
     * @throws ExecutionException if an exception was thrown during execution.
     * @throws InterruptedException if the actor does not reply within the allotted timeout, or if
     * the thread was interrupted for other reasons.
     */
    public static void execute(final ActorRef ref, final Duration timeout) throws ExecutionException, InterruptedException {
            Patterns.ask(
                    ref,
                    EXECUTE,
                    timeout
            )
            .thenCompose(reply -> {
                @SuppressWarnings("unchecked")
                final Optional<PersistenceException> o = (Optional<PersistenceException>) reply;
                final CompletableFuture<Void> future = new CompletableFuture<>();
                o.ifPresent(future::completeExceptionally);
                future.complete(null);
                return future;
            })
            .toCompletableFuture()
            .get();
    }

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .matchEquals(TICK, msg -> tick())
                .matchEquals(EXECUTE, msg -> execute(getSender()))
                .build();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        getTimers().startPeriodicTimer("PERIODIC_TICK", TICK, TICK_INTERVAL);
    }

    private void recordCounter(final String metricName, final long value) {
        final String fullMetric = String.format("executions/partition_manager/%s/%s", _table, metricName);
        _periodicMetrics.recordCounter(fullMetric, value);
    }

    private void tick() {
        recordCounter("tick", 1);

        if (_schedule.nextRun(_lastRun).map(run -> run.isBefore(Instant.now())).orElse(true)) {
            getSelf().tell(EXECUTE, getSelf());
        }
    }

    private void execute(final ActorRef sender) {
        CallableSql sql = _ebeanServer.createCallableSql("{ call create_daily_partition(?, ?, ?, ?) }");

        final LocalDate startDate = ZonedDateTime.now().toLocalDate();
        final LocalDate endDate = startDate.plusDays(_lookahead);

        sql = sql.bind(1, _schema)
                .bind(2, _table)
                .bind(3, startDate)
                .bind(4, endDate);

        LOGGER.info().setMessage("Creating daily partitions for table")
                .addData("schema", _schema)
                .addData("table", _table)
                .addData("startDate", startDate)
                .addData("endDate", endDate)
                .log();

        Optional<PersistenceException> error = Optional.empty();
        try {
            _ebeanServer.execute(sql);
            _lastRun = Optional.of(Instant.now());
        } catch (final PersistenceException e) {
            error = Optional.of(e);
            LOGGER.error().setMessage("Failed to create daily partitions for table")
                    .addData("schema", _schema)
                    .addData("table", _table)
                    .addData("startDate", startDate)
                    .addData("endDate", endDate)
                    .setThrowable(e)
                    .log();
        }
        recordCounter("create", error.isPresent() ? 0 : 1);

        sender.tell(error, getSelf());
    }

}
