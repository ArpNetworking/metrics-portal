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

package models.internal.impl;

import akka.actor.ActorRef;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import models.internal.reports.RecipientGroup;
import models.internal.reports.Report;
import models.internal.reports.ReportSource;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link Report}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DefaultReport implements Report {
    private DefaultReport(final Builder builder) {
        _id = builder._id;
        _name = builder._name;
        _schedule = builder._schedule;
        _source = builder._source;
        _groups = builder._groups;
    }

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public Schedule getSchedule() {
        return _schedule;
    }

    @Override
    public ReportSource getSource() {
        return _source;
    }

    @Override
    public Collection<RecipientGroup> getRecipientGroups() {
        return Collections.unmodifiableCollection(_groups);
    }

    @Override
    public Optional<Instant> getLastRun() {
        return Optional.empty();
    }

    @Override
    public CompletionStage<Report.Result> execute(final ActorRef scheduler, final Instant scheduled) {
        return CompletableFuture.completedFuture(null);
    }

    private final UUID _id;
    private final String _name;
    private final Schedule _schedule;
    private final ReportSource _source;
    private final Collection<RecipientGroup> _groups;

    /**
     * Builder implementation that constructs {@code DefaultReport}.
     */
    public static final class Builder extends OvalBuilder<DefaultReport> {
        /**
         * Public Constructor.
         */
        public Builder() {
            super(DefaultReport::new);
            _schedule = NeverSchedule.getInstance();
            _groups = Collections.emptySet();
        }

        /**
         * Set the report id. Required. Cannot be null.
         * @param id The report id.
         * @return This instance of {@code Builder}.
         */
        public Builder setId(final UUID id) {
            _id = id;
            return this;
        }

        /**
         * Set the report name. Required. Cannot be null or empty.
         * @param name The report name.
         * @return This instance of {@code Builder}.
         */
        public Builder setName(final String name) {
            _name = name;
            return this;
        }

        /**
         * Set the report schedule. Required. Cannot be null.
         * @param schedule The report schedule.
         * @return This instance of {@code Builder}.
         */
        public Builder setSchedule(final Schedule schedule) {
            _schedule = schedule;
            return this;
        }

        /**
         * Set the report source. Required. Cannot be null.
         * @param source The report source.
         * @return This instance of {@code Builder}.
         */
        public Builder setReportSource(final ReportSource source) {
            _source = source;
            return this;
        }

        /**
         * Set the report recipients. Required. Cannot be null.
         * @param groups The report recipient groups.
         * @return This instance of {@code Builder}.
         */
        public Builder setRecipientGroups(final Collection<RecipientGroup> groups) {
            _groups = groups;
            return this;
        }

        @NotNull
        private UUID _id;
        @NotNull
        @NotEmpty
        private String _name;
        @NotNull
        private ReportSource _source;
        @NotNull
        private Collection<RecipientGroup> _groups;
        @NotNull
        private Schedule _schedule;
    }
}
