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
package com.arpnetworking.metrics.portal.scheduling;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.inject.Injector;
import models.internal.Organization;
import models.internal.impl.DefaultOrganization;
import models.internal.scheduling.Job;
import net.sf.oval.constraint.NotNull;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

/**
 * A serializable reference to a {@link Job}.
 *
 * @param <T> The type of the result computed by the job.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Loggable
public final class JobRef<T> implements Serializable {
    private final Class<? extends JobRepository<T>> _repositoryType;
    private final UUID _jobId;
    private final UUID _orgId;

    private JobRef(final Builder<T> builder) {
        _repositoryType = builder._repositoryType;
        _jobId = builder._jobId;
        _orgId = builder._orgId;
    }

    /**
     * Loads the {@link Job} that this ref refers to.
     *
     * @param injector The Guice injector to load the repository through.
     * @return The referenced job, or {@code empty} if the repository contains no such job.
     */
    public Optional<? extends Job<T>> get(final Injector injector) {
        return getRepository(injector).getJob(_jobId, getOrganization());
    }

    /**
     * Loads the {@link JobRepository} that {@code repositoryType} refers to.
     *
     * @param injector The Guice injector to load the repository through.
     * @return The repository that the given injector has for this ref's {@code repositoryType}.
     */
    public JobRepository<T> getRepository(final Injector injector) {
        return injector.getInstance(_repositoryType);
    }

    public UUID getJobId() {
        return _jobId;
    }

    public Organization getOrganization() {
        return new DefaultOrganization.Builder().setId(_orgId).build();
    }

    private static final long serialVersionUID = 1L;

    /**
     * Implementation of builder pattern for {@link JobRef}.
     *
     * @param <T> The type of the result computed by the job.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder<T> extends OvalBuilder<JobRef<T>> {
        @NotNull
        private Class<? extends JobRepository<T>> _repositoryType;
        @NotNull
        private UUID _jobId;
        @NotNull
        private UUID _orgId;

        /**
         * Public constructor.
         */
        public Builder() {
            super(JobRef<T>::new);
        }

        /**
         * The type of the {@link JobRepository} that contains the job. Required. Cannot be null.
         *
         * @param repositoryType The type of the repository, later used to load the repository via Guice.
         * @return This instance of Builder.
         */
        public Builder<T> setRepositoryType(final Class<? extends JobRepository<T>> repositoryType) {
            _repositoryType = repositoryType;
            return this;
        }

        /**
         * The id of the job in the {@code repository}. Required. Cannot be null.
         *
         * @param id The id.
         * @return This instance of Builder.
         */
        public Builder<T> setId(final UUID id) {
            _jobId = id;
            return this;
        }

        /**
         * The {@link Organization} that the job belongs to in the {@code repository}. Required. Cannot be null.
         *
         * @param organization The organization.
         * @return This instance of Builder.
         */
        public Builder<T> setOrganization(final Organization organization) {
            _orgId = organization.getId();
            return this;
        }
    }
}
