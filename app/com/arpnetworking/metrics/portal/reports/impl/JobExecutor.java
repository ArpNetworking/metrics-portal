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
package com.arpnetworking.metrics.portal.reports.impl;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.arpnetworking.metrics.portal.reports.Job;

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
                    e.getJob().getSpec().render()
                            .thenAccept(r -> e.getJob().getSink().send(r))
                            .handle((nil, err) -> {
                                e.getNotifiee().tell(
                                        (err == null) ? Success.INSTANCE : new Failure(err),
                                        getSelf()
                                );
                                return null;
                            });
                })
                .build();
    }

    /**
     * Message to tell a {@link JobExecutor} to execute a job and notify another actor when it finishes.
     */
    public static final class Execute {
        private final Job _job;
        private final ActorRef _notifiee;

        /**
         * @param job The job to execute.
         * @param notifiee The actor to notify when execution finishes.
         */
        public Execute(final Job job, final ActorRef notifiee) {
            _job = job;
            _notifiee = notifiee;
        }

        public Job getJob() {
            return _job;
        }

        public ActorRef getNotifiee() {
            return _notifiee;
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
    public static class Failure {
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
