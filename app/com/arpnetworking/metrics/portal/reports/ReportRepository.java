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
import com.arpnetworking.metrics.portal.scheduling.JobRepository;
import models.internal.Organization;
import models.internal.reports.Report;

import java.util.Optional;
import java.util.UUID;

/**
 * A repository for storing and retrieving instances of {@link Report}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface ReportRepository extends JobRepository<Report.Result> {
    /**
     * Get a {@code Report} by identifier.
     *
     * @param identifier The {@code Report} identifier.
     * @param organization The {@code Organization} which owns the report.
     * @return The matching {@code Report}, if any, otherwise {@link Optional#empty()}.
     */
    Optional<Report> getReport(UUID identifier, Organization organization);

    /**
     * Create or update a {@code Report}.
     *
     * @param report The {@code Report} to create or update.
     * @param organization The {@code Organization} which owns the report.
     */
    void addOrUpdateReport(Report report, Organization organization);
}

