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

import com.google.common.base.MoreObjects;

import java.time.Instant;
import java.util.Optional;

/**
 * BaseScheduleViewModel that should be executed exactly once.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class OneOffSchedule extends BaseSchedule {

    private OneOffSchedule(final Builder builder) {
        super(builder);
    }

    public Instant getWhenRun() {
        return getRunAtAndAfter();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("instant", getRunAtAndAfter())
                .toString();
    }

    @Override
    protected Optional<Instant> unboundedNextRun(final Optional<Instant> lastRun) {
        if (lastRun.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(getWhenRun());
    }

    /**
     * Implementation of builder pattern for {@link OneOffSchedule}.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder extends BaseSchedule.Builder<Builder, OneOffSchedule> {
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
    }
}
