/*
 * Copyright 2019 Dropbox, Inc.
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
package models.view.scheduling;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.MoreObjects;
import models.view.impl.NeverSchedule;
import models.view.impl.OneOffSchedule;
import models.view.impl.PeriodicSchedule;

import java.time.Instant;
import java.util.Objects;

/**
 * View model of {@link com.arpnetworking.metrics.portal.scheduling.Schedule}. Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PeriodicSchedule.class, name = "PERIODIC"),
        @JsonSubTypes.Type(value = OneOffSchedule.class, name = "ONE_OFF"),
        @JsonSubTypes.Type(value = NeverSchedule.class, name = "NEVER"),
})
public abstract class Schedule {

    public Instant getRunAtAndAfter() {
        return _runAtAndAfter;
    }

    public void setRunAtAndAfter(final Instant runAtAndAfter) {
        this._runAtAndAfter = runAtAndAfter;
    }

    /**
     * Convert to an internal model {@link com.arpnetworking.metrics.portal.scheduling.Schedule}.
     *
     * @return The internal model.
     */
    public abstract com.arpnetworking.metrics.portal.scheduling.Schedule toInternal();

    /**
     * Convert from an internal model {@link com.arpnetworking.metrics.portal.scheduling.Schedule}.
     *
     * @param schedule The internal schedule model
     * @return The view model.
     */
    public static Schedule fromInternal(final com.arpnetworking.metrics.portal.scheduling.Schedule schedule) {
        if (schedule instanceof com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule) {
            return OneOffSchedule.fromInternal((com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule) schedule);
        } else if (schedule instanceof com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule) {
            return PeriodicSchedule.fromInternal((com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule) schedule);
        } else if (schedule instanceof com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule) {
            return NeverSchedule.fromInternal((com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule) schedule);
        } else {
            throw new IllegalArgumentException("Cannot convert class " + schedule.getClass() + " to a view model.");
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Schedule)) {
            return false;
        }
        final Schedule schedule = (Schedule) o;
        return _runAtAndAfter.equals(schedule._runAtAndAfter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_runAtAndAfter);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_runAtAndAfter", _runAtAndAfter)
                .toString();
    }

    private Instant _runAtAndAfter;
}
