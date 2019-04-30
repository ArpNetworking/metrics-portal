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
package models.view.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import models.view.scheduling.Periodicity;
import models.view.scheduling.Schedule;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Schedule for a job that repeats periodically.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PeriodicSchedule implements Schedule {

    public Periodicity getPeriod() {
        return _period;
    }

    public void setPeriod(final Periodicity period) {
        _period = period;
    }

    public ZoneId getZone() {
        return _zone;
    }

    public void setZone(final ZoneId zone) {
        _zone = zone;
    }

    public Duration getOffset() {
        return _offset;
    }

    public void setOffset(final Duration offset) {
        _offset = offset;
    }

    @Override
    public com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule toInternal() {
        return new com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule.Builder()
                .setRunAtAndAfter(_runAtAndAfter)
                .setRunUntil(_runUntil)
                .setPeriod(_period.toInternal())
                .setOffset(_offset)
                .setZone(_zone)
                .build();
    }

    public static PeriodicSchedule fromInternal(final com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule schedule) {
        final PeriodicSchedule viewSchedule = new PeriodicSchedule();
        viewSchedule._runAtAndAfter = schedule.getRunAtAndAfter();
        viewSchedule._runUntil = schedule.getRunUntil().orElse(null);
        final Periodicity period = Periodicity.fromValue(schedule.getPeriod());
        viewSchedule.setPeriod(period);
        viewSchedule.setOffset(schedule.getOffset());
        viewSchedule.setZone(schedule.getZone());
        return viewSchedule;
    }

    @JsonProperty("runAtAndAfter")
    private Instant _runAtAndAfter;
    @JsonProperty("runUntil")
    private Instant _runUntil;
    @JsonProperty("period")
    private Periodicity _period;
    @JsonProperty("offset")
    private Duration _offset;
    @JsonProperty("zone")
    private ZoneId _zone;

}
