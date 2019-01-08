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

import com.arpnetworking.metrics.portal.scheduling.Schedule;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * A (possibly recurring) job describing a task to perform and how often to repeat.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface Job {

    /**
     * The unique identifier of the job.
     *
     * @return The unique identifier of the job.
     */
    UUID getId();

    /**
     * @return The schedule on which the Job should be repeated.
     */
    Schedule getSchedule();

    /**
     * @return A {@link CompletionStage} that completes exceptionally iff the job throws an exception.
     */
    CompletionStage<Void> start();
}

