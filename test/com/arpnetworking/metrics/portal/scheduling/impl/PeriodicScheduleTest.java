package com.arpnetworking.metrics.portal.scheduling.impl;

import models.internal.scheduling.Schedule;
import org.junit.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class PeriodicScheduleTest {

    private static final ZonedDateTime t0 = ZonedDateTime.parse("2019-01-01T00:00:00Z");

    @Test
    public void testNextRunWithAlignedBounds() {
        final Schedule schedule = new PeriodicSchedule.Builder()
                .setPeriod(PeriodicSchedule.Period.DAY)
                .setOffset(Duration.ZERO)
                .setRunAtAndAfter(ZonedDateTime.parse("2019-01-01T00:00:00Z"))
                .setRunUntil(     ZonedDateTime.parse("2019-01-03T00:00:00Z"))
                .build();

        // typical progression, from lastRun=null to lastRun>runUntil
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-01T00:00:00Z")), schedule.nextRun(Optional.empty()));
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-02T00:00:00Z")), schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-01T00:00:00Z"))));
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-03T00:00:00Z")), schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-02T00:00:00Z"))));
        assertEquals(
                Optional.empty(),                                         schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-03T00:00:00Z"))));

        // lastRun<runAfter: next run should be right at/after runAfter
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-01T00:00:00Z")), schedule.nextRun(Optional.of(ZonedDateTime.parse("2018-12-20T12:34:56Z"))));

        // runAfter < lastRun < runUntil: should round correctly
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-03T00:00:00Z")), schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-02T12:34:56Z"))));

        // lastRun>runUntil: should not run again
        assertEquals(
                Optional.empty(),                                         schedule.nextRun(Optional.of(ZonedDateTime.parse("9999-01-01T00:00:00Z"))));
    }

    @Test
    public void testNextRunWithUnalignedBounds() {
        final Schedule schedule = new PeriodicSchedule.Builder()
                .setPeriod(PeriodicSchedule.Period.DAY)
                .setOffset(Duration.ofHours(12))
                .setRunAtAndAfter(ZonedDateTime.parse("2019-01-01T06:00:00Z"))
                .setRunUntil(     ZonedDateTime.parse("2019-01-04T00:00:00Z"))
                .build();

        // typical progression, from lastRun=null to lastRun>runUntil
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-02T12:00:00Z")),
                schedule.nextRun(Optional.empty()));
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-03T12:00:00Z")),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-02T12:00:00Z"))));
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-03T12:00:00Z"))));

        // lastRun<runAfter: next run should be right at/after runAfter
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-01T12:00:00Z")),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("2018-12-20T12:34:56Z"))));

        // runAfter < lastRun < runUntil: should round correctly
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-03T12:00:00Z")),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-02T12:34:56Z"))));

        // lastRun>runUntil: should not run again
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("9999-01-01T00:00:00Z"))));
    }

    @Test(expected = net.sf.oval.exception.ConstraintsViolatedException.class)
    public void testBuilderOffsetMustBeSmallerThanPeriod() {
        new PeriodicSchedule.Builder()
                .setPeriod(PeriodicSchedule.Period.HOUR)
                .setOffset(Duration.ofHours(1))
                .setRunAtAndAfter(t0)
                .build();
    }

    @Test(expected = net.sf.oval.exception.ConstraintsViolatedException.class)
    public void testBuilderOffsetCannotBeNegative() {
        new PeriodicSchedule.Builder()
                .setPeriod(PeriodicSchedule.Period.HOUR)
                .setOffset(Duration.ofSeconds(-1))
                .setRunAtAndAfter(t0)
                .build();
    }

    @Test
    public void testBuilderOffsetDefaultsToZero() {
        PeriodicSchedule schedule = new PeriodicSchedule.Builder()
                .setPeriod(PeriodicSchedule.Period.HOUR)
                .setRunAtAndAfter(t0)
                .build();
        assertEquals(
                Duration.ZERO, schedule.getOffset());
    }

    @Test
    public void testPeriodFloor() {
        assertEquals(t0, PeriodicSchedule.Period.HOUR.floor(t0));
        assertEquals(t0, PeriodicSchedule.Period.HOUR.floor(t0.plus(Duration.ofSeconds(1))));
        assertEquals(t0.minus(Duration.ofHours(1)), PeriodicSchedule.Period.HOUR.floor(t0.minus(Duration.ofSeconds(1))));
    }

    @Test
    public void testPeriodCeil() {
        assertEquals(t0, PeriodicSchedule.Period.HOUR.ceil(t0));
        assertEquals(t0, PeriodicSchedule.Period.HOUR.ceil(t0.minus(Duration.ofSeconds(1))));
        assertEquals(t0.plus(Duration.ofHours(1)), PeriodicSchedule.Period.HOUR.ceil(t0.plus(Duration.ofSeconds(1))));
    }

}
