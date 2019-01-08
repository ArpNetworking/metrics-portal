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

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Abstract base class for {@link Schedule}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public abstract class BaseSchedule implements Schedule {
    private final ZonedDateTime _runAtAndAfter;
    private final Optional<ZonedDateTime> _runUntil;


    /**
     * Protected constructor.
     *
     * @param builder Instance of <code>Builder</code>.
     */
    protected BaseSchedule(final Builder<?, ?> builder) {
        _runAtAndAfter = builder._runAtAndAfter;
        _runUntil = builder._runUntil;
    }

    public ZonedDateTime getRunAtAndAfter() {
        return _runAtAndAfter;
    }

    public Optional<ZonedDateTime> getRunUntil() {
        return _runUntil;
    }

    @Override
    public Optional<ZonedDateTime> nextRun(final Optional<ZonedDateTime> lastRun) {
        Optional<ZonedDateTime> result = unboundedNextRun(lastRun);
        while (result.isPresent() && result.get().isBefore(_runAtAndAfter)) {
            result = unboundedNextRun(result);
        }
        if (_runUntil.isPresent() && result.isPresent() && result.get().isAfter(_runUntil.get())) {
            return Optional.empty();
        }
        return result;
    }

    /**
     * Return the next time the schedule should run, much as {@link Schedule}{@code .nextRun} does.
     * Will get restricted between {@code runAtAndAfter} and {@code runUntil} by {@code nextRun}.
     *
     * @param lastRun The last time the job was run.
     * @return The next time to run the job.
     */
    protected abstract Optional<ZonedDateTime> unboundedNextRun(Optional<ZonedDateTime> lastRun);

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
        @ValidateWithMethod(methodName = "validateRunAtAndAfter", parameterType = ZonedDateTime.class)
        protected ZonedDateTime _runAtAndAfter;
        @NotNull
        protected Optional<ZonedDateTime> _runUntil = Optional.empty();

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
         * <code>Builder</code>, even from setters of base class.
         *
         * @return instance with correct <code>Builder</code> class type.
         */
        protected abstract B self();

        /**
         * The earliest time at which the schedule should run. Required. Cannot be null.
         *
         * @param runAtAndAfter The time.
         * @return This instance of {@code Builder}.
         */
        public B setRunAtAndAfter(final ZonedDateTime runAtAndAfter) {
            _runAtAndAfter = runAtAndAfter;
            return self();
        }

        /**
         * The last time at which the schedule should ever run. Optional. Defaults to null.
         *
         * @param runUntil The time.
         * @return This instance of {@code Builder}.
         */
        public B setRunUntil(@Nullable final ZonedDateTime runUntil) {
            _runUntil = Optional.ofNullable(runUntil);
            return self();
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by @ValidateWithMethod")
        private boolean validateRunAtAndAfter(final ZonedDateTime runAtAndAfter) {
            return !_runUntil.isPresent() || !runAtAndAfter.isAfter(_runUntil.get());
        }
    }
}
