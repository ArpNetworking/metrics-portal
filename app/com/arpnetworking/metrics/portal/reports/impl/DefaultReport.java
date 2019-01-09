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

package com.arpnetworking.metrics.portal.reports.impl;

import akka.actor.ActorRef;
import com.arpnetworking.metrics.portal.reports.Report;
import com.arpnetworking.metrics.portal.scheduling.Schedule;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link Report}.
 */
public class DefaultReport implements Report {
    /**
     * Creates a new {@code DefaultReport} with the given id and schedule.
     * @param id The id of the report.
     * @param schedule The schedule for this report.
     */
    public DefaultReport(final UUID id, final Schedule schedule) {
        this._id = id;
        this._schedule = schedule;
    }

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public Schedule getSchedule() {
        return _schedule;
    }

    @Override
    public Optional<Instant> getLastRun() {
        return Optional.empty();
    }

    @Override
    public CompletionStage<Report.Result> execute(final ActorRef scheduler, final Instant scheduled) {
        return null;
    }

    private final UUID _id;
    private final Schedule _schedule;
}
