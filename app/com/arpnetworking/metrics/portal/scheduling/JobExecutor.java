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
import akka.actor.Props;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;

import java.util.NoSuchElementException;

/**
 * An actor that executes a {@link Job}, i.e. renders it and sends out the resultant Report.
 *
 * @author Spencer Pearson
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

                    // No matter what, we want this actor to shut down immediately after executing it job,
                    //  because a one-off job-execution is this actor's entire purpose.
                    getContext().getSystem().stop(getSelf());

                    final Job j = e.getRepo().get(e.getJobId());
                    if (j == null) {
                        LOGGER.error()
                                .setMessage("repository has no job with given id")
                                .addData("repo", e.getRepo())
                                .addData("jobId", e.getJobId())
                                .log();
                        e.getNotifiee().tell(new Failure(new NoSuchElementException(e.getJobId())), getSelf());
                        return;
                    }

                    j.start().handle((result, err) -> {
                        e.getNotifiee().tell(
                                err == null ? Success.INSTANCE : new Failure(err),
                                getSelf()
                        );
                        return null;
                    });
                })
                .build();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutor.class);

    /**
     * Message to tell a {@link JobExecutor} to execute a job and notify another actor when it finishes.
     */
    public static final class Execute {
        private final JobRepository _repo;
        private final String _jobId;
        private final ActorRef _notifiee;

        private Execute(final Builder builder) {
            _repo = builder._repo;
            _jobId = builder._jobId;
            _notifiee = builder._notifiee;
        }

        public JobRepository getRepo() {
            return _repo;
        }

        public String getJobId() {
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
            private String _jobId;
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
             * The id of the job that the Executor should retrieve from its repository.
             *
             * @param jobId The job id.
             * @return This instance of <code>Builder</code>.
             */
            public Builder setJobId(final String jobId) {
                _jobId = jobId;
                return this;
            }

            /**
             * The ActorRef that the {@link JobExecutor} should notify when the job finishes running.
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
