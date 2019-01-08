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

package com.arpnetworking.metrics.portal.reports;

// TODO(cwbriones): Decouple from ebean model and expose via the internal model.
import models.ebean.ReportingJob;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * A repository for storing and retrieving instances of {@link ReportingJob}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface ReportingJobRepository {
    /**
     * Open the {@code ReportingJobRepository}.
     */
    void open();

    /**
     * Close the {@code ReportingJobRepository}.
     */
    void close();

    /**
     * Get a {@code ReportingJob} by identifier.
     *
     * @param identifier The {@code ReportingJob} identifier.
     * @return The matching {@code ReportingJob}, if any, otherwise {@link Optional#empty()}.
     */
    Optional<ReportingJob> getJob(UUID identifier);

    /**
     * Create or update a {@code ReportingJob}.
     *
     * @param job The {@code ReportingJob} to create or update.
     */
    void addOrUpdateJob(ReportingJob job);

    /**
     * Mark a {@code ReportingJob} as completed.
     *
     * @param job The {@code ReportingJob} to update.
     * @param result The completion result of the job, i.e whether it succeeded or failed.
     * @param completionTime The time of completion.
     */
    void jobCompleted(ReportingJob job, ReportingJob.Result result, ZonedDateTime completionTime);
}

