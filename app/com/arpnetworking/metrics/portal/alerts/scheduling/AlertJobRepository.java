/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.alerts.scheduling;

import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.scheduling.JobQuery;
import com.arpnetworking.metrics.portal.scheduling.JobRepository;
import com.google.common.collect.ImmutableList;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultJobQuery;
import models.internal.impl.DefaultQueryResult;
import models.internal.scheduling.Job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;

/**
 * A {@code JobRepository} for alert evaluation jobs.
 * <p>
 * This class is meant to act as an adapter, wrapping an {@link AlertRepository}
 * and allowing for the construction of {@link AlertJob}s from alert instances
 * defined in that repository.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertJobRepository implements JobRepository<AlertEvaluationResult> {
    private final AlertRepository _repo;
    private final AlertExecutionContext _context;

    /**
     * Default constructor.
     *
     * @param repo The alert repository to wrap.
     * @param context The alert execution context.
     */
    @Inject
    public AlertJobRepository(
            final AlertRepository repo,
            final AlertExecutionContext context
    ) {
        _repo = repo;
        _context = context;
    }

    @Override
    public void open() {
        _repo.open();
    }

    @Override
    public void close() {
        _repo.close();
    }

    @Override
    public Optional<Job<AlertEvaluationResult>> getJob(
            final UUID id, final Organization organization
    ) {
        return _repo.getAlert(id, organization)
                .map(a -> new AlertJob(a, _context));
    }

    @Override
    public JobQuery<AlertEvaluationResult> createJobQuery(final Organization organization) {
        return new DefaultJobQuery<>(this, organization);
    }

    @Override
    public QueryResult<Job<AlertEvaluationResult>> queryJobs(final JobQuery<AlertEvaluationResult> query) {
        final QueryResult<Alert> queryResult = _repo.createAlertQuery(query.getOrganization())
                .offset(query.getOffset().orElse(0))
                .limit(query.getLimit())
                .execute();
        final List<Job<AlertEvaluationResult>> values = queryResult.values()
                .stream()
                .map(a -> new AlertJob(a, _context))
                .collect(ImmutableList.toImmutableList());
        return new DefaultQueryResult<>(values, queryResult.total());
    }
}
