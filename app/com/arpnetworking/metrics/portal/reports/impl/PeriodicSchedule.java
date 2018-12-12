package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Schedule;

import java.time.Instant;
import java.time.temporal.TemporalAmount;

public class PeriodicSchedule implements Schedule {

    private final TemporalAmount period;

    public PeriodicSchedule(TemporalAmount period) {
        this.period = period;
    }

    @Override
    public Instant nextRun(Instant lastRun) {
        return lastRun.plus(period);
    }

    public TemporalAmount getPeriod() {
        return period;
    }
}
