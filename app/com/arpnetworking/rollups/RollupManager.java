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

import akka.actor.AbstractActorWithTimers;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableSet;
import scala.concurrent.duration.FiniteDuration;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * Actor for holding and dispatching rollup definitions.  This allows for different mechanisms, e.g. automated
 * rollups and manual backfills, to be ordered, staged and de-duplicated before being acted upon.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class RollupManager extends AbstractActorWithTimers {
    private final PeriodicMetrics _periodicMetrics;
    private final MetricsFactory _metricsFactory;
    private TreeSet<RollupDefinition> _rollupDefinitions;
    private RollupPartitioner _partitioner;

    private static final Object RECORD_METRICS_MSG = new Object();
    private static final String METRICS_TIMER = "metrics_timer";
    private static final FiniteDuration METRICS_INTERVAL = FiniteDuration.apply(1, TimeUnit.SECONDS);
    private static final Logger LOGGER = LoggerFactory.getLogger(RollupManager.class);

    /**
     * Metrics discovery constructor.
     *
     * @param periodicMetrics periodic metrics client
     * @param metricsFactory metrics factory
     * @param partitioner {@link RollupPartitioner} to split up failed jobs
     */
    @Inject
    public RollupManager(final PeriodicMetrics periodicMetrics, final MetricsFactory metricsFactory, final RollupPartitioner partitioner) {
        _periodicMetrics = periodicMetrics;
        _metricsFactory = metricsFactory;
        _partitioner = partitioner;
        _rollupDefinitions = new TreeSet<>(new RollupComparator());
        getTimers().startPeriodicTimer(METRICS_TIMER, RECORD_METRICS_MSG, METRICS_INTERVAL);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(
                        RECORD_METRICS_MSG,
                        work -> _periodicMetrics.recordGauge("rollup/manager/queue_size", _rollupDefinitions.size()))
                .match(
                        RollupDefinition.class,
                        work -> _rollupDefinitions.add(work))
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
        try (Metrics metrics = _metricsFactory.create()) {
            metrics.addAnnotation("rollup_metric", message.getRollupDefinition().getDestinationMetricName());
            metrics.incrementCounter("rollup/manager/executor_finished", 1);

            final Optional<Throwable> failure = message.getFailure();
            if (!failure.isPresent()) {
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
                children = _partitioner.splitJob(message.getRollupDefinition());
            } catch (final RollupPartitioner.CannotSplitException e) {
                LOGGER.error()
                        .setMessage("giving up on job that can't be split any more")
                        .addData("rollupDefinition", message.getRollupDefinition())
                        .setThrowable(failure.get())
                        .log();
                metrics.addAnnotation("outcome", "unable_to_split");
                return;
            }

            LOGGER.info()
                    .setMessage("splitting and retrying job")
                    .addData("parent", message.getRollupDefinition())
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

    private static class RollupComparator implements Comparator<RollupDefinition>, Serializable {

        private static final long serialVersionUID = -3992696463296110397L;

        @Override
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
