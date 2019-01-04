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

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.pattern.PatternsCS;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import models.internal.scheduling.Job;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * A one-shot actor that executes a single {@link Job}, notifies another actor when finished, and then self-terminates.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobExecutor extends AbstractActor {

    /**
     * Props factory.
     *
     * @return A new Props to create this actor.
     */
    public static Props props() {
        return Props.create(JobExecutor.class, JobExecutor::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Execute.class, e -> {

                    // No matter what, we want this actor to shut down after executing its job,
                    //  because a one-off job-execution is this actor's entire purpose.
                    getSelf().tell(PoisonPill.getInstance(), getSelf());

                    final Optional<Job> job = e.getRepo().getJob(e.getJobId());
                    if (!job.isPresent()) {
                        LOGGER.error()
                                .setMessage("repository has no job with given id")
                                .addData("repo", e.getRepo())
                                .addData("jobId", e.getJobId())
                                .log();
                        e.getNotifiee().tell(new Failure(new NoSuchElementException(e.getJobId().toString())), getSelf());
                        return;
                    }

                    PatternsCS.pipe(
                            job.get().start().handle((r, err) -> err == null ? Success.INSTANCE : new Failure(err)),
                            getContext().dispatcher()
                    ).to(e.getNotifiee());
                })
                .build();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutor.class);

    /**
     * Message to tell a {@link JobExecutor} to execute a job and notify another actor when it finishes.
     */
    public static final class Execute {
        private final JobRepository _repo;
        private final UUID _jobId;
        private final ActorRef _notifiee;

        private Execute(final Builder builder) {
            _repo = builder._repo;
            _jobId = builder._jobId;
            _notifiee = builder._notifiee;
        }

        public JobRepository getRepo() {
            return _repo;
        }

        public UUID getJobId() {
            return _jobId;
        }

        public ActorRef getNotifiee() {
            return _notifiee;
        }

        /**
         * Builder implementation for {@link JobExecutor}.
         */
        public static final class Builder extends OvalBuilder<Execute> {
            private JobRepository _repo;
            private UUID _jobId;
            private ActorRef _notifiee;
            /**
             * Public constructor.
             */
            public Builder() {
                super(Execute::new);
            }

            /**
             * The {@link JobRepository} the JobExecutor should retrieve its job from. Required. Cannot be null.
             *
             * @param repo The repo.
             * @return This instance of <code>Builder</code>.
             */
            public Builder setRepo(final JobRepository repo) {
                _repo = repo;
                return this;
            }

            /**
             * The id of the job that the Executor should retrieve from its repository. Required. Cannot be null.
             *
             * @param jobId The job id.
             * @return This instance of <code>Builder</code>.
             */
            public Builder setJobId(final UUID jobId) {
                _jobId = jobId;
                return this;
            }

            /**
             * The ActorRef that the {@link JobExecutor} should notify when the job finishes running. Required. Cannot be null.
             *
             * @param notifiee The ActorRef.
             * @return This instance of <code>Builder</code>.
             */
            public Builder setNotifiee(final ActorRef notifiee) {
                _notifiee = notifiee;
                return this;
            }
        }
    }

    /**
     * A message sent to an executor's <code>notifiee</code> when rendering&sending complete successfully.
     */
    public static final class Success {
        /**
         * The only instance of Success.
         */
        public static final Success INSTANCE = new Success();
        private Success() {}
    }
    /**
     * A message sent to an executor's <code>notifiee</code> if rendering/sending fails.
     */
    public static final class Failure {
        private final Throwable _throwable;

        /**
         * @param throwable The reason the execution failed.
         */
        public Failure(final Throwable throwable) {
            _throwable = throwable;
        }

        public Throwable getThrowable() {
            return _throwable;
        }
    }


}
