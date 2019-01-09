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

import com.arpnetworking.metrics.portal.scheduling.Schedule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link PeriodicSchedule}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PeriodicScheduleTest {

    private static final Instant t0 = Instant.parse("2019-01-01T00:00:00Z");

    @Test
    public void testNextRunWithAlignedBounds() {
        final Schedule schedule = new PeriodicSchedule.Builder()
                .setZone(ZoneId.of("+00:00"))
                .setPeriod(ChronoUnit.DAYS)
                .setOffset(Duration.ZERO)
                .setRunAtAndAfter(Instant.parse("2019-01-01T00:00:00Z"))
                .setRunUntil(Instant.parse("2019-01-03T00:00:00Z"))
                .build();

        // typical progression, from lastRun=null to lastRun>runUntil
        assertEquals(
                Optional.of(Instant.parse("2019-01-01T00:00:00Z")),
                schedule.nextRun(Optional.empty()));
        assertEquals(
                Optional.of(Instant.parse("2019-01-02T00:00:00Z")),
                schedule.nextRun(Optional.of(Instant.parse("2019-01-01T00:00:00Z"))));
        assertEquals(
                Optional.of(Instant.parse("2019-01-01T00:00:00Z")),
                schedule.nextRun(Optional.of(Instant.parse("2018-12-20T12:34:56Z"))));

        // runAfter < lastRun < runUntil: should round correctly
        assertEquals(
                Optional.of(Instant.parse("2019-01-03T00:00:00Z")), schedule.nextRun(Optional.of(Instant.parse("2019-01-02T12:34:56Z"))));

        // lastRun>runUntil: should not run again
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(Instant.parse("9999-01-01T00:00:00Z"))));
    }

    @Test
    public void testNextRunWithUnalignedBounds() {
        final Schedule schedule = new PeriodicSchedule.Builder()
                .setZone(ZoneId.of("+00:00"))
                .setPeriod(ChronoUnit.DAYS)
                .setOffset(Duration.ofHours(12))
                .setRunAtAndAfter(Instant.parse("2019-01-01T06:00:00Z"))
                .setRunUntil(Instant.parse("2019-01-04T00:00:00Z"))
                .build();

        // typical progression, from lastRun=null to lastRun>runUntil
        assertEquals(
                Optional.of(Instant.parse("2019-01-02T12:00:00Z")),
                schedule.nextRun(Optional.empty()));
        assertEquals(
                Optional.of(Instant.parse("2019-01-01T12:00:00Z")),
                schedule.nextRun(Optional.of(Instant.parse("2018-12-20T12:34:56Z"))));

        // runAfter < lastRun < runUntil: should round correctly
        assertEquals(
                Optional.of(Instant.parse("2019-01-03T12:00:00Z")),
                schedule.nextRun(Optional.of(Instant.parse("2019-01-02T12:34:56Z"))));

        // lastRun>runUntil: should not run again
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(Instant.parse("9999-01-01T00:00:00Z"))));
    }

    @Test
    public void testNextRunWithPathologicallySmallBounds() {
        final Schedule schedule = new PeriodicSchedule.Builder()
                .setZone(ZoneId.of("+00:00"))
                .setPeriod(ChronoUnit.DAYS)
                .setOffset(Duration.ZERO)
                .setRunAtAndAfter(Instant.parse("2019-01-01T12:34:56Z"))
                .setRunUntil(Instant.parse("2019-01-01T12:34:57Z"))
                .build();

        assertEquals(Optional.empty(), schedule.nextRun(Optional.empty()));
        assertEquals(Optional.empty(), schedule.nextRun(Optional.of(Instant.parse("2018-01-01T00:00:00Z"))));
    }

    @Test(expected = net.sf.oval.exception.ConstraintsViolatedException.class)
    public void testBuilderOffsetMustBeSmallerThanPeriod() {
        new PeriodicSchedule.Builder()
                .setZone(ZoneId.of("+00:00"))
                .setPeriod(ChronoUnit.HOURS)
                .setOffset(Duration.ofHours(1))
                .setRunAtAndAfter(t0)
                .build();
    }

    @Test(expected = net.sf.oval.exception.ConstraintsViolatedException.class)
    public void testBuilderOffsetCannotBeNegative() {
        new PeriodicSchedule.Builder()
                .setZone(ZoneId.of("+00:00"))
                .setPeriod(ChronoUnit.HOURS)
                .setOffset(Duration.ofSeconds(-1))
                .setRunAtAndAfter(t0)
                .build();
    }

    @Test
    public void testBuilderOffsetDefaultsToZero() {
        PeriodicSchedule schedule = new PeriodicSchedule.Builder()
                .setZone(ZoneId.of("+00:00"))
                .setPeriod(ChronoUnit.HOURS)
                .setRunAtAndAfter(t0)
                .build();
        assertEquals(Optional.of(t0), schedule.nextRun(Optional.empty()));
    }

    @Test
    public void testAlignsToCorrectTimeZone() {
        final Schedule schedule = new PeriodicSchedule.Builder()
                .setZone(ZoneId.of("+12:34"))
                .setPeriod(ChronoUnit.DAYS)
                .setOffset(Duration.ofHours(12))
                .setRunAtAndAfter(ZonedDateTime.parse("2019-01-01T00:00:00+12:34").toInstant())
                .setRunUntil(ZonedDateTime.parse("2019-01-04T00:00:00+12:34").toInstant())
                .build();
        assertEquals(Optional.of(ZonedDateTime.parse("2019-01-01T12:00:00+12:34").toInstant()), schedule.nextRun(Optional.empty()));
    }

}
