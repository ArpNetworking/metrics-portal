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

import java.util.Optional;
import java.util.UUID;

/**
 * A repository for storing and retrieving instances of {@link ReportingJob}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface ReportingJobRepository {
    /**
     * Open the <code>ReportingJobRepository</code>.
     */
    void open();

    /**
     * Close the <code>ReportingJobRepository</code>.
     */
    void close();

    /**
     * Get a <code>ReportingJob</code> by identifier.
     *
     * @param identifier The <code>ReportingJob</code> identifier.
     * @return The matching <code>ReportingJob</code>, if any, otherwise <code>Optional.empty()</code>.
     */
    Optional<ReportingJob> getJob(UUID identifier);

    /**
     * Create or update a <code>ReportingJob</code>.

     * @param job The <code>ReportingJob</code> to create or update.
     */
    void addOrUpdateJob(ReportingJob job);

    /**
     * Delete a <code>ReportingJob</code> by identifier.
     *
     * @param identifier - The <code>ReportingJob</code> identifier.
     */
    void deleteJob(UUID identifier);
}

