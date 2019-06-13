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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.reports.SourceType;
import com.google.common.base.MoreObjects;
import models.internal.reports.ReportSource;
import net.sf.oval.constraint.NotNull;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal model for a report source that pulls content from a Grafana report panel.
 *
 * The URI for this source should point to an individual Grafana report panel,
 * e.g. {@code https://play.grafana.org/d/000000012/grafana-play-home?orgId=1&fullscreen&panelId=3} .
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Loggable
public final class GrafanaReportPanelReportSource implements ReportSource {
    private final WebPageReportSource _webPageReportSource;
    private final ChronoUnit _timeRangePeriod;
    private final int _timeRangeWidthPeriods;
    private final int _timeRangeEndPeriodsAgo;

    private GrafanaReportPanelReportSource(final Builder builder) {
        _webPageReportSource = builder._webPageReportSource;
        _timeRangePeriod = builder._timeRangePeriod;
        _timeRangeWidthPeriods = builder._timeRangeWidthPeriods;
        _timeRangeEndPeriodsAgo = builder._timeRangeEndPeriodsAgo;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_webPageReportSource", _webPageReportSource)
                .add("_timeRangePeriod", _timeRangePeriod)
                .add("_timeRangeWidthPeriods", _timeRangeWidthPeriods)
                .add("_timeRangeEndPeriodsAgo", _timeRangeEndPeriodsAgo)
                .toString();
    }

    @Override
    public UUID getId() {
        return _webPageReportSource.getId();
    }

    /**
     * Get the underlying {@link WebPageReportSource} which describes the Grafana-panel webpage to load.
     *
     * @return the URI to load.
     */
    public WebPageReportSource getWebPageReportSource() {
        return _webPageReportSource;
    }

    public ChronoUnit getTimeRangePeriod() {
        return _timeRangePeriod;
    }

    public int getTimeRangeWidthPeriods() {
        return _timeRangeWidthPeriods;
    }

    public int getTimeRangeEndPeriodsAgo() {
        return _timeRangeEndPeriodsAgo;
    }

    @Override
    public SourceType getType() {
        return SourceType.GRAFANA;
    }

    @Override
    public <T> T accept(final Visitor<T> sourceVisitor) {
        return sourceVisitor.visitGrafana(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrafanaReportPanelReportSource)) {
            return false;
        }
        final GrafanaReportPanelReportSource that = (GrafanaReportPanelReportSource) o;
        return _timeRangeWidthPeriods == that._timeRangeWidthPeriods
                && _timeRangeEndPeriodsAgo == that._timeRangeEndPeriodsAgo
                && _webPageReportSource.equals(that._webPageReportSource)
                && _timeRangePeriod == that._timeRangePeriod;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_webPageReportSource, _timeRangePeriod, _timeRangeWidthPeriods, _timeRangeEndPeriodsAgo);
    }

    /**
     * Builder implementation that constructs {@link GrafanaReportPanelReportSource}.
     */
    public static final class Builder extends OvalBuilder<GrafanaReportPanelReportSource> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(GrafanaReportPanelReportSource::new);
        }

        /**
         * The {@link WebPageReportSource} representing the underlying page to be loaded. Required. Cannot be null.
         *
         * @param webPageReportSource The underlying web page report source.
         * @return This instance of {@code Builder}.
         */
        public Builder setWebPageReportSource(final WebPageReportSource webPageReportSource) {
            _webPageReportSource = webPageReportSource;
            return this;
        }

        /**
         * The unit of time that the time-range start/end times will be truncated to. Required. Cannot be null.
         *
         * @param period The underlying web page report source.
         * @return This instance of {@code Builder}.
         */
        public Builder setTimeRangePeriod(final ChronoUnit period) {
            _timeRangePeriod = period;
            return this;
        }

        /**
         * The width of the time range to query Grafana for, in periods. Required. Cannot be null.
         *
         * @param widthPeriods The number of periods.
         *   For example, if at 2am you want to run a report for the previous day, you probably have Period=DAILY and widthPeriods=1.
         * @return This instance of {@code Builder}.
         */
        public Builder setTimeRangeWidthPeriods(final int widthPeriods) {
            _timeRangeWidthPeriods = widthPeriods;
            return this;
        }

        /**
         * The number of periods between the end-time and the (truncated) scheduled reporting time. Required. Cannot be null.
         *
         * @param endPeriodsAgo The number of periods.
         *   For example, if at 2am you want to run a report for the previous day, you would have Period=DAILY and endPeriodsAgo=0
         *   (because the scheduled time will get rounded to the start of the day; which is exactly when the time-range should end).
         *   If you for some reason wanted to run a report for the <i>previous</i> previous day (e.g. on 2am Apr 3 you want to generate
         *   a report looking at Apr 1), then you would have Period=DAILY and endPeriodsAgo=1.
         * @return This instance of {@code Builder}.
         */
        public Builder setTimeRangeEndPeriodsAgo(final int endPeriodsAgo) {
            _timeRangeEndPeriodsAgo = endPeriodsAgo;
            return this;
        }

        @NotNull
        private WebPageReportSource _webPageReportSource;
        @NotNull
        private ChronoUnit _timeRangePeriod;
        @NotNull
        private int _timeRangeWidthPeriods;
        @NotNull
        private int _timeRangeEndPeriodsAgo;

    }
}
