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
package models.internal.scheduling;

import com.arpnetworking.commons.builder.OvalBuilder;
import edu.umd.cs.findbugs.annotations.Nullable;
import net.sf.oval.constraint.NotNull;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/**
 * Internal model for the execution state of a given {@link Job}.
 * <p>
 * An execution is uniquely identified by its Job ID and scheduled time, since a single job will likely have
 * many executions over its lifetime.
 *
 * @param <T> The type of result produced by the corresponding {@code Job}.
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public abstract class JobExecution<T> {
    private final UUID _jobId;
    private final Instant _scheduled;

    private <B extends Builder<B, T, U>, U extends JobExecution<T>> JobExecution(final Builder<B, T, U> builder) {
        _jobId = builder._jobId;
        _scheduled = builder._scheduled;
    }

    /**
     * Get the UUID of the parent job for this execution.
     *
     * @return the job ID
     */
    public UUID getJobId() {
        return _jobId;
    }

    /**
     * Get the scheduled time for this execution.
     *
     * @return the scheduled time.
     */
    public Instant getScheduled() {
        return _scheduled;
    }

    abstract <U> U accept(Visitor<T, U> visitor);

    /**
     * A visitor for {@code JobExecution}s.
     * <p>
     * This provides a type-safe way to uniformly access information about the particular state of a JobExecution.
     *
     * @param <T> The result produced by this {@code JobExecution}.
     * @param <U> The result produced by this visitor.
     */
    public interface Visitor<T, U> {
        /**
         * Convenience wrapper around {@code state.accept(visitor)}.
         *
         * @param state The state to visit.
         * @return The result produced by this visitor.
         */
        default U visit(final JobExecution<T> state) {
            return state.accept(this);
        }

        /**
         * Visit a started state.
         *
         * @param state The state to visit.
         * @return The result produced by this visitor.
         */
        U visit(JobExecution.Started<T> state);

        /**
         * Visit a success state.
         *
         * @param state The state to visit.
         * @return The result produced by this visitor.
         */
        U visit(JobExecution.Success<T> state);

        /**
         * Visit a failure state.
         *
         * @param state The state to visit.
         * @return The result produced by this visitor.
         */
        U visit(JobExecution.Failure<T> state);
    }

    /**
     * Base builder class for all subclasses of {@code JobExecution}.
     *
     * @param <B> The concrete type of this builder.
     * @param <T> The result produced by the output JobExecution.
     * @param <U> The output of this builder.
     */
    protected abstract static class Builder<B extends Builder<B, T, U>, T, U extends JobExecution<T>> extends OvalBuilder<U> {
        @NotNull
        private UUID _jobId;
        @NotNull
        private Instant _scheduled;

        /**
         * Forwarding Constructor.
         *
         * @param targetConstructor The constructor for a {@code U}.
         */
        protected Builder(final Function<B, U> targetConstructor) {
            super(targetConstructor);
        }

        /**
         * Set the jobId for this execution.
         *
         * @param jobId The job Id.
         * @return This instance of builder.
         */
        public B setJobId(@Nullable final UUID jobId) {
            _jobId = jobId;
            return self();
        }

        /**
         * Set the scheduled time this execution.
         *
         * @param scheduled The instant this execution is scheduled for.
         * @return This instance of builder.
         */
        public B setScheduled(@Nullable final Instant scheduled) {
            _scheduled = scheduled;
            return self();
        }

        /**
         * Wrapper for propagating the concrete type of a derived builder instance.
         *
         * @return This instance of builder.
         */
        protected abstract B self();
    }

    /**
     * An execution that has been started but not yet completed.
     *
     * @param <T> The result type of this execution.
     */
    public static final class Started<T> extends JobExecution<T> {
        private final Instant _startedAt;

        private Started(final Builder<T> builder) {
            super(builder);
            _startedAt = builder._startedAt;
        }

        /**
         * Get the instant when this execution was started.
         *
         * @return The instant this execution was started.
         */
        public Instant getStartedAt() {
            return _startedAt;
        }

        @Override
        public <U> U accept(final Visitor<T, U> visitor) {
            return visitor.visit(this);
        }

        /**
         * A builder for instances of {@link Started}.
         *
         * @param <T> The result type of this {@code JobExecution}.
         */
        public static class Builder<T> extends JobExecution.Builder<Builder<T>, T, Started<T>> {
            @NotNull
            private Instant _startedAt;

            /**
             * A builder for instances of {@link Started}.
             */
            public Builder() {
                super(Started::new);
            }

            /**
             * Set the startedAt for this execution.
             *
             * @param startedAt The instant this job was started.
             * @return This builder instance.
             */
            public Builder<T> setStartedAt(@Nullable final Instant startedAt) {
                _startedAt = startedAt;
                return self();
            }

            @Override
            protected Builder<T> self() {
                return this;
            }
        }
    }

    /**
     * An execution that completed with a successful output result.
     *
     * @param <T> The result type of this execution.
     */
    public static final class Success<T> extends JobExecution<T> {
        private final Instant _startedAt;
        private final Instant _completedAt;
        private final T _result;

        private Success(final Builder<T> builder) {
            super(builder);
            _completedAt = builder._completedAt;
            _startedAt = builder._startedAt;
            _result = builder._result;
        }

        /**
         * Get the instant when this execution was started.
         *
         * @return The instant this execution was started.
         */
        public Instant getStartedAt() {
            return _startedAt;
        }

        /**
         * Get the instant when this execution was completed.
         *
         * @return The instant this execution was completed.
         */
        public Instant getCompletedAt() {
            return _completedAt;
        }

        public T getResult() {
            return _result;
        }

        @Override
        public <U> U accept(final Visitor<T, U> visitor) {
            return visitor.visit(this);
        }

        /**
         * A builder for instances of {@link Success}.
         *
         * @param <T> The result type of this {@code JobExecution}.
         */
        public static class Builder<T> extends JobExecution.Builder<Builder<T>, T, Success<T>> {
            @NotNull
            private Instant _startedAt;
            @NotNull
            private Instant _completedAt;
            @NotNull
            private T _result;

            /**
             * A builder for instances of {@link Success}.
             */
            public Builder() {
                super(Success::new);
            }

            @Override
            protected Builder<T> self() {
                return this;
            }

            /**
             * Set the startedAt for this execution.
             *
             * @param startedAt The instant this job was started.
             * @return This builder instance.
             */
            public Builder<T> setStartedAt(@Nullable final Instant startedAt) {
                _startedAt = startedAt;
                return this;
            }

            /**
             * Set the completedAt for this execution.
             *
             * @param completedAt The instant this job was completed.
             * @return This builder instance.
             */
            public Builder<T> setCompletedAt(@Nullable final Instant completedAt) {
                _completedAt = completedAt;
                return this;
            }

            /**
             * Set the result for this execution.
             *
             * @param result The result that completed this job.
             * @return This builder instance.
             */
            public Builder<T> setResult(@Nullable final T result) {
                _result = result;
                return this;
            }
        }
    }

    /**
     * An execution that completed with an error result.
     *
     * @param <T> The result type of this execution.
     */
    public static final class Failure<T> extends JobExecution<T> {
        private final Instant _startedAt;
        private final Instant _completedAt;
        private final Throwable _result;

        private Failure(final Builder<T> builder) {
            super(builder);
            _completedAt = builder._completedAt;
            _startedAt = builder._startedAt;
            _result = builder._result;
        }

        /**
         * Get the instant when this execution was started.
         *
         * @return The instant this execution was started.
         */
        public Instant getStartedAt() {
            return _startedAt;
        }

        /**
         * Get the instant when this execution was completed.
         *
         * @return The instant this execution was completed.
         */
        public Instant getCompletedAt() {
            return _completedAt;
        }

        /**
         * Get the error that completed this execution.
         *
         * @return The error.
         */
        public Throwable getError() {
            return _result;
        }

        @Override
        public <U> U accept(final Visitor<T, U> visitor) {
            return visitor.visit(this);
        }

        /**
         * A builder for instances of {@link Failure}.
         *
         * @param <T> The result type of this {@code JobExecution}.
         */
        public static class Builder<T> extends JobExecution.Builder<Builder<T>, T, Failure<T>> {
            @NotNull
            private Instant _startedAt;
            @NotNull
            private Instant _completedAt;
            @NotNull
            private Throwable _result;

            /**
             * A builder for instances of {@link Failure}.
             */
            public Builder() {
                super(Failure::new);
            }

            /**
             * Set the startedAt for this execution.
             *
             * @param startedAt The instant this job was started.
             * @return This builder instance.
             */
            public Builder<T> setStartedAt(@Nullable final Instant startedAt) {
                _startedAt = startedAt;
                return this;
            }

            /**
             * Set the completedAt for this execution.
             *
             * @param completedAt The instant this job was completed.
             * @return This builder instance.
             */
            public Builder<T> setCompletedAt(@Nullable final Instant completedAt) {
                _completedAt = completedAt;
                return this;
            }

            /**
             * Set the error for this execution.
             *
             * @param result The error that completed this job.
             * @return This builder instance.
             */
            public Builder<T> setError(@Nullable final Throwable result) {
                _result = result;
                return this;
            }

            @Override
            public Builder<T> self() {
                return this;
            }
        }

    }
}
