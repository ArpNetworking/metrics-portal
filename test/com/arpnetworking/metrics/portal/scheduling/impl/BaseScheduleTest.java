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

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link BaseSchedule}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class BaseScheduleTest {

    private static final Instant t0 = Instant.parse("2018-01-01T00:00:00Z");

    private static final class MinimalSchedule extends BaseSchedule {

        public static final MinimalSchedule INSTANCE = new Builder().build();

        private MinimalSchedule(final Builder builder) {
            super(builder);
        }

        @Override
        protected Optional<Instant> unboundedNextRun(Optional<Instant> lastRun) {
            return Optional.empty();
        }

        /**
         * Implementation of builder pattern for {@link MinimalSchedule}.
         *
         * @author Spencer Pearson (spencerpearson at dropbox dot com)
         */
        private static final class Builder extends BaseSchedule.Builder<Builder, MinimalSchedule> {
            private Builder() {
                super(MinimalSchedule::new);
            }

            @Override
            protected Builder self() {
                return this;
            }
        }
    }

    @Test(expected = net.sf.oval.exception.ConstraintsViolatedException.class)
    public void testBuilderRejectsRunAfterAfterRunUntil() {
        new MinimalSchedule.Builder()
                .setRunAtAndAfter(t0)
                .setRunUntil(t0.minus(Duration.ofSeconds(1)))
                .build();
    }

}
