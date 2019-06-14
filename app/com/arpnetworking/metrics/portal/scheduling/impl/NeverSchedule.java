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

import java.time.Instant;
import java.util.Optional;

/**
 * Schedule that should never fire.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class NeverSchedule extends BaseSchedule {

    /**
     * The only instance of {@code NeverSchedule}, since they're all identical.
     */
    private static final NeverSchedule INSTANCE = new NeverSchedule(Builder.INSTANCE);

    private NeverSchedule(final Builder builder) {
        super(builder);
    }

    public static NeverSchedule getInstance() {
        return INSTANCE;
    }

    @Override
    public String toString() {
        return "NeverSchedule{}";
    }

    @Override
    protected Optional<Instant> unboundedNextRun(final Optional<Instant> lastRun) {
        return Optional.empty();
    }

    @Override
    public <T> T accept(final Visitor<T> visitor) {
        return visitor.visitNever(this);
    }

    /**
     * Implementation of builder pattern for {@link NeverSchedule}.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    private static final class Builder extends BaseSchedule.Builder<Builder, NeverSchedule> {

        private static final Builder INSTANCE = new Builder();
        private Builder() {
            super(NeverSchedule::new);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
