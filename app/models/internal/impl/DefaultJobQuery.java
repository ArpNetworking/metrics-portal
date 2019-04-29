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
import com.arpnetworking.metrics.portal.scheduling.JobQuery;
import com.arpnetworking.metrics.portal.scheduling.JobRepository;
import com.google.common.base.MoreObjects;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.scheduling.Job;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Default internal model implementation for a job query.
 *
 * @param <T> The type of result the Job computes.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class DefaultJobQuery<T> implements JobQuery<T> {
    private static final int DEFAULT_LIMIT = 1000;

    private final JobRepository<T> _repository;
    private final Organization _organization;
    private int _limit = DEFAULT_LIMIT;
    @Nullable
    private Integer _offset;

    /**
     * Public constructor.
     *
     * @param repository   The {@code JobRepository<T>} to query against.
     * @param organization The {@code Organization} to search in.
     */
    public DefaultJobQuery(final JobRepository<T> repository, final Organization organization) {
        _repository = repository;
        _organization = organization;
    }

    @Override
    public JobQuery<T> limit(final int limit) {
        _limit = limit;
        return this;
    }

    @Override
    public JobQuery<T> offset(final int offset) {
        _offset = offset;
        return this;
    }

    @Override
    public QueryResult<Job<T>> execute() {
        return _repository.queryJobs(this);
    }

    @Override
    public Organization getOrganization() {
        return _organization;
    }

    @Override
    public int getLimit() {
        return _limit;
    }

    @Override
    public Optional<Integer> getOffset() {
        return Optional.ofNullable(_offset);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("repository", _repository)
                .add("limit", _limit)
                .add("offset", _offset)
                .toString();
    }
}
