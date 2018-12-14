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

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.persistence.AbstractPersistentActorWithTimers;
import akka.persistence.SnapshotOffer;
import com.arpnetworking.metrics.portal.reports.Job;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
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
        return props(repository, Clock.systemUTC());
    }
    protected static Props props(final JobRepository repository, final Clock clock) {
        return Props.create(JobScheduler.class, () -> new JobScheduler(repository, clock));
    }

    public static class ScheduledJob {
        private final Instant whenRun;
        private final String jobId;

        public ScheduledJob(Instant whenRun, String jobId) {
            this.whenRun = whenRun;
            this.jobId = jobId;
        }

        @Override
        public String toString() {
            return "ScheduledJob{" +
                    "whenRun=" + whenRun +
                    ", jobId='" + jobId + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScheduledJob that = (ScheduledJob) o;
            return Objects.equals(whenRun, that.whenRun) &&
                    Objects.equals(jobId, that.jobId);
        }

        @Override
        public int hashCode() {

            return Objects.hash(whenRun, jobId);
        }

        public Instant getWhenRun() {
            return whenRun;
        }

        public String getJobId() {
            return jobId;
        }
    }

    protected static class Tick implements Serializable {
        private static final long serialVersionUID = 1L;
        public static final Tick INSTANCE = new Tick();
    }

    private interface Command {}
    public static class ScheduleCmd implements Command {
        public ScheduledJob job;
        public ScheduleCmd(ScheduledJob job) {
            this.job = job;
        }
    }
    public static class GetPlanCmd implements Command {
        public static final GetPlanCmd INSTANCE = new GetPlanCmd();
    }

    private interface Event extends Serializable {}
    private static class AddJobEvt implements Event {
        public ScheduledJob job;
        public AddJobEvt(ScheduledJob job) {
            this.job = job;
        }
        private static final long serialVersionUID = 1L;
    }
    private static class RemoveJobEvt implements Event {
        public static final RemoveJobEvt INSTANCE = new RemoveJobEvt();
        private static final long serialVersionUID = 1L;
    }

    private static class State {
        public PriorityQueue<ScheduledJob> plan = new PriorityQueue<>(Comparator.comparing(ScheduledJob::getWhenRun));
    }

    private State state = new State();

    private final JobRepository repository;
    private final Clock clock;

    private JobScheduler(final JobRepository repository, final Clock clock) {
        this.repository = repository;
        this.clock = clock;
        timers().startPeriodicTimer("TICK", new Tick(), Duration.apply(1, TimeUnit.MINUTES));
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(Event.class, this::updateState)
                .match(SnapshotOffer.class, ss -> {
                    try {
                        state = (State) ss.snapshot();
                    } catch (ClassCastException e) {
                        LOGGER.error()
                                .setMessage("got non-JobScheduler.State snapshot")
                                .setThrowable(e)
                                .addData("actual_type", ss.snapshot().getClass().getCanonicalName())
                                .log();
                    }
                })
                .build();
    }

    private Object handleCommand(Command c) {
        if (c instanceof ScheduleCmd) {
            updateState(new AddJobEvt(((ScheduleCmd) c).job));
            return true;
        } else if (c instanceof GetPlanCmd) {
            return new PriorityQueue<>(state.plan);
        } else {
            LOGGER.error()
                    .setMessage("got Command of unrecognized type")
                    .addData("type", c.getClass())
                    .log();
            return null;
        }
    }

    private void updateState(Event e) {
        if (e instanceof AddJobEvt) {
            state.plan.add(((AddJobEvt) e).job);
        }
        else if (e instanceof RemoveJobEvt) {
            state.plan.remove();
        }
        else {
            LOGGER.error()
                    .setMessage("got Event of unrecognized type")
                    .addData("type", e.getClass())
                    .log();
        }
        if (lastSequenceNr() % SNAPSHOT_INTERVAL == 0) {
            saveSnapshot(state);
        }
    }

    private void runNext(ActorRef notifiee) {
        final ScheduledJob sj = state.plan.peek();
        if (sj == null) {
            throw new NoSuchElementException("can't run next job when none are scheduled");
        }
        final String id = sj.getJobId();

        final Job j = repository.get(id);
        if (j == null) {
            LOGGER.error()
                    .setMessage("job in queue with nonexistent id")
                    .addData("id", sj.getJobId())
                    .log();
            // We need to remove the job from the plan, or else it'll block everything else.
            persist(RemoveJobEvt.INSTANCE, this::updateState);
            return;
        }


        List<Event> events = new ArrayList<>();
        events.add(RemoveJobEvt.INSTANCE);
        Instant nextRun = j.getSchedule().nextRun(sj.whenRun);
        if (nextRun != null) {
            events.add(new AddJobEvt(new ScheduledJob(j.getSchedule().nextRun(sj.whenRun), id)));
        }
        persistAll(events, this::updateState);

        getContext().actorOf(JobExecutor.props(j, notifiee));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, e -> {
                    if (state.plan.size() > 0 && state.plan.peek().getWhenRun().isBefore(clock.instant())) {
                        this.runNext(getSender());
                        getSelf().tell(Tick.INSTANCE, getSelf());
                    }
                })
                .match(Command.class, c -> getSender().tell(this.handleCommand(c), getSelf()))
                .build();
    }

    @Override
    public String persistenceId() {
        return "com.arpnetworking.metrics.portal.reports.impl.JobScheduler";
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduler.class);

}
