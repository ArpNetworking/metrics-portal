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
import net.sf.oval.exception.ConstraintsViolatedException;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link UnboundedPeriodicSchedule}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class UnboundedPeriodicScheduleTest {
    private static final Instant CLOCK_START = Instant.parse("2020-07-30T10:00:00Z");
    private static final Instant FIRST_RUN = Instant.parse("2020-07-30T10:30:00Z");

    @Test(expected = ConstraintsViolatedException.class)
    public void testItDisallowsZeroPeriod() {
        new UnboundedPeriodicSchedule.Builder()
                .setPeriodCount(0)
                .setPeriod(ChronoUnit.MINUTES)
                .build();
    }

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
        assertThat(nextScheduled.get(), equalTo(FIRST_RUN));

        nextScheduled = schedule.nextRun(Optional.of(CLOCK_START.minus(1, ChronoUnit.DAYS)));
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(FIRST_RUN));

        nextScheduled = schedule.nextRun(Optional.of(Instant.EPOCH));
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(FIRST_RUN));
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
        assertThat(nextScheduled.get(), equalTo(FIRST_RUN.plus(fullPeriod)));

        clock.tick();
        nextScheduled = schedule.nextRun(Optional.empty());
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), equalTo(FIRST_RUN.plus(fullPeriod.multipliedBy(2))));
    }

    @Test
    public void testNoFixedPoints() {
        // Now being exactly on an interval should still return a time in the future.
        final Clock clock = Clock.fixed(CLOCK_START, ZoneOffset.UTC);
        final Schedule schedule =
                new UnboundedPeriodicSchedule.Builder()
                        .setClock(clock)
                        .setPeriodCount(30)
                        .setPeriod(ChronoUnit.MINUTES)
                        .build();

        final Optional<Instant> nextScheduled = schedule.nextRun(Optional.empty());
        assertThat(nextScheduled.isPresent(), is(true));
        assertThat(nextScheduled.get(), greaterThan(CLOCK_START));
    }

    @Test
    public void testReportingOverrun() {
        final Instant start = Instant.parse("2020-08-12T10:15:00Z");

        @SuppressWarnings("unchecked")
        final Consumer<Long> overrunReporter = (Consumer<Long>) Mockito.mock(Consumer.class);
        final Clock clock = Clock.fixed(start, ZoneOffset.UTC);
        final Schedule schedule =
                new UnboundedPeriodicSchedule.Builder()
                        .setClock(clock)
                        .setPeriodCount(30)
                        .setPeriod(ChronoUnit.MINUTES)
                        .setOverrunReporter(overrunReporter)
                        .build();

        // nothing run, nothing to miss.
        schedule.nextRun(Optional.empty());
        verify(overrunReporter, never()).accept(any());

        // nothing missed.
        schedule.nextRun(Optional.of(Instant.parse("2020-08-12T10:05:00Z")));
        verify(overrunReporter, never()).accept(any());

        // We're possibly missing the execution at 10:00:00Z
        schedule.nextRun(Optional.of(Instant.parse("2020-08-12T09:55:00Z")));
        verify(overrunReporter, times(1)).accept(eq(1L));

        // We're possibly missing the executions at 09:30:00Z, 10:00:00Z
        schedule.nextRun(Optional.of(Instant.parse("2020-08-12T09:25:00Z")));
        verify(overrunReporter, times(1)).accept(eq(1L));
    }
}
