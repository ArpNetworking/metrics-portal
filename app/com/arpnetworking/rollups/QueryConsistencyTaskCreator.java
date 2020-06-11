package com.arpnetworking.rollups;

import akka.actor.ActorRef;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;

import java.time.Instant;
import java.util.Iterator;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class QueryConsistencyTaskCreator implements Consumer<MetricsQuery> {
    private static final Random RANDOM = new Random();

    private final float _checkFraction;
    private final ActorRef _consistencyChecker;

    public QueryConsistencyTaskCreator(final float _checkFraction, final ActorRef _consistencyChecker) {
        this._checkFraction = _checkFraction;
        this._consistencyChecker = _consistencyChecker;
    }

    @Override
    public void accept(MetricsQuery query) {
        if (!query.getStartTime().isPresent()) {
            // TODO: log
            return;
        }

        if (!query.getEndTime().isPresent()) {
            // TODO: log
            return;
        }

        final Instant startTime = query.getStartTime().get();
        final Instant endTime = query.getEndTime().get();

        query.getMetrics().stream()
                .map(Metric::getName)
                .map(RollupMetric::fromRollupMetricName)
                .forEach(rollupMetricMaybe ->
                        rollupMetricMaybe.ifPresent(rollupMetric -> {
                            checkerTasks(startTime, endTime, rollupMetric)
                                    .filter(tasj -> RANDOM.nextFloat() < _checkFraction)
                                    // TODO: wait for consistency checker to process the message before sending more?
                                    .forEach(task -> _consistencyChecker.tell(task, ActorRef.noSender()));
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

    private static Stream<Instant> periodStreamForInterval(Instant startTime, Instant endTime, RollupPeriod period) {
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
            this._periodStart = _start;
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
