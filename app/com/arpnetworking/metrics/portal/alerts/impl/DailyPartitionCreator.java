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
import io.ebean.EbeanServer;
import io.ebean.SqlQuery;

import java.time.Clock;
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
 * An actor that will periodically create table partitions.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DailyPartitionCreator extends AbstractActorWithTimers {
    /* package private */ static final Object TICK = new Object();
    private static final Object START_TICKING = new Object();
    private static final Object EXECUTE = new Object();

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyPartitionCreator.class);
    private static final Duration TICK_INTERVAL = Duration.ofMinutes(1);
    private static final String TICKER_NAME = "PERIODIC_TICK";
    private final EbeanServer _ebeanServer;
    private final PeriodicMetrics _periodicMetrics;

    private final int _lookahead;
    private final String _schema;
    private final String _table;
    private final Clock _clock;
    private final Schedule _schedule;
    private Optional<Instant> _lastRun;

    private DailyPartitionCreator(
            final EbeanServer ebeanServer,
            final PeriodicMetrics periodicMetrics,
            final String schema,
            final String table,
            final Duration scheduleOffset,
            final int lookahead
    ) {
        this(ebeanServer, periodicMetrics, schema, table, scheduleOffset, lookahead, Clock.systemUTC());
    }

    /* package private */ DailyPartitionCreator(
            final EbeanServer ebeanServer,
            final PeriodicMetrics periodicMetrics,
            final String schema,
            final String table,
            final Duration scheduleOffset,
            final int lookahead,
            final Clock clock
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
        _clock = clock;
    }

    /**
     * Create {@link Props} for this actor.
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
                () -> new DailyPartitionCreator(
                    ebeanServer,
                    periodicMetrics,
                    schema,
                    table,
                    scheduleOffset,
                    lookahead
                )
        );
    }

    /**
     * Ask the actor referenced by {@code ref} to start execution.
     * <p>
     * This will execute partition creation exactly once and then periodically thereafter using the actor's props.
     *
     * @param ref an {@code DailyPartitionCreator}.
     * @param timeout timeout for the operation
     * @throws ExecutionException if an exception was thrown during execution.
     * @throws InterruptedException if the actor does not reply within the allotted timeout, or if the actor thread was
     * interrupted for other reasons.
     */
    public static void start(
            final ActorRef ref,
            final Duration timeout
    ) throws ExecutionException, InterruptedException {
        Patterns.ask(
                ref,
                START_TICKING,
                timeout
        )
                .thenCompose(reply -> {
                    @SuppressWarnings("unchecked") final Optional<Throwable> o = (Optional<Throwable>) reply;
                    final CompletableFuture<Void> future = new CompletableFuture<>();
                    o.ifPresent(future::completeExceptionally);
                    future.complete(null);
                    return future;
                })
                .toCompletableFuture()
                .get();
    }

    /**
     * Ask the actor referenced by {@code ref} to stop execution.
     *
     * @param ref an {@code DailyPartitionCreator}.
     * @param timeout timeout for the operation
     * @throws ExecutionException if an exception was thrown during execution.
     * @throws InterruptedException if the actor does not stop within the allotted timeout, or if the actor thread was
     * interrupted for other reasons.
     */
    public static void stop(final ActorRef ref, final Duration timeout) throws ExecutionException, InterruptedException {
        Patterns.gracefulStop(ref, timeout).toCompletableFuture().get();
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        LOGGER.info().setMessage("Actor was stopped")
                .addData("schema", _schema)
                .addData("table", _table)
                .addData("lookahead", _lookahead)
                .log();
    }

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .matchEquals(START_TICKING, msg -> startTicking())
                .matchEquals(TICK, msg -> tick())
                .matchEquals(EXECUTE, msg -> execute())
                .build();
    }

    private void recordCounter(final String metricName, final long value) {
        final String fullMetric = String.format("partition_creator/%s/%s", _table, metricName);
        _periodicMetrics.recordCounter(fullMetric, value);
    }

    // Message handlers

    private void startTicking() {
        if (getTimers().isTimerActive(TICKER_NAME)) {
            getSender().tell(Optional.of(new IllegalStateException("Timer already started")), getSelf());
            return;
        }
        LOGGER.info().setMessage("Starting execution timer")
            .addData("schema", _schema)
            .addData("table", _table)
            .addData("lookahead", _lookahead)
            .log();
        final Optional<Exception> executeResult = execute();
        getTimers().startPeriodicTimer(TICKER_NAME, TICK, TICK_INTERVAL);
        getSender().tell(executeResult, getSelf());
    }

    private void tick() {
        recordCounter("tick", 1);

        final Instant now = _clock.instant();
        if (_schedule.nextRun(_lastRun).map(run -> run.isBefore(now)).orElse(true)) {
            getSelf().tell(EXECUTE, getSelf());
        }
    }

    // Wrapper to propagate any errors that occurred to the caller.
    // This is really only useful on a call to `start`.
    private Optional<Exception> execute() {
        final LocalDate startDate = ZonedDateTime.ofInstant(_clock.instant(), _clock.getZone()).toLocalDate();
        final LocalDate endDate = startDate.plusDays(_lookahead);

        LOGGER.info().setMessage("Creating daily partitions for table")
                .addData("schema", _schema)
                .addData("table", _table)
                .addData("startDate", startDate)
                .addData("endDate", endDate)
                .log();

        Optional<Exception> error = Optional.empty();
        try {
            execute(_schema, _table, startDate, endDate);
            _lastRun = Optional.of(_clock.instant());
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
        return error;
    }

    /**
     * Create a series of daily partitions for the given parameters.
     *
     * @param schema the database schema
     * @param table the parent table
     * @param startDate the start date, inclusive.
     * @param endDate the end date, exclusive.
     */
    protected void execute(final String schema,
                         final String table,
                         final LocalDate startDate,
                         final LocalDate endDate) {
        // While this query does not return anything meaningful semantically,
        // it still returns a "non-empty" void result and so we can't use the
        // ordinarily more appropriate SqlUpdate type.
        final SqlQuery sql = _ebeanServer.createSqlQuery(
                "select * from create_daily_partition(?::text, ?::text, ?::date, ?::date)")
                .setParameter(1, schema)
                .setParameter(2, table)
                .setParameter(3, startDate)
                .setParameter(4, endDate);

        sql.findOneOrEmpty().orElseThrow(() -> new IllegalStateException("Expected a single empty result."));
    }
}
