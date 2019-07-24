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
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.typesafe.config.Config;
import scala.concurrent.duration.FiniteDuration;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
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
    private TreeSet<RollupDefinition> _rollupDefinitions;
    private FiniteDuration _refreshInterval;

    private static final Object RECORD_METRICS_MSG = new Object();
    private static final String METRICS_TIMER = "metrics_timer";
    private static final FiniteDuration METRICS_INTERVAL = FiniteDuration.apply(1, TimeUnit.SECONDS);

    /**
     * Metrics discovery constructor.
     *
     * @param configuration play configuration object
     * @param periodicMetrics periodic metrics client
     */
    @Inject
    public RollupManager(final Config configuration, final PeriodicMetrics periodicMetrics) {
        _refreshInterval = ConfigurationHelper.getFiniteDuration(configuration, "rollup.fetch.interval");
        _periodicMetrics = periodicMetrics;
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
                        RollupFetch.class,
                        work -> {
                            final Optional<RollupDefinition> rollupDefinition = getNextRollup();
                            if (rollupDefinition.isPresent()) {
                                getSender().tell(rollupDefinition.get(), getSelf());
                            } else {
                                getSender().tell(new NoMoreRollups(_refreshInterval.fromNow()), getSelf());
                            }

                        })
                .build();
    }

    private Optional<RollupDefinition> getNextRollup() {
        return Optional.ofNullable(_rollupDefinitions.pollFirst());
    }

    private static class RollupComparator implements Comparator<RollupDefinition>, Serializable {

        private static final long serialVersionUID = -3992696463296110397L;

        @Override
        public int compare(final RollupDefinition def1, final RollupDefinition def2) {
            // It's assumed that if the source, destination and period are the same then they represent
            // the same period for a different (or the same) time range.  We don't check endTime, groupByTags,
            // etc, because if they were different then the resulting rollup would be broken anyway.
            if (Objects.hash(def1.getSourceMetricName(), def1.getDestinationMetricName(), def1.getPeriod())
                    == Objects.hash(def2.getSourceMetricName(), def2.getDestinationMetricName(), def2.getPeriod())) {
                return def1.getStartTime().compareTo(def2.getStartTime());
            } else {
                return 1;
            }
        }
    }
}
