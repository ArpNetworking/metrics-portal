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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.scheduling.BaseSchedule;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Schedule that should be executed exactly once.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class OneOffSchedule extends BaseSchedule {

    private final ZonedDateTime _whenRun;

    private OneOffSchedule(final Builder builder) {
        super(builder);
        _whenRun = builder._whenRun;
    }

    public ZonedDateTime getWhenRun() {
        return _whenRun;
    }

    @Override
    protected Optional<ZonedDateTime> unboundedNextRun(final Optional<ZonedDateTime> lastRun) {
        if (lastRun.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(_whenRun);
    }

    /**
     * Implementation of builder pattern for {@link OneOffSchedule}.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder extends BaseSchedule.Builder<Builder, OneOffSchedule> {

        @NotNull
        @ValidateWithMethod(methodName = "validateWhenRun", parameterType = ZonedDateTime.class)
        private ZonedDateTime _whenRun;

        /**
         * Public constructor.
         */
        public Builder() {
            super(OneOffSchedule::new);
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * The time when the schedule should fire. Required. Cannot be null.
         *
         * @param whenRun The time.
         * @return This instance of Builder.
         */
        public Builder setWhenRun(final ZonedDateTime whenRun) {
            _whenRun = whenRun;
            return this;
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by @ValidateWithMethod")
        private boolean validateWhenRun(final ZonedDateTime whenRun) {
            return !whenRun.isBefore(_runAtAndAfter) && (!_runUntil.isPresent() || !whenRun.isAfter(_runUntil.get()));
        }
    }
}
