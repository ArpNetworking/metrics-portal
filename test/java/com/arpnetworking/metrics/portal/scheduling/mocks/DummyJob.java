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
package com.arpnetworking.metrics.portal.scheduling.mocks;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.google.inject.Injector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.scheduling.Job;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Simple {@link Job} implementation for use in tests.
 *
 * @param <T> type of job
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class DummyJob<T> implements Job<T> {
    private final UUID _uuid;
    private final Schedule _schedule;
    private final Duration _timeout;
    private final Optional<T> _result;
    private final Optional<Throwable> _error;
    private final CompletionStage<?> _blocker;

    private DummyJob(final Builder<T> builder) {
        _uuid = builder._uuid;
        _schedule = builder._schedule;
        _timeout = builder._timeout;
        _result = builder._result;
        _error = builder._error;
        _blocker = builder._blocker;
    }

    @Override
    public UUID getId() {
        return _uuid;
    }

    @Override
    public Optional<String> getETag() {
        return Optional.of(_uuid.toString());
    }

    @Override
    public Schedule getSchedule() {
        return _schedule;
    }

    @Override
    public Duration getTimeout() {
        return _timeout;
    }

    public Optional<T> getResult() {
        return _result;
    }

    public Optional<Throwable> getError() {
        return _error;
    }

    @Override
    public CompletionStage<T> execute(final Injector injector, final Instant scheduled) {
        return _blocker.thenCompose(whatever -> {
            final CompletableFuture<T> future = new CompletableFuture<>();
            if (_result.isPresent()) {
                future.complete(_result.get());
            } else {
                future.completeExceptionally(_error.get());
            }
            return future;
        });
    }


    /**
     * Implementation of builder pattern for {@link DummyJob}.
     *
     * @param <T> The type of result of the job run by the recipient actor.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public static final class Builder<T> extends OvalBuilder<DummyJob<T>> {
        @NotNull
        private UUID _uuid = UUID.randomUUID();
        @NotNull
        private Schedule _schedule;
        @NotNull
        private Duration _timeout;
        @ValidateWithMethod(methodName = "validateResultXorError", parameterType = Object.class)
        private Optional<T> _result = Optional.empty();
        private Optional<Throwable> _error = Optional.empty();
        private CompletionStage<?> _blocker = CompletableFuture.completedFuture(null);

        /**
         * Public constructor.
         */
        public Builder() {
            super(DummyJob<T>::new);
        }

        /**
         * Sets Id.
         *
         * @param uuid id to set
         * @return this builder
         */
        public Builder<T> setId(final UUID uuid) {
            _uuid = uuid;
            return this;
        }

        /**
         * Sets schedule.
         *
         * @param schedule schedule to set
         * @return this builder
         */
        public Builder<T> setSchedule(final Schedule schedule) {
            _schedule = schedule;
            return this;
        }

        /**
         * Sets timeout.
         *
         * @param timeout timeout to set
         * @return this builder
         */
        public Builder<T> setTimeout(final Duration timeout) {
            _timeout = timeout;
            return this;
        }

        /**
         * Sets a one off schedule.
         *
         * @param runAt time to run
         * @return this builder
         */
        public Builder<T> setOneOffSchedule(final Instant runAt) {
            _schedule = new OneOffSchedule.Builder().setRunAtAndAfter(runAt).build();
            return this;
        }

        /**
         * Sets the result.
         *
         * @param result the result
         * @return this builder
         */
        public Builder<T> setResult(final T result) {
            _result = Optional.of(result);
            _error = Optional.empty();
            return this;
        }

        /**
         * Sets the error.
         *
         * @param error the error
         * @return this builder
         */
        public Builder<T> setError(final Throwable error) {
            _result = Optional.empty();
            _error = Optional.of(error);
            return this;
        }

        /**
         * Sets the blocking completionstage.
         *
         * @param blocker the blocker
         * @return this builder
         */
        public Builder<T> setBlocker(final CompletionStage<?> blocker) {
            _blocker = blocker;
            return this;
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by @ValidateWithMethod")
        private boolean validateResultXorError(final Object result) {
            return _result.isPresent() ^ _error.isPresent();
        }
    }
}
