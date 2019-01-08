package com.arpnetworking.metrics.portal.scheduling.impl;

import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class NeverScheduleTest {

    private static final ZonedDateTime t0 = ZonedDateTime.parse("2018-01-01T00:00:00Z");

    @Test
    public void testNextRun() {
        // Two interesting cases: lastRun=null, lastRun!=null
        assertEquals(Optional.empty(), NeverSchedule.getInstance().nextRun(Optional.empty()));
        assertEquals(Optional.empty(), NeverSchedule.getInstance().nextRun(Optional.of(ZonedDateTime.parse("2019-01-01T00:00:00Z"))));
    }

}
