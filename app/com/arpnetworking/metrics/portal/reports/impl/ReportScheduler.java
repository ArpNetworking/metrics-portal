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

import akka.actor.Props;
import akka.persistence.AbstractPersistentActorWithTimers;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

import akka.persistence.SnapshotOffer;
import com.arpnetworking.metrics.portal.reports.Job;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import com.arpnetworking.metrics.portal.reports.ReportSpec;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public class ReportScheduler extends AbstractPersistentActorWithTimers {

    private static final int SNAPSHOT_INTERVAL = 1000;


    /**
     * Props factory.
     *
     * @param repository a
     * @return a new props to create this actor
     */
    public static Props props(final JobRepository repository) {
        return Props.create(ReportScheduler.class, () -> new ReportScheduler(repository));
    }

    public static class ScheduledJob {
        private Instant whenRun;
        private String jobId;

        public ScheduledJob(Instant whenRun, String jobId) {
            this.whenRun = whenRun;
            this.jobId = jobId;
        }

        public Instant getWhenRun() {
            return whenRun;
        }

        public void setWhenRun(Instant whenRun) {
            this.whenRun = whenRun;
        }

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }
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
        public ScheduledJob job;
        public Schedule(ScheduledJob job) {
            this.job = job;
        }
        private static final long serialVersionUID = 1L;
    }

    private PriorityQueue<ScheduledJob> plan = new PriorityQueue<>(Comparator.comparing(ScheduledJob::getWhenRun));

    private JobRepository repository;
    @Inject
    public ReportScheduler(final JobRepository repository) {
        this.repository = repository;
        timers().startPeriodicTimer("TICK", new Tick(), Duration.apply(3, TimeUnit.SECONDS /* TODO: when done testing, MINUTES */));
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(ExecuteNext.class, e -> plan.remove())
                .match(Schedule.class, e -> plan.add(e.job))
                .match(SnapshotOffer.class, ss -> {
                    plan.clear(); getContext().getSystem().scheduler();
                    try {
                        // This is basically just `plan = new PriorityQueue<>(ss.snapshot())`,
                        //  but needs to be more convoluted to avoid delayed ClassCastExceptions.
                        // There might be a more elegant way.
                        for (Object j : (PriorityQueue) ss.snapshot()) {
                            plan.add((ScheduledJob) j);
                        }
                    } catch (ClassCastException e) {
                        LOGGER.error("expected snapshot of type PriorityQueue<Job>, but got type "+ss.snapshot().getClass());
                    }
                })
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, tick -> {
                    if (plan.size() > 0 && plan.peek().getWhenRun().isBefore(Instant.now())) {
                        self().tell(ExecuteNext.INSTANCE, self());
                    }
                    if (lastSequenceNr() > SNAPSHOT_INTERVAL) {
                        saveSnapshot(new PriorityQueue<>(plan));
                    }
                })
                .match(ExecuteNext.class, execute -> {
                    final ScheduledJob sj;
                    try {
                        sj = plan.peek();
                    } catch (NoSuchElementException err) {
                        LOGGER.warn("received ExecuteNext, but no jobs are planned");
                        return;
                    }

                    final String id = sj.getJobId();

                    final Job j = repository.get(id);
                    if (j == null) {
                        LOGGER.warn("found job id="+sj.getJobId()+", but no such job exists");
                        return;
                    }

                    ScheduledJob next = new ScheduledJob(sj.whenRun.plus(j.getPeriod()), id);
                    persistAll(new ArrayList<Object>(/*execute, next*/), persisted -> {
                        if (persisted == execute)
                        LOGGER.info("executing job id="+id);
                        plan.add(next);
                        context().actorOf(JobExecutor.props(j)); // Does this even need to be in a separate actor?
                        self().tell(Tick.INSTANCE, self());
                    });

                })
                .match(Schedule.class, e ->
                    persist(e, _e -> {
                        LOGGER.info("scheduling new job id="+_e.job.getJobId());
                        plan.add(_e.job);
                    })
                )
                .build();
    }

    @Override
    public String persistenceId() {
        return "com.arpnetworking.metrics.portal.reports.impl.ReportScheduler";
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportScheduler.class);

}
