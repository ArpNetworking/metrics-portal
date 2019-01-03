/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.scheduling;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.persistence.AbstractPersistentActorWithTimers;
import akka.persistence.SnapshotOffer;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import models.internal.scheduling.Job;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Manages execution of recurring {@link Job}s.
 *
 * @author Spencer Pearson
 */
public final class JobScheduler extends AbstractPersistentActorWithTimers {

    private static final int SNAPSHOT_INTERVAL = 1000;

    private State _state = new State();

    /**
     * Props factory.
     *
     * @param repository The {@link JobRepository} that the scheduler should fish jobs out of when it needs to execute them.
     * @return A new props to create this actor.
     */
    public static Props props(final JobRepository repository) {
        return props(repository, Clock.systemUTC());
    }

    /**
     * Props factory.
     *
     * @param repository The {@link JobRepository} that the scheduler should fish jobs out of when it needs to execute them.
     * @param clock The clock the scheduler will use, when it ticks, to determine whether it's time to run the next job(s) yet.
     * @return A new props to create this actor.
     */
    protected static Props props(final JobRepository repository, final Clock clock) {
        return Props.create(JobScheduler.class, () -> new JobScheduler(repository, clock));
    }


    private final JobRepository _repository;
    private final Clock _clock;

    @Inject
    private JobScheduler(final JobRepository repository, final Clock clock) {
        _repository = repository;
        _clock = clock;
        timers().startPeriodicTimer("TICK", new Tick(), Duration.apply(1, TimeUnit.MINUTES));
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(Event.class, this::updateState)
                .match(SnapshotOffer.class, ss -> {
                    try {
                        _state = (State) ss.snapshot();
                    } catch (final ClassCastException e) {
                        LOGGER.error()
                                .setMessage("got non-JobScheduler.State snapshot")
                                .setThrowable(e)
                                .addData("actual_type", ss.snapshot().getClass().getCanonicalName())
                                .log();
                    }
                })
                // TODO(spencerpearson): when recovery finishes, we might want to fast-forward until plan.peek() is in the future.
                .build();
    }

    private @Nullable Object handleCommand(final Command c) {
        if (c instanceof ScheduleCmd) {
            if (((ScheduleCmd) c).getJob().getWhenRun().isBefore(_clock.instant())) {
                return false;
            }
            updateState(new AddJobEvt(((ScheduleCmd) c).getJob()));
            return true;
        } else if (c instanceof GetPlanCmd) {
            return new PriorityQueue<>(_state.getPlan());
        } else {
            LOGGER.error()
                    .setMessage("got Command of unrecognized type")
                    .addData("type", c.getClass())
                    .log();
            return null;
        }
    }

    private void updateState(final Event e) {
        if (e instanceof AddJobEvt) {
            _state.getPlan().add(((AddJobEvt) e).getJob());
        } else if (e instanceof RemoveJobEvt) {
            _state.getPlan().remove();
        } else {
            LOGGER.error()
                    .setMessage("got Event of unrecognized type")
                    .addData("type", e.getClass())
                    .log();
        }
        if (lastSequenceNr() % SNAPSHOT_INTERVAL == 0) {
            saveSnapshot(_state);
        }
    }

    private void runNext(final ActorRef notifiee) {
        final ScheduledJob sj = _state.getPlan().peek();
        if (sj == null) {
            throw new NoSuchElementException("can't run next job when none are scheduled");
        }
        final UUID id = sj.getJobId();

        final Optional<Job> j = _repository.getJob(id);
        if (!j.isPresent()) {
            LOGGER.error()
                    .setMessage("job in queue with nonexistent id")
                    .addData("id", sj.getJobId())
                    .log();
            // We need to remove the job from the plan, or else it'll block everything else.
            persist(RemoveJobEvt.INSTANCE, this::updateState);
            return;
        }


        final List<Event> events = new ArrayList<>();
        events.add(RemoveJobEvt.INSTANCE);
        j.get().getSchedule().nextRun(sj.getWhenRun(), _clock.instant()).ifPresent(nextRun -> {
            events.add(new AddJobEvt(new ScheduledJob(nextRun, id)));
        });
        persistAll(events, this::updateState);

        getContext().actorOf(JobExecutor.props()).tell(
                new JobExecutor.Execute.Builder().setRepo(_repository).setJobId(id).setNotifiee(notifiee).build(),
                notifiee
        );
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, e -> {
                    if (_state.getPlan().size() > 0 && _state.getPlan().peek().getWhenRun().isBefore(_clock.instant())) {
                        this.runNext(getSender());
                        getSelf().tell(Tick.INSTANCE, getSelf());
                    }
                })
                .match(Command.class, c -> getSender().tell(this.handleCommand(c), getSelf()))
                .build();
    }

    @Override
    public String persistenceId() {
        return "com.arpnetworking.metrics.portal.scheduling.JobScheduler";
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduler.class);


    /**
     * Internal message, telling the scheduler to run any necessary jobs.
     */
    protected static final class Tick implements Serializable {
        private static final long serialVersionUID = 1L;
        public static final Tick INSTANCE = new Tick();
    }

    private interface Command {}
    /**
     * Tells the scheduler to enqueue a {@link ScheduledJob}.
     * Gets a Boolean reply indicating whether the scheduling succeeded.
     */
    public static final class ScheduleCmd implements Command {
        private final ScheduledJob _job;
        /**
         * @param job The {@link ScheduledJob} to add to the plan.
         */
        public ScheduleCmd(final ScheduledJob job) {
            _job = job;
        }

        public ScheduledJob getJob() {
            return _job;
        }
    }
    /**
     * Asks the scheduler for its current plan.
     * Reply is a PriorityQueue<{@link ScheduledJob}> containing all planned job-executions.
     */
    public static final class GetPlanCmd implements Command {
        /**
         * The only instance of GetPlanCmd.
         */
        public static final GetPlanCmd INSTANCE = new GetPlanCmd();
        private GetPlanCmd() {}
    }

    private interface Event extends Serializable {}
    private static final class AddJobEvt implements Event {
        private final ScheduledJob _job;
        AddJobEvt(final ScheduledJob job) {
            _job = job;
        }

        public ScheduledJob getJob() {
            return _job;
        }

        private static final long serialVersionUID = 1L;
    }
    private static final class RemoveJobEvt implements Event {
        public static final RemoveJobEvt INSTANCE = new RemoveJobEvt();
        private static final long serialVersionUID = 1L;
    }

    private static final class State {
        private final PriorityQueue<ScheduledJob> _plan = new PriorityQueue<>(Comparator.comparing(ScheduledJob::getWhenRun));

        public PriorityQueue<ScheduledJob> getPlan() {
            return _plan;
        }
    }

}
