package models.view.reports;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import models.internal.impl.DefaultReport;
import models.view.scheduling.Schedule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * View model for a {@link models.internal.reports.Report}.
 *
 * This class is mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class Report {
    private Report() {
        // Clients should use Report::fromInternal.
    }

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

    public ReportSource getReportSource() {
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

    public models.internal.reports.Report toInternal() {
        return new DefaultReport.Builder()
                .setId(_id)
                .setName(_name)
                .setReportSource(_source.toInternal())
                .setSchedule(_schedule.toInternal())
                // FIXME(cbriones)
//                .setRecipients((viewReport.getRecipients()))
                .build();
    }

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

    private static Stream<Recipient> recipientsFromEntry(final Map.Entry<models.internal.reports.ReportFormat, Collection<models.internal.reports.Recipient>> entry) {
        final models.internal.reports.ReportFormat format = entry.getKey();
        final ReportFormat viewFormat = ReportFormat.fromInternal(format);
        return entry.getValue().stream().map(r -> Recipient.fromInternal(r, viewFormat));
    }

    @JsonProperty("id")
    private UUID _id;
    @JsonProperty("name")
    private String _name;
    @JsonProperty("source")
    private ReportSource _source;
    @JsonProperty("schedule")
    private Schedule _schedule;
    @JsonProperty("recipients")
    private List<Recipient> _recipients;
}
