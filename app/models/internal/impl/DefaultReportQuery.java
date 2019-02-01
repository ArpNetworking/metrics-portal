/*
 * Copyright 2019 Dropbox, Inc.
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
package models.internal.impl;

import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.reports.ReportQuery;
import com.arpnetworking.metrics.portal.reports.ReportRepository;
import com.arpnetworking.metrics.portal.scheduling.JobQuery;
import com.google.common.base.MoreObjects;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.reports.Report;

import java.util.Optional;

// CHECKSTYLE.OFF: JavadocTypeCheck - Checkstyle does not recognize implNote.
/**
 * Default internal model implementation for a report query.
 *
 * @implNote This should delegate all methods to an instance of {@link JobQuery} when
 * possible. Ideally these types would be within the same class hierarchy but we would
 * be unable to reconcile their return types otherwise.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.ON: JavadocTypeCheck
@Loggable
public final class DefaultReportQuery implements ReportQuery {
    private final ReportRepository _repository;
    private final JobQuery<Report.Result> _jobQuery;

    /**
     * Public constructor.
     *
     * @param repository The {@code ReportRepository} to query against.
     * @param jobQuery   The corresponding {@code JobQuery}.
     */
    public DefaultReportQuery(final ReportRepository repository, final JobQuery<Report.Result> jobQuery) {
        _repository = repository;
        _jobQuery = jobQuery;
    }

    @Override
    public QueryResult<Report> execute() {
        return _repository.query(this);
    }

    // Delegate all other methods to JobQuery

    @Override
    public ReportQuery limit(final int limit) {
        _jobQuery.limit(limit);
        return this;
    }

    @Override
    public ReportQuery offset(final int offset) {
        _jobQuery.offset(offset);
        return this;
    }

    @Override
    public Organization getOrganization() {
        return _jobQuery.getOrganization();
    }

    @Override
    public int getLimit() {
        return _jobQuery.getLimit();
    }

    @Override
    public Optional<Integer> getOffset() {
        return _jobQuery.getOffset();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("repository", _repository)
                .add("jobQuery", _jobQuery)
                .toString();
    }
}
