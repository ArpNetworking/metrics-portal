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
package com.arpnetworking.metrics.portal.scheduling.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Abstract base class for {@link Schedule}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public abstract class BaseSchedule implements Schedule {

    private final Instant _runAtAndAfter;
    private final Optional<Instant> _runUntil;

    /**
     * Protected constructor.
     *
     * @param builder Instance of {@link Builder}.
     */
    protected BaseSchedule(final Builder<?, ?> builder) {
        _runAtAndAfter = builder._runAtAndAfter;
        _runUntil = Optional.ofNullable(builder._runUntil);
    }

    public Instant getRunAtAndAfter() {
        return _runAtAndAfter;
    }

    public Optional<Instant> getRunUntil() {
        return _runUntil;
    }

    @Override
    public Optional<Instant> nextRun(final Optional<Instant> lastRun) {
        Optional<Instant> result = unboundedNextRun(lastRun);
        while (result.isPresent() && result.get().isBefore(_runAtAndAfter)) {
            result = unboundedNextRun(result);
        }
        if (_runUntil.isPresent() && result.isPresent() && result.get().isAfter(_runUntil.get())) {
            return Optional.empty();
        }
        return result;
    }

    /**
     * Return the next time the schedule should run, without regard for the bounds set by {@code runAtAndAfter}/{@code runUntil}.
     * ({@code nextRun} wraps this method and enforces the window mask.)
     *
     * @param lastRun The last time the job was run.
     * @return The next time to run the job.
     */
    protected abstract Optional<Instant> unboundedNextRun(Optional<Instant> lastRun);

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BaseSchedule that = (BaseSchedule) o;
        return Objects.equals(getRunAtAndAfter(), that.getRunAtAndAfter())
                && Objects.equals(getRunUntil(), that.getRunUntil());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRunAtAndAfter(), getRunUntil());
    }

    /**
     * Builder implementation for {@link BaseSchedule} subclasses.
     *
     * @param <B> type of the builder
     * @param <S> type of the object to be built
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    protected abstract static class Builder<B extends Builder<B, S>, S extends BaseSchedule> extends OvalBuilder<S> {
        @NotNull
        @ValidateWithMethod(methodName = "validateRunAtAndAfter", parameterType = Instant.class)
        protected Instant _runAtAndAfter;
        protected Instant _runUntil;

        /**
         * Protected constructor for subclasses.
         *
         * @param targetConstructor The constructor for the concrete type to be created by this builder.
         */
        protected Builder(final Function<B, S> targetConstructor) {
            super(targetConstructor);
        }

        /**
         * Called by setters to always return appropriate subclass of
         * {@link Builder}, even from setters of base class.
         *
         * @return instance with correct {@link Builder} class type.
         */
        protected abstract B self();

        /**
         * The earliest time at which the schedule should run. Required. Cannot be null.
         * <p>
         * This time cannot be {@link Instant#MIN} since it must correspond to a valid
         * date. If you need an arbitrary point in the past, you should instead use
         * {@link Instant#EPOCH}.
         *
         * @param runAtAndAfter The time.
         * @return This instance of {@link Builder}.
         */
        public B setRunAtAndAfter(final Instant runAtAndAfter) {
            _runAtAndAfter = runAtAndAfter;
            return self();
        }

        /**
         * The last time at which the schedule should ever run. Optional. Defaults to null.
         *
         * @param runUntil The time.
         * @return This instance of {@link Builder}.
         */
        public B setRunUntil(@Nullable final Instant runUntil) {
            _runUntil = runUntil;
            return self();
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by @ValidateWithMethod")
        private boolean validateRunAtAndAfter(final Instant runAtAndAfter) {
            return !runAtAndAfter.equals(Instant.MIN) && ((_runUntil == null) || !runAtAndAfter.isAfter(_runUntil));
        }
    }
}
