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
import models.internal.Organization;
import models.internal.scheduling.Job;
import net.sf.oval.constraint.NotNull;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A serializable reference to a {@link Job}.
 *
 * @param <T> The type of the result computed by the job.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobRef<T> implements Serializable, Supplier<Optional<Job<T>>> {
    private final JobRepository<T> _repository;
    private final UUID _id;
    private final Organization _organization;

    private JobRef(final Builder<T> builder) {
        _repository = builder._repository;
        _id = builder._id;
        _organization = builder._organization;
    }

    @Override
    public Optional<Job<T>> get() {
        return _repository.getJob(_id, _organization);
    }

    public JobRepository<T> getRepository() {
        return _repository;
    }

    public UUID getId() {
        return _id;
    }

    public Organization getOrganization() {
        return _organization;
    }

    /**
     * Convenience function to get the last time the job was run from the underlying {@link JobRepository}.
     *
     * @return The time.
     */
    public Optional<Instant> getLastRun() {
        return _repository.getLastRun(_id, _organization);
    }

    /**
     * Convenience function to mark a particular run of the job as having started in the underlying {@link JobRepository}.
     *
     * @param scheduled The time the run was scheduled for.
     */
    public void jobStarted(final Instant scheduled) {
        _repository.jobStarted(_id, _organization, scheduled);
    }

    /**
     * Convenience function to mark a particular run of the job as having completed successfully in the underlying {@link JobRepository}.
     *
     * @param scheduled The time the run was scheduled for.
     * @param result The result that the job computed.
     */
    public void jobSucceeded(final Instant scheduled, final T result) {
        _repository.jobSucceeded(_id, _organization, scheduled, result);
    }

    /**
     * Convenience function to mark a particular run of the job as having failed in the underlying {@link JobRepository}.
     *
     * @param scheduled The time the run was scheduled for.
     * @param error The exception that caused the job to abort.
     */
    public void jobFailed(final Instant scheduled, final Throwable error) {
        _repository.jobFailed(_id, _organization, scheduled, error);
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
        private JobRepository<T> _repository;
        @NotNull
        private UUID _id;
        @NotNull
        private Organization _organization;

        /**
         * Public constructor.
         */
        public Builder() {
            super(JobRef<T>::new);
        }

        /**
         * The {@link JobRepository} that contains the job. Required. Cannot be null.
         *
         * @param repository The repository.
         * @return This instance of Builder.
         */
        public Builder<T> setRepository(final JobRepository<T> repository) {
            _repository = repository;
            return this;
        }

        /**
         * The id of the job in the {@code repository}. Required. Cannot be null.
         *
         * @param id The id.
         * @return This instance of Builder.
         */
        public Builder<T> setId(final UUID id) {
            _id = id;
            return this;
        }

        /**
         * The {@link Organization} that the job belongs to in the {@code repository}. Required. Cannot be null.
         *
         * @param organization The organization.
         * @return This instance of Builder.
         */
        public Builder<T> setOrganization(final Organization organization) {
            _organization = organization;
            return this;
        }
    }
}
