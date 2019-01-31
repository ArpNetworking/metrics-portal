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
package models.internal.scheduling;

import akka.actor.ActorRef;
import com.arpnetworking.metrics.portal.scheduling.Schedule;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * A (possibly recurring) job describing a task to perform and how often to repeat.
 *
 * @param <T> The type of result the Job computes.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface Job<T> {

    /**
     * The unique identifier of the job.
     *
     * @return The UUID.
     */
    UUID getId();

    /**
     * Gets an <a href="https://en.wikipedia.org/wiki/HTTP_ETag">ETag</a> that changes each time the job changes.
     *
     * @return The ETag.
     */
    String getETag();

    /**
     * Returns the schedule on which the Job should be repeated.
     *
     * @return The schedule.
     */
    Schedule getSchedule();

    /**
     * Starts a particular instant's execution of the job running.
     *
     * @param scheduler The Akka actor that's scheduling this job to be run.
     * @param scheduled The instant that the job is running for. (Should probably have come from {@code getSchedule().nextRun(...)}.)
     * @return A {@link CompletionStage} that completes with the job's result, or with the exception the job encounters (if any).
     */
    CompletionStage<T> execute(ActorRef scheduler, Instant scheduled);
}

