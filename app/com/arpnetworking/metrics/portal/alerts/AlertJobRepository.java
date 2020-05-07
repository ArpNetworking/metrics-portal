/*
 * Copyright 2020 Groupon.com
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
package com.arpnetworking.metrics.portal.alerts;

import com.arpnetworking.metrics.portal.scheduling.JobQuery;
import com.arpnetworking.metrics.portal.scheduling.JobRepository;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.FiringAlertResult;
import models.internal.impl.DefaultJobQuery;
import models.internal.impl.DefaultQueryResult;
import models.internal.scheduling.Job;

import java.util.Optional;
import java.util.UUID;

/**
 * A repository of alert jobs.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class AlertJobRepository implements JobRepository<FiringAlertResult> {

    private final AlertRepository _alertRepository;

    /**
     * Construct a AlertJobRepository from an AlertRepository.
     * @param alertRepository The alert repository to wrap.
     */
    @Inject
    public AlertJobRepository(final AlertRepository alertRepository) {
        _alertRepository = alertRepository;
    }

    @Override
    public void open() {
        // It doesn't make sense to handle the AlertRepository lifecycle here,
        // it's handled elsewhere.
    }

    @Override
    public void close() {
        // It doesn't make sense to handle the AlertRepository lifecycle here,
        // it's handled elsewhere.
    }

    @Override
    public Optional<Job<FiringAlertResult>> getJob(final UUID id, final Organization organization) {
        // TODO: proxy and map the alertRepository
        return Optional.empty();
    }

    @Override
    public JobQuery<FiringAlertResult> createJobQuery(final Organization organization) {
        return new DefaultJobQuery<>(this, organization);
    }

    @Override
    public QueryResult<Job<FiringAlertResult>> queryJobs(final JobQuery<FiringAlertResult> query) {
        return new DefaultQueryResult<>(ImmutableList.of(), 0);
    }
}
