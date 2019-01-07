package com.arpnetworking.metrics.portal.scheduling.impl;

import models.internal.scheduling.Schedule;
import org.junit.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class OneOffScheduleTest {

    private static final ZonedDateTime t0 = ZonedDateTime.parse("2018-01-01T00:00:00Z");

    @Test
    public void testNextRun() {
        final Schedule schedule = new OneOffSchedule.Builder()
                .setWhenRun(ZonedDateTime.parse("2019-01-01T00:00:00Z"))
                .setRunAtAndAfter(ZonedDateTime.parse("2019-01-01T00:00:00Z"))
                .build();

        // typical progression, from lastRun=null to lastRun>runUntil
        assertEquals(
                Optional.of(ZonedDateTime.parse("2019-01-01T00:00:00Z")),
                schedule.nextRun(Optional.empty())
        );
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-01T00:00:00Z")))
        );

        // lastRun < runAfter: next run should be null
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("2018-12-20T12:34:56Z")))
        );

        // runAfter < lastRun < runUntil: next run should be null
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("2019-01-02T12:34:56Z")))
        );

        // lastRun > runUntil: next run should be null
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(ZonedDateTime.parse("9999-01-01T00:00:00Z")))
        );
    }

    @Test(expected = net.sf.oval.exception.ConstraintsViolatedException.class)
    public void testBuilderRejectsRunBeforeRunAfter() {
        new OneOffSchedule.Builder()
                .setWhenRun(t0)
                .setRunAtAndAfter(t0.plus(Duration.ofSeconds(1)))
                .build();
    }

    @Test(expected = net.sf.oval.exception.ConstraintsViolatedException.class)
    public void testBuilderRejectsRunAfterRunUntil() {
        new OneOffSchedule.Builder()
                .setWhenRun(t0)
                .setRunAtAndAfter(t0)
                .setRunUntil(t0.minus(Duration.ofSeconds(1)))
                .build();
    }

}
