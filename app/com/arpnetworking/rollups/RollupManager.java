/*
 * Copyright 2019 Dropbox Inc.
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

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.pattern.Patterns;
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Actor for holding and dispatching rollup definitions.  This allows for different mechanisms, e.g. automated
 * rollups and manual backfills, to be ordered, staged and de-duplicated before being acted upon.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class RollupManager extends AbstractActorWithTimers {
    private final PeriodicMetrics _periodicMetrics;
    private final MetricsFactory _metricsFactory;
    private TreeSet<RollupDefinition> _rollupDefinitions;
    private RollupPartitioner _partitioner;
    private final ActorRef _consistencyChecker;
    private final double _consistencyCheckFractionOfWrites;
    private Duration _consistencyCheckDelay = Duration.ofSeconds(30);

    private static final Object RECORD_METRICS_MSG = new Object();
    private static final String METRICS_TIMER = "metrics_timer";
    private static final Duration METRICS_INTERVAL = Duration.ofSeconds(1);
    private static final Logger LOGGER = LoggerFactory.getLogger(RollupManager.class);
    private static final Random RANDOM = new Random();
    private static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(1);

    /**
     * Creates a {@link Props} for use in Pekko.
     *
     * @param periodicMetrics periodic metrics client
     * @param metricsFactory metrics factory
     * @param partitioner {@link RollupPartitioner} to split up failed jobs
     * @param consistencyChecker {@link ConsistencyChecker} ref that should be told to consistency-check completed datapoints
     * @param consistencyCheckFractionOfWrites fraction of successfully written datapoints to request consistency-checks for
     * @return A new props to create this actor.
     */
    public static Props props(
            final PeriodicMetrics periodicMetrics,
            final MetricsFactory metricsFactory,
            final RollupPartitioner partitioner,
            final ActorRef consistencyChecker,
            final double consistencyCheckFractionOfWrites
    ) {
        return Props.create(
                RollupManager.class,
                periodicMetrics,
                metricsFactory,
                partitioner,
                consistencyChecker,
                consistencyCheckFractionOfWrites
        );
    }

    private RollupManager(
            final PeriodicMetrics periodicMetrics,
            final MetricsFactory metricsFactory,
            final RollupPartitioner partitioner,
            final ActorRef consistencyChecker,
            final double consistencyCheckFractionOfWrites
    ) {
        _periodicMetrics = periodicMetrics;
        _metricsFactory = metricsFactory;
        _partitioner = partitioner;
        _consistencyChecker = consistencyChecker;
        _consistencyCheckFractionOfWrites = consistencyCheckFractionOfWrites;
        _rollupDefinitions = new TreeSet<>(new RollupComparator());
        getTimers().startTimerAtFixedRate(METRICS_TIMER, RECORD_METRICS_MSG, METRICS_INTERVAL);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(
                        RECORD_METRICS_MSG,
                        work -> _periodicMetrics.recordGauge("rollup/manager/queue_size", _rollupDefinitions.size()))
                .match(
                        RollupDefinition.class,
                        work -> {
                            _periodicMetrics.recordCounter("rollup/manager/submit", 1);
                            LOGGER.debug()
                                    .setMessage("received task")
                                    .addData("task", work)
                                    .log();
                            _rollupDefinitions.add(work);
                        })
                .match(
                        RollupExecutor.FinishRollupMessage.class,
                        this::executorFinished
                )
                .match(
                        RollupFetch.class,
                        fetchMsg -> {
                            _periodicMetrics.recordCounter("rollup/manager/fetch", 1);
                            final Optional<RollupDefinition> rollupDefinition = getNextRollup();
                            if (rollupDefinition.isPresent()) {
                                getSender().tell(rollupDefinition.get(), getSelf());
                            } else {
                                getSender().tell(NoMoreRollups.getInstance(), getSelf());
                            }

                        })
                .build();
    }

    private void executorFinished(final RollupExecutor.FinishRollupMessage message) {
        final RollupDefinition definition = message.getRollupDefinition();
        final double latencyNs = (double) Duration.between(definition.getEndTime(), Instant.now()).toNanos();

        final RollupDefinition defn = message.getRollupDefinition();
        if (shouldRequestConsistencyCheck(message)) {
            EXECUTOR.schedule(
                    () -> RollupManager.requestConsistencyCheck(_consistencyChecker, defn),
                    _consistencyCheckDelay.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            // ^ "Why delay?" Because KairosDB has an internal write-queue that might take a little while
            //   to flush to Cassandra, so we don't quite have read-after-write consistency.
            // (example shelldump: https://pastebin.com/dTq8X5et )
            // Empirically, in simple tests like that, I see discrepancies get resolved in <1sec,
            //   but waiting is cheap, and large write-batches might take longer to flush,
            //   so to be safe, we wait much longer than that 1sec.
        }

        try (Metrics metrics = _metricsFactory.create()) {
            metrics.incrementCounter("rollup/manager/executor_finished", 1);
            metrics.setGauge("rollup/manager/executor_finished/latency_sec", latencyNs / 1e9);

            final Optional<Throwable> failure = message.getFailure();
            if (!failure.isPresent()) {
                LOGGER.trace()
                        .setMessage("rollup finished successfully")
                        .addData("rollupDefinition", message.getRollupDefinition())
                        .log();
                metrics.addAnnotation("outcome", "success");
                return;
            }

            final boolean isRetryable = _partitioner.mightSplittingFixFailure(failure.get());
            if (!isRetryable) {
                LOGGER.warn()
                        .setMessage("giving up after non-retryable error")
                        .addData("rollupDefinition", message.getRollupDefinition())
                        .setThrowable(failure.get())
                        .log();
                metrics.addAnnotation("outcome", "non_retryable_error");
                return;
            }

            final ImmutableSet<RollupDefinition> children;
            try {
                children = _partitioner.splitJob(definition);
            } catch (final RollupPartitioner.CannotSplitException e) {
                LOGGER.error()
                        .setMessage("giving up on job that can't be split any more")
                        .addData("rollupDefinition", definition)
                        .setThrowable(failure.get())
                        .log();
                metrics.addAnnotation("outcome", "unable_to_split");
                return;
            }

            LOGGER.info()
                    .setMessage("splitting and retrying job")
                    .addData("parent", definition)
                    .addData("children", children)
                    .setThrowable(failure.get())
                    .log();
            metrics.addAnnotation("outcome", "split_and_retry");
            children.forEach(child -> getSelf().tell(child, getSelf()));
        }
    }

    private Optional<RollupDefinition> getNextRollup() {
        return Optional.ofNullable(_rollupDefinitions.pollFirst());
    }

    private static void requestConsistencyCheck(final ActorRef consistencyChecker, final RollupDefinition defn) {
        final ConsistencyChecker.Task ccTask = ThreadLocalBuilder.build(ConsistencyChecker.Task.Builder.class, b -> b
                .setSourceMetricName(defn.getSourceMetricName())
                .setRollupMetricName(defn.getDestinationMetricName())
                .setStartTime(defn.getStartTime())
                .setPeriod(defn.getPeriod())
                .setFilterTags(defn.getFilterTags())
                .setTrigger(ConsistencyChecker.Task.Trigger.WRITE_COMPLETED)
        );
        Patterns.ask(consistencyChecker, ccTask, Duration.ofSeconds(10))
                .whenComplete((response, failure) -> {
                    if (failure == null) {
                        LOGGER.debug()
                                .setMessage("consistency-checker queue accepted task")
                                .addData("task", ccTask)
                                .log();
                    } else if (failure instanceof ConsistencyChecker.BufferFull) {
                        LOGGER.warn()
                                .setMessage("consistency-checker task rejected")
                                .addData("task", ccTask)
                                .setThrowable(failure)
                                .log();
                    } else {
                        LOGGER.error()
                                .setMessage("communication with consistency-checker failed")
                                .addData("task", ccTask)
                                .setThrowable(failure)
                                .log();
                    }
                });
    }

    private boolean shouldRequestConsistencyCheck(final RollupExecutor.FinishRollupMessage message) {
        return !message.isFailure() && RANDOM.nextDouble() < _consistencyCheckFractionOfWrites;
    }

    public void setConsistencyCheckDelay(final Duration consistencyCheckDelay) {
        _consistencyCheckDelay = consistencyCheckDelay;
    }

    private static final class RollupComparator implements Comparator<RollupDefinition>, Serializable {

        private static final long serialVersionUID = -3992696463296110397L;

        @Override
        @SuppressFBWarnings("RV_NEGATING_RESULT_OF_COMPARETO")
        public int compare(final RollupDefinition def1, final RollupDefinition def2) {
            if (def1.equals(def2)) {
                return 0;
            }

            int result;
            result = def1.getStartTime().compareTo(def2.getStartTime());
            if (result != 0) {
                // earlier = higher-priority
                return result;
            }
            result = Integer.compare(def1.getFilterTags().size(), def2.getFilterTags().size());
            if (result != 0) {
                // more specific = higher-priority
                return -result;
            }
            result = def1.getPeriod().compareTo(def2.getPeriod());
            if (result != 0) {
                // shorter period = higher-priority
                return result;
            }

            return 1;
        }
    }
}
