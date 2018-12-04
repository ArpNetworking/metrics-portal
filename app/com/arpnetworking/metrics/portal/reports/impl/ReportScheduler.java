/*
 * Copyright 2018 Dropbox
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
package com.arpnetworking.metrics.portal.reports.impl;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.persistence.AbstractPersistentActorWithTimers;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

import com.arpnetworking.metrics.portal.reports.ReportRepository;
import com.arpnetworking.metrics.portal.reports.ReportSpec;
import com.arpnetworking.play.ProxyClient;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public class ReportScheduler extends AbstractPersistentActorWithTimers {

    private static class ReportExecutor extends AbstractActor {
        public static Props props(final ReportRepository repository, final String specId) {
            return Props.create(ReportExecutor.class, () -> new ReportExecutor(repository, specId));
        }

        public ReportExecutor(ReportRepository repository, String specId) {
            ReportSpec spec = repository.getSpec(specId);
            if (spec == null) {
                LOGGER.warn("attempting to run job with id="+specId+", but does not exist in repository");
                return;
            }
            try {
                spec.run();
            } catch (Exception e) {
                LOGGER.error("error executing job id="+specId+": "+e);
            }
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().build();
        }
        private static final Logger LOGGER = LoggerFactory.getLogger(ReportScheduler.class);
    }

    /**
     * Props factory.
     *
     * @param repository a
     * @return a new props to create this actor
     */
    public static Props props(final ReportRepository repository) {
        return Props.create(ReportScheduler.class, () -> new ReportScheduler(repository));
    }

    public static class Job implements Serializable {
        public String id;
        public Instant nextRun;
        public TemporalAmount period;
        public Job(String id, Instant nextRun, TemporalAmount period) {
            this.id = id;
            this.nextRun = nextRun;
            this.period = period;
        }
        public Job nextJob() {
            return new Job(id, nextRun.plus(period), period);
        }
        private static final long serialVersionUID = 1L;
    }

    private static class Tick implements Serializable {
        private static final long serialVersionUID = 1L;
        private Tick() {}
        public static final Tick INSTANCE = new Tick();
    }
    private static class ExecuteNext implements Serializable {
        private static final long serialVersionUID = 1L;
        private ExecuteNext() {}
        public static final ExecuteNext INSTANCE = new ExecuteNext();
    }
    public static class Schedule implements Serializable {
        public Job job;
        public Schedule(Job job) {
            this.job = job;
        }
        private static final long serialVersionUID = 1L;
    }

    private PriorityQueue<Job> plan = new PriorityQueue<>();

    private ReportRepository repository;
    @Inject
    public ReportScheduler(final ReportRepository repository) {
        this.repository = repository;
        timers().startPeriodicTimer("TICK", new Tick(), Duration.apply(3, TimeUnit.SECONDS /* TODO: when done testing, MINUTES */));
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(ExecuteNext.class, e -> plan.remove())
                .match(Schedule.class, e -> plan.add(e.job))
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, e -> {
                    if (plan.size() > 0 && plan.peek().nextRun.isBefore(Instant.now())) {
                        self().tell(ExecuteNext.INSTANCE, self());
                    }
                })
                .match(ExecuteNext.class, e -> {
                    Job j;
                    try {
                        j = plan.remove();
                    } catch (NoSuchElementException err) {
                        LOGGER.warn("received ExecuteNext, but no jobs are planned");
                        return;
                    }
                    plan.add(j.nextJob());

                    context().actorOf(ReportExecutor.props(repository, j.id));
                    self().tell(Tick.INSTANCE, self());
                })
                .match(Schedule.class, e -> {
                    LOGGER.info("scheduling new job id="+e.job.id);
                    plan.add(e.job);
                })
                .build();
    }

    @Override
    public String persistenceId() {
        return "com.arpnetworking.metrics.portal.reports.impl.ReportScheduler";
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportScheduler.class);

}
