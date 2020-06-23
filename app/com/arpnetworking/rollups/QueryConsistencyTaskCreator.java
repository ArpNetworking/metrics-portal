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

public class QueryConsistencyTaskCreator implements Consumer<MetricsQuery> {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryConsistencyTaskCreator.class);

    private static final Random RANDOM = new Random();

    private final double _checkFraction;
    private final ActorRef _consistencyChecker;

    public QueryConsistencyTaskCreator(final double _checkFraction, final ActorRef _consistencyChecker) {
        this._checkFraction = _checkFraction;
        this._consistencyChecker = _consistencyChecker;
    }

    @Override
    public void accept(MetricsQuery query) {
        if (RANDOM.nextDouble() > _checkFraction) {
            return;
        }

        if (!query.getStartTime().isPresent()) {
            LOGGER.trace()
                    .setMessage("not consistency-checking because no start time present")
                    .addData("query",query)
                    .log();
            return;
        }

        if (!query.getEndTime().isPresent()) {
            LOGGER.trace()
                    .setMessage("not consistency-checking because no end time present")
                    .addData("query",query)
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
                                        } catch (InterruptedException | ExecutionException e) {
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

    private static Stream<ConsistencyChecker.Task> checkerTasks(Instant startTime, Instant endTime, RollupMetric rollupMetric) {
        return periodStreamForInterval(startTime, endTime, rollupMetric.getPeriod())
                .map(periodStartTime -> new ConsistencyChecker.Task.Builder()
                        .setSourceMetricName(rollupMetric.getBaseMetricName())
                        .setRollupMetricName(rollupMetric.getRollupMetricName())
                        .setStartTime(periodStartTime)
                        .setTrigger(ConsistencyChecker.Task.Trigger.QUERIED)
                        .setPeriod(rollupMetric.getPeriod())
                        .build());
    }

    static Stream<Instant> periodStreamForInterval(Instant startTime, Instant endTime, RollupPeriod period) {
        final PeriodIterator periods = new PeriodIterator(startTime, endTime, period);
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        periods,
                        Spliterator.ORDERED)
                , false);
    }


    static class PeriodIterator implements Iterator<Instant> {
        Instant _periodStart;
        final Instant _end;
        final RollupPeriod _rollupPeriod;

        public PeriodIterator(final Instant _start, final Instant _end, RollupPeriod _rollupPeriod) {
            this._periodStart = _rollupPeriod.truncate(_start);
            this._end = _end;
            this._rollupPeriod = _rollupPeriod;
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
