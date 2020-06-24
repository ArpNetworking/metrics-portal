/*
 * Copyright 2020 Dropbox Inc.
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
package com.arpnetworking.rollups;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Randomly samples some queries to be forwarded to the rollup consistency checker (if they contain rollups at all).
 *
 * @author William Ehlhardt (whale at dropbox dot com)
 */
public class QueryConsistencyTaskCreator implements Consumer<MetricsQuery> {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryConsistencyTaskCreator.class);

    private static final Random RANDOM = new Random();

    private final double _checkFraction;
    private final ActorRef _consistencyChecker;

    /**
     * Constructor.
     *
     * @param checkFraction      Fraction of queries to send for checking.
     * @param consistencyChecker Destination consistency checker actor.
     */
    public QueryConsistencyTaskCreator(final double checkFraction, final ActorRef consistencyChecker) {
        this._checkFraction = checkFraction;
        this._consistencyChecker = consistencyChecker;
    }

    @Override
    public void accept(final MetricsQuery query) {
        if (RANDOM.nextDouble() > _checkFraction) {
            return;
        }

        if (!query.getStartTime().isPresent()) {
            LOGGER.trace()
                    .setMessage("not consistency-checking because no start time present")
                    .addData("query", query)
                    .log();
            return;
        }

        if (!query.getEndTime().isPresent()) {
            LOGGER.trace()
                    .setMessage("not consistency-checking because no end time present")
                    .addData("query", query)
                    .log();
            return;
        }

        LOGGER.trace()
                .setMessage("maybe sending for consistency check?")
                .addData("query", query)
                .log();

        final Instant startTime = query.getStartTime().get();
        final Instant endTime = query.getEndTime().get();

        query.getMetrics().stream()
                .map(Metric::getName)
                .map(RollupMetric::fromRollupMetricName)
                .forEach(rollupMetricMaybe ->
                        rollupMetricMaybe.ifPresent(rollupMetric -> {
                            checkerTasks(startTime, endTime, rollupMetric)
                                    .forEach(task -> {
                                        LOGGER.trace()
                                                .setMessage("sending for consistency check")
                                                .addData("task", task)
                                                .addData("query", query)
                                                .log();
                                        // The consistency checker actor is expected to be running on the same node, to
                                        // respond ~immediately to a task send, and to drop tasks if there are too many
                                        // to fit into the queue. Hence, blockingly send them to the checker actor as
                                        // fast as it'll accept them.
                                        try {
                                            Patterns.ask(_consistencyChecker, task, Duration.ofSeconds(1)).toCompletableFuture().get();
                                        } catch (final InterruptedException | ExecutionException e) {
                                            if (!(e.getCause() instanceof ConsistencyChecker.BufferFull)) {
                                                LOGGER.error()
                                                        .setMessage("unexpected exception sending task to consistency checker")
                                                        .setThrowable(e)
                                                        .addData("task", task)
                                                        .addData("query", query)
                                                        .log();
                                            }
                                        }
                                    });
                        })
                );

    }

    private static Stream<ConsistencyChecker.Task> checkerTasks(
            final Instant startTime, final Instant endTime, final RollupMetric rollupMetric) {
        return periodStreamForInterval(startTime, endTime, rollupMetric.getPeriod())
                .map(periodStartTime -> new ConsistencyChecker.Task.Builder()
                        .setSourceMetricName(rollupMetric.getBaseMetricName())
                        .setRollupMetricName(rollupMetric.getRollupMetricName())
                        .setStartTime(periodStartTime)
                        .setTrigger(ConsistencyChecker.Task.Trigger.QUERIED)
                        .setPeriod(rollupMetric.getPeriod())
                        .build());
    }

    static Stream<Instant> periodStreamForInterval(
            final Instant startTime, final Instant endTime, final RollupPeriod period) {
        final PeriodIterator periods = new PeriodIterator(startTime, endTime, period);
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        periods,
                        Spliterator.ORDERED),
                false);
    }


    private static class PeriodIterator implements Iterator<Instant> {
        private Instant _periodStart;
        private final Instant _end;
        private final RollupPeriod _rollupPeriod;

        PeriodIterator(final Instant start, final Instant end, final RollupPeriod rollupPeriod) {
            this._periodStart = rollupPeriod.mostRecentBoundary(start);
            this._end = end;
            this._rollupPeriod = rollupPeriod;
        }

        @Override
        public boolean hasNext() {
            return _periodStart.isBefore(_end);
        }

        @Override
        public Instant next() {
            final Instant ret = _periodStart;
            _periodStart = _rollupPeriod.nextPeriodStart(_periodStart);
            return ret;
        }
    }


}
