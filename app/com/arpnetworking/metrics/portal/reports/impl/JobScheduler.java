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
import akka.persistence.SnapshotOffer;
import com.arpnetworking.metrics.portal.reports.Job;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

public class JobScheduler extends AbstractPersistentActorWithTimers {

    private static final int SNAPSHOT_INTERVAL = 1000;


    /**
     * Props factory.
     *
     * @param repository a
     * @return a new props to create this actor
     */
    public static Props props(final JobRepository repository) {
        return Props.create(JobScheduler.class, () -> new JobScheduler(repository));
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

    private interface Command {}
    public static class ScheduleCmd implements Command {
        public ScheduledJob job;
        public ScheduleCmd(ScheduledJob job) {
            this.job = job;
        }
    }

    private interface Event {}
    private static class AddJobEvt implements Event, Serializable {
        public ScheduledJob job;
        public AddJobEvt(ScheduledJob job) {
            this.job = job;
        }
        private static final long serialVersionUID = 1L;
    }
    private static class RemoveJobEvt implements Event, Serializable {
        private RemoveJobEvt() {}
        public static final RemoveJobEvt INSTANCE = new RemoveJobEvt();
        private static final long serialVersionUID = 1L;
    }

    private PriorityQueue<ScheduledJob> plan = new PriorityQueue<>(Comparator.comparing(ScheduledJob::getWhenRun));

    private JobRepository repository;
    @Inject
    public JobScheduler(final JobRepository repository) {
        this.repository = repository;
        timers().startPeriodicTimer("TICK", new Tick(), Duration.apply(3, TimeUnit.SECONDS /* TODO: when done testing, MINUTES */));
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(Event.class, this::updateState)
                .match(SnapshotOffer.class, ss -> {
                    plan.clear();
                    try {
                        // This is basically just `plan = new PriorityQueue<>(ss.snapshot())`,
                        //  but needs to be more convoluted to avoid delayed ClassCastExceptions.
                        // There might be a more elegant way.
                        for (Object j : (PriorityQueue) ss.snapshot()) {
                            plan.add((ScheduledJob) j);
                        }
                    } catch (ClassCastException e) {
                        LOGGER.error()
                                .setMessage("got non-PriorityQueue<ScheduledJob> snapshot")
                                .setThrowable(e)
                                .addData("actual_type", ss.snapshot().getClass().getCanonicalName())
                                .log();
                    }
                })
                .build();
    }

    protected void updateState(Event e) {
        if (e instanceof AddJobEvt) {
            plan.add(((AddJobEvt) e).job);
        }
        else if (e instanceof RemoveJobEvt) {
            plan.remove();
        }
        else {
            LOGGER.error()
                    .setMessage("got Event of unrecognized type")
                    .addData("type", e.getClass())
                    .log();
        }
    }

    protected void runNext() {
        final ScheduledJob sj = plan.peek();
        final String id = sj.getJobId();

        final Job j = repository.get(id);
        if (j == null) {
            LOGGER.error()
                    .setMessage("job in queue with nonexistent id")
                    .addData("id", sj.getJobId())
                    .log();
            return;
        }

        List<Event> events = Arrays.asList(
                RemoveJobEvt.INSTANCE,
                new AddJobEvt(new ScheduledJob(j.getSchedule().nextRun(sj.whenRun), id)));
        persistAll(events, this::updateState);

        j.getSpec().render().thenAccept(j.getSink()::send);
    }

    protected void tick() {
        while (plan.size() > 0 && plan.peek().getWhenRun().isBefore(Instant.now())) {
            this.runNext();
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, e -> tick())
                .match(ScheduleCmd.class, e -> updateState(new AddJobEvt(e.job)))
                .build();
    }

    @Override
    public String persistenceId() {
        return "com.arpnetworking.metrics.portal.reports.impl.JobScheduler";
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduler.class);

}
