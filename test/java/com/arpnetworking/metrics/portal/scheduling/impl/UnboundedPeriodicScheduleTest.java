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

package com.arpnetworking.metrics.portal.scheduling.impl;

import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Unit tests for {@link UnboundedPeriodicSchedule}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class UnboundedPeriodicScheduleTest {
    private static final Instant CLOCK_START = Instant.parse("2020-07-30T10:00:00Z");

    @Test
    public void testLastRunEmptyOrInThePast() {
        final Clock clock = Clock.fixed(CLOCK_START, ZoneOffset.UTC);
        final Schedule schedule =
                new UnboundedPeriodicSchedule.Builder()
                    .setClock(clock)
                    .setPeriodCount(30)
                    .setPeriod(ChronoUnit.MINUTES)
                    .build();

        Optional<Instant> nextScheduled = schedule.nextRun(Optional.empty());
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(CLOCK_START));

        nextScheduled = schedule.nextRun(Optional.of(CLOCK_START.minus(1, ChronoUnit.DAYS)));
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(CLOCK_START));

        nextScheduled = schedule.nextRun(Optional.of(Instant.EPOCH));
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(CLOCK_START));
    }

    @Test
    public void testLaggingJobIgnoresThePast() {
        final Duration fullPeriod = Duration.of(30, ChronoUnit.MINUTES);
        final ManualClock clock = new ManualClock(CLOCK_START, fullPeriod, ZoneOffset.UTC);
        final Schedule schedule =
                new UnboundedPeriodicSchedule.Builder()
                        .setClock(clock)
                        .setPeriodCount(30)
                        .setPeriod(ChronoUnit.MINUTES)
                        .build();

        clock.tick();
        Optional<Instant> nextScheduled = schedule.nextRun(Optional.of(CLOCK_START));
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(CLOCK_START.plus(fullPeriod)));

        clock.tick();
        nextScheduled = schedule.nextRun(Optional.empty());
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(CLOCK_START.plus(fullPeriod.multipliedBy(2))));
    }

    @Test
    public void testWhenLastRunIsAlreadyAfterNextScheduled() {
        final Clock clock = Clock.fixed(CLOCK_START, ZoneOffset.UTC);
        final Schedule schedule =
                new UnboundedPeriodicSchedule.Builder()
                        .setClock(clock)
                        .setPeriodCount(30)
                        .setPeriod(ChronoUnit.MINUTES)
                        .build();
        final Duration fullPeriod = Duration.of(30, ChronoUnit.MINUTES);

        Optional<Instant> nextScheduled = schedule.nextRun(Optional.of(CLOCK_START));
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(CLOCK_START.plus(fullPeriod)));

        nextScheduled = schedule.nextRun(Optional.of(CLOCK_START.plus(1, ChronoUnit.SECONDS)));
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(CLOCK_START.plus(fullPeriod)));

        final Duration fivePeriods = fullPeriod.multipliedBy(5);
        nextScheduled = schedule.nextRun(Optional.of(CLOCK_START.plus(fivePeriods)));
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(CLOCK_START.plus(fivePeriods).plus(fullPeriod)));
    }
}
