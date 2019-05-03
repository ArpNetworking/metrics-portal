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
package models.view.impl;

import models.view.scheduling.Schedule;

import java.time.Instant;

/**
 * Schedule that should be executed exactly once.
 *
 * Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class OneOffSchedule implements Schedule {

    public Instant getRunAtAndAfter() {
        return _runAtAndAfter;
    }

    public void setRunAtAndAfter(final Instant runAtAndAfter) {
        _runAtAndAfter = runAtAndAfter;
    }

    @Override
    public com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule toInternal() {
        return new com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule.Builder()
                .setRunAtAndAfter(_runAtAndAfter)
                .build();
    }

    /**
     * Create a {@code OneOffSchedule} from its internal representation.
     *
     * @param schedule The internal model.
     * @return The view model.
     */
    public static OneOffSchedule fromInternal(final com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule schedule) {
        final OneOffSchedule viewSchedule = new OneOffSchedule();
        viewSchedule.setRunAtAndAfter(schedule.getRunAtAndAfter());
        return viewSchedule;
    }

    private Instant _runAtAndAfter;
}
