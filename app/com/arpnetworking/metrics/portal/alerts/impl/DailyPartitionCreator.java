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
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.Sets;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.ebean.EbeanServer;
import io.ebean.Transaction;
import net.sf.oval.constraint.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.persistence.PersistenceException;

/**
 * An actor that will periodically create table partitions.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DailyPartitionCreator extends AbstractActorWithTimers {
    /* package private */ static final Object TICK = "MSG_TICK";
    private static final Logger LOGGER = LoggerFactory.getLogger(DailyPartitionCreator.class);
    private static final Duration TICK_INTERVAL = Duration.ofMinutes(1);
    private static final String TICKER_NAME = "PERIODIC_TICK";

    private final EbeanServer _ebeanServer;
    private final PeriodicMetrics _periodicMetrics;
    private final Set<LocalDate> _partitionCache;

    private final int _lookaheadDays;
    private final String _schema;
    private final String _table;
    private final Clock _clock;
    private final Schedule _schedule;
    private Optional<Instant> _lastRun;
    private final Executor _executor;

    private DailyPartitionCreator(
            final EbeanServer ebeanServer,
            final PeriodicMetrics periodicMetrics,
            final String schema,
            final String table,
            final Duration scheduleOffset,
            final int lookahead,
            final Executor executor
        ) {
        this(ebeanServer, periodicMetrics, schema, table, scheduleOffset, lookahead, Clock.systemUTC(), executor);
    }

    /* package private */ DailyPartitionCreator(
            final EbeanServer ebeanServer,
            final PeriodicMetrics periodicMetrics,
            final String schema,
            final String table,
            final Duration scheduleOffset,
            final int lookaheadDays,
            final Clock clock,
            final Executor executor
    ) {
        _ebeanServer = ebeanServer;
        _periodicMetrics = periodicMetrics;
        _lookaheadDays = lookaheadDays;
        _schedule = new PeriodicSchedule.Builder()
                .setOffset(scheduleOffset)
                .setPeriod(ChronoUnit.DAYS)
                .setRunAtAndAfter(Instant.EPOCH)
                .setZone(ZoneOffset.UTC)
                .build();
        _lastRun = Optional.empty();
        _schema = schema;
        _table = table;
        _clock = clock;
        _partitionCache = Sets.newHashSet();
        _executor = executor;
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
            final int lookahead,
            final Executor executor
    ) {
        return Props.create(
                DailyPartitionCreator.class,
                () -> new DailyPartitionCreator(
                    ebeanServer,
                    periodicMetrics,
                    schema,
                    table,
                    scheduleOffset,
                    lookahead,
                    executor
                )
        );
    }

    /**
     * Ask the actor referenced by {@code ref} to create the partition(s) needed
     * for the given instant.
     *
     * @param ref an {@code DailyPartitionCreator}.
     * @param instant The instant being recorded
     * @param timeout timeout for the operation
     *
     * @return A future that completes when the operation does.
     */
    public static CompletionStage<Void> ensurePartitionExistsForInstant(
            final ActorRef ref,
            final Instant instant,
            final Duration timeout
    ) {
        final LocalDate date = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).toLocalDate();
        final CreateForRange.Builder createPartitions = new CreateForRange.Builder()
                .setStart(date)
                .setEnd(date.plusDays(1));
        return Patterns.askWithReplyTo(
                ref,
                replyTo -> createPartitions.setReplyTo(replyTo).build(),
                timeout
        ).thenApply(ignore -> null);
    }

    @Override
    public void preStart() {
        LOGGER.info().setMessage("Starting execution timer")
                .addData("schema", _schema)
                .addData("table", _table)
                .addData("lookahead", _lookaheadDays)
                .log();
        getSelf().tell(TICK, getSelf());
        getTimers().startPeriodicTimer(TICKER_NAME, TICK, TICK_INTERVAL);
    }


    @Override
    public void preRestart(final Throwable error, final Optional<Object> msg) {
        LOGGER.info().setMessage("Actor is crashing")
                .addData("schema", _schema)
                .addData("table", _table)
                .addData("lookahead", _lookaheadDays)
                .addData("error", error)
                .addData("msg", msg)
                .log();
    }

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .matchEquals(TICK, msg -> tick())
                .match(CreateForRangeComplete.class, msg -> {
                    _lastRun = Optional.of(msg.getExecutedAt());
                    updateCache(msg.getStart(), msg.getEnd());

                    msg.getReplyTo().ifPresent(replyTo -> {
                        final Status.Status resp = msg.getError()
                                .map(err -> (Status.Status) new Status.Failure(err))
                                .orElseGet(() -> new Status.Success(null));

                        replyTo.tell(resp, self());
                    });
                })
                .match(CreateForRange.class, msg -> {
                    execute(msg.getStart(), msg.getEnd(), msg.getReplyTo());
                })
                .build();
    }

    private void recordCounter(final String metricName, final long value) {
        final String fullMetric = String.format("partition_creator/%s/%s", _table, metricName);
        _periodicMetrics.recordCounter(fullMetric, value);
    }

    private void recordTimer(final String metricName, final Duration duration) {
        final String fullMetric = String.format("partition_creator/%s/%s", _table, metricName);
        _periodicMetrics.recordTimer(fullMetric, duration.toMillis(), Optional.of(TimeUnit.MILLISECONDS));
    }

    // Message handlers

    private void tick() {
        LOGGER.info("tick");
        recordCounter("tick", 1);

        final Instant now = _clock.instant();
        final Optional<Instant> nextRun = _schedule.nextRun(_lastRun);
        if (nextRun.isPresent() && nextRun.get().compareTo(now) <= 0) {
            final LocalDate startDate = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).toLocalDate();
            final LocalDate endDate = startDate.plusDays(_lookaheadDays);

            final CreateForRange createPartitions = new CreateForRange.Builder()
                    .setStart(startDate)
                    .setEnd(endDate)
                    .build();
            getSelf().tell(createPartitions, getSelf());
            return;
        }
        LOGGER.info()
            .setMessage("tick received too soon, skipping.")
            .addData("nextRun", nextRun)
            .addData("lastRun", _lastRun)
            .log();
    }

    private void execute(final LocalDate startDate, final LocalDate endDate, final Optional<ActorRef> replyTo) {

        // Much like other portions of the codebase dealing with time, the dates
        // used in this class are all fixed to UTC. So while the code in this
        // method uses a LocalDate, there's an implicit assumption that all
        // dates are UTC and these conversions happen at the interaction
        // boundary (tick, ensurePartitionExists).

        LocalDate d = startDate;
        boolean allPartitionsExist = true;
        while (d.compareTo(endDate) <= 0) {
            if (!_partitionCache.contains(d)) {
                allPartitionsExist = false;
                break;
            }
            d = d.plusDays(1);
        }
        if (allPartitionsExist) {
            LOGGER.debug()
                    .setMessage("partitions already exist, ignoring execute request")
                    .addData("schema", _schema)
                    .addData("table", _table)
                    .addData("startDate", startDate)
                    .addData("endDate", endDate)
                    .log();
            // Just reply directly instead of going through CreateForRangeComplete
            replyTo.ifPresent(ref -> ref.tell(new Status.Success(null), self()));
            return;
        }

        LOGGER.info()
                .setMessage("Creating daily partitions for table")
                .addData("schema", _schema)
                .addData("table", _table)
                .addData("startDate", startDate)
                .addData("endDate", endDate)
                .log();

        final Instant start = _clock.instant();
        final CompletionStage<CreateForRangeComplete> messageFut =
            execute(_schema, _table, startDate, endDate)
                    .handle((ignore, error) -> {
                        // The system clock is thread-safe, although the safety of
                        // any other implementations is unclear.
                        final Instant completedAt = _clock.instant();
                        recordTimer("create_latency", Duration.between(start, completedAt));
                        recordCounter("create", error == null ? 1 : 0);
                        if (error != null) {
                            LOGGER.error()
                                    .setMessage("Failed to create daily partitions for table")
                                    .addData("schema", _schema)
                                    .addData("table", _table)
                                    .addData("startDate", startDate)
                                    .addData("endDate", endDate)
                                    .setThrowable(error)
                                    .log();
                        }
                        final CreateForRangeComplete.Builder msgBuilder = new CreateForRangeComplete.Builder()
                                .setStart(startDate)
                                .setEnd(endDate)
                                .setError(error)
                                .setExecutedAt(completedAt);
                        replyTo.ifPresent(msgBuilder::setReplyTo);
                        return msgBuilder.build();
                    });
        Patterns.pipe(messageFut, getContext().getDispatcher()).to(self());
    }

    private void updateCache(final LocalDate start, final LocalDate end) {
        LocalDate date = start;
        while (date.compareTo(end) <= 0) {
            _partitionCache.add(date);
            date = date.plusDays(1);
        }
    }

    /**
     * Create a series of daily partitions for the given parameters.
     *
     * @param schema the database schema
     * @param table the parent table
     * @param startDate the start date, inclusive.
     * @param endDate the end date, exclusive.
     *
     * @return A future representing completion of this operation.
     */
    protected CompletionStage<Void> execute(
            final String schema,
            final String table,
            final LocalDate startDate,
            final LocalDate endDate
    ) {
        // The closest thing Ebean provides to executing raw sql is SqlQuery, or SqlUpdate.
        // Neither works for table creation, and Ebean's recommendation in these
        // cases is just to use the raw JDBC connection.
        //
        // SQLQuery was originally used but does not actually allow for any writes,
        // while SQLUpdate does not allow for SELECT statements.
        //
        // https://ebean.io/docs/intro/queries/jdbc-query
        //
        // TODO(cbriones): Move DB operation off the dispatcher thread pool.
        return CompletableFuture.runAsync(() -> {
            final String sql = "SELECT portal.create_daily_partition(?, ?, ?, ?);";
            try (Transaction tx = _ebeanServer.beginTransaction()) {
                final Connection conn = tx.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, schema);
                    stmt.setString(2, table);
                    stmt.setDate(3, java.sql.Date.valueOf(startDate));
                    stmt.setDate(4, java.sql.Date.valueOf(endDate));
                    stmt.execute();
                }
                tx.commit();
            } catch (final SQLException e) {
                throw new PersistenceException("Could not create daily partitions", e);
            }
        }, _executor);
    }

    private static final class CreateForRange {
        private final LocalDate _start;
        private final LocalDate _end;
        private final Optional<ActorRef> _replyTo;

        private CreateForRange(final Builder builder) {
            _start = builder._start;
            _end = builder._end;
            _replyTo = Optional.ofNullable(builder._replyTo);
        }

        public LocalDate getStart() {
            return _start;
        }

        public LocalDate getEnd() {
            return _end;
        }

        public Optional<ActorRef> getReplyTo() {
            return _replyTo;
        }

        static final class Builder extends OvalBuilder<CreateForRange> {
            private LocalDate _start;
            private LocalDate _end;
            @Nullable
            private ActorRef _replyTo;

            Builder() {
                super(CreateForRange::new);
            }

            /**
             * Sets the start.
             *
             * @param start the start.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setStart(final LocalDate start) {
                _start = start;
                return this;
            }

            /**
             * Sets the end.
             *
             * @param end the end.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setEnd(final LocalDate end) {
                _end = end;
                return this;
            }

            /**
             * Sets the reply to.
             *
             * @param replyTo the reply to.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setReplyTo(final ActorRef replyTo) {
                _replyTo = replyTo;
                return this;
            }
        }
    }

    private static final class CreateForRangeComplete {
        private final LocalDate _start;
        private final LocalDate _end;
        private final Optional<ActorRef> _replyTo;
        private final Instant _executedAt;
        private final Optional<Throwable> _error;

        private CreateForRangeComplete(final CreateForRangeComplete.Builder builder) {
            _start = builder._start;
            _end = builder._end;
            _replyTo = Optional.ofNullable(builder._replyTo);
            _executedAt = builder._executedAt;
            _error = Optional.ofNullable(builder._error);
        }

        public LocalDate getStart() {
            return _start;
        }

        public LocalDate getEnd() {
            return _end;
        }

        public Optional<ActorRef> getReplyTo() {
            return _replyTo;
        }

        public Instant getExecutedAt() {
            return _executedAt;
        }

        public Optional<Throwable> getError() {
            return _error;
        }

        static final class Builder extends OvalBuilder<CreateForRangeComplete> {
            @Nullable
            private Throwable _error;
            private LocalDate _start;
            private LocalDate _end;
            @Nullable
            private ActorRef _replyTo;
            @NotNull
            private Instant _executedAt;

            Builder() {
                super(CreateForRangeComplete::new);
            }

            /**
             * Sets the start.
             *
             * @param start the start.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setStart(final LocalDate start) {
                _start = start;
                return this;
            }

            /**
             * Sets the end.
             *
             * @param end the end.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setEnd(final LocalDate end) {
                _end = end;
                return this;
            }

            /**
             * Sets the reply to.
             *
             * @param replyTo the reply to.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setReplyTo(final ActorRef replyTo) {
                _replyTo = replyTo;
                return this;
            }

            /**
             * Sets the executedAt.
             *
             * @param value the executed at.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setExecutedAt(final Instant value) {
                _executedAt = value;
                return this;
            }

            /**
             * Sets the error. Default is null.
             *
             * @param error the error.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setError(@Nullable final Throwable error) {
                _error = error;
                return this;
            }
        }

    }
}
