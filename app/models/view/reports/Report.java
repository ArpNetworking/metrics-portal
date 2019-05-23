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
package models.view.reports;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import models.internal.impl.DefaultReport;
import models.view.scheduling.Schedule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * View model for a {@link models.internal.reports.Report}.
 *
 * Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class Report {
    public UUID getId() {
        return _id;
    }

    public void setId(final UUID id) {
        this._id = id;
    }

    public String getName() {
        return _name;
    }

    public void setName(final String name) {
        this._name = name;
    }

    public ReportSource getSource() {
        return _source;
    }

    public void setSource(final ReportSource source) {
        this._source = source;
    }

    public Schedule getSchedule() {
        return _schedule;
    }

    public void setSchedule(final Schedule schedule) {
        this._schedule = schedule;
    }

    public void setRecipients(final List<Recipient> recipients) {
        this._recipients = recipients;
    }

    public List<Recipient> getRecipients() {
        return _recipients;
    }

    /**
     * Convert this report into its internal representation.
     *
     * @return The internal model.
     */
    public models.internal.reports.Report toInternal() {
        final ImmutableSetMultimap<models.internal.reports.ReportFormat, models.internal.reports.Recipient> internalRecipients =
                _recipients
                    .stream()
                    .collect(ImmutableSetMultimap.toImmutableSetMultimap(
                            r -> r.getFormat().toInternal(),
                            Recipient::toInternal
                    ));

        return new DefaultReport.Builder()
                .setId(_id)
                .setName(_name)
                .setReportSource(_source.toInternal())
                .setSchedule(_schedule.toInternal())
                .setRecipients(internalRecipients)
                .build();
    }

    /**
     * Create a {@code Report} from an internal model.
     *
     * @param report The internal model
     * @return The view model.
     */
    public static Report fromInternal(final models.internal.reports.Report report) {
        final models.view.reports.Report viewReport = new Report();
        viewReport.setId(report.getId());
        viewReport.setName(report.getName());
        viewReport.setSource(ReportSource.fromInternal(report.getSource()));
        viewReport.setSchedule(Schedule.fromInternal(report.getSchedule()));
        final List<models.view.reports.Recipient> recipients =
                report.getRecipientsByFormat()
                    .entrySet()
                    .stream()
                    .flatMap(Report::recipientsFromEntry)
                    .collect(ImmutableList.toImmutableList());
        viewReport.setRecipients(recipients);
        return viewReport;
    }

    private static Stream<Recipient> recipientsFromEntry(
            final Map.Entry<models.internal.reports.ReportFormat,
            Collection<models.internal.reports.Recipient>> entry
    ) {
        final models.internal.reports.ReportFormat format = entry.getKey();
        final ReportFormat viewFormat = ReportFormat.fromInternal(format);
        return entry.getValue().stream().map(r -> Recipient.fromInternal(r, viewFormat));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Report report = (Report) o;
        return _id.equals(report._id)
                && _name.equals(report._name)
                && _source.equals(report._source)
                && _schedule.equals(report._schedule)
                && _recipients.equals(report._recipients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id, _name, _source, _schedule, _recipients);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_id", _id)
                .add("_name", _name)
                .add("_source", _source)
                .add("_schedule", _schedule)
                .add("_recipients", _recipients)
                .toString();
    }

    private UUID _id;
    private String _name;
    private ReportSource _source;
    private Schedule _schedule;
    private List<Recipient> _recipients;
}
