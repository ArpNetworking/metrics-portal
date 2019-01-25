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
import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link Report}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class DefaultReport implements Report {
    private DefaultReport(final Builder builder) {
        _id = builder._id;
        _name = builder._name;
        _schedule = builder._schedule;
        _source = builder._source;
        _recipients = builder._recipients;
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
    public ImmutableMap<ReportFormat, Collection<Recipient>> getRecipientsByFormat() {
        return _recipients.asMap();
    }

    @Override
    @SuppressFBWarnings(
            value = "NP_NONNULL_PARAM_VIOLATION",
            justification = "Known problem with FindBugs. See https://github.com/findbugsproject/findbugs/issues/79."
    )
    public CompletionStage<Result> execute(final ActorRef scheduler, final Instant scheduled) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String toString() {
        final List<UUID> recipientIds =
                _recipients.values()
                        .stream()
                        .map(Recipient::getId)
                        .collect(Collectors.toList());
        return MoreObjects.toStringHelper(this)
                .add("id", _id)
                .add("name", _name)
                .add("schedule", _schedule)
                .add("recipients", recipientIds)
                .add("source.id", _source.getId())
                .toString();
    }

    private final UUID _id;
    private final String _name;
    private final Schedule _schedule;
    private final ReportSource _source;

    private final ImmutableSetMultimap<ReportFormat, Recipient> _recipients;

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultReport that = (DefaultReport) o;
        return Objects.equals(_id, that._id)
                && Objects.equals(_name, that._name)
                && Objects.equals(_schedule, that._schedule)
                && Objects.equals(_source, that._source)
                && Objects.equals(_recipients, that._recipients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id, _name, _schedule, _source, _recipients);
    }

    /**
     * Builder implementation that constructs {@code DefaultReport}.
     */
    public static final class Builder extends OvalBuilder<DefaultReport> {
        /**
         * Public Constructor.
         */
        public Builder() {
            super(DefaultReport::new);
        }

        /**
         * Set the report id. Required. Cannot be null.
         *
         * @param id The report id.
         * @return This instance of {@code Builder}.
         */
        public Builder setId(final UUID id) {
            _id = id;
            return this;
        }

        /**
         * Set the report name. Required. Cannot be null or empty.
         *
         * @param name The report name.
         * @return This instance of {@code Builder}.
         */
        public Builder setName(final String name) {
            _name = name;
            return this;
        }

        /**
         * Set the report schedule. Required. Cannot be null.
         *
         * @param schedule The report schedule.
         * @return This instance of {@code Builder}.
         */
        public Builder setSchedule(final Schedule schedule) {
            _schedule = schedule;
            return this;
        }

        /**
         * Set the report source. Required. Cannot be null.
         *
         * @param source The report source.
         * @return This instance of {@code Builder}.
         */
        public Builder setReportSource(final ReportSource source) {
            _source = source;
            return this;
        }

        /**
         * Set the report recipients. Required. Cannot be null.
         *
         * @param recipients The mapping of formats to recipients.
         * @return This instance of {@code Builder}.
         */
        public Builder setRecipients(final ImmutableSetMultimap<ReportFormat, Recipient> recipients) {
            _recipients = recipients;
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
        private ImmutableSetMultimap<ReportFormat, Recipient> _recipients;
        @NotNull
        private Schedule _schedule;
    }
}
