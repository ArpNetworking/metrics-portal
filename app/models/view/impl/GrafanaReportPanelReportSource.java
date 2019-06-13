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

import com.arpnetworking.logback.annotations.Loggable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import models.view.reports.ReportSource;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * View model for a report source that pulls screenshots from the web.
 *
 * Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class GrafanaReportPanelReportSource implements ReportSource {

    public WebPageReportSource getWebPageReportSource() {
        return _webPageReportSource;
    }

    public void setWebPageReportSource(final WebPageReportSource value) {
        _webPageReportSource = value;
    }

    public ChronoUnit getTimeRangePeriod() {
        return _timeRangePeriod;
    }

    public void setTimeRangePeriod(final ChronoUnit value) {
        _timeRangePeriod = value;
    }

    public int getTimeRangeWidthPeriods() {
        return _timeRangeWidthPeriods;
    }

    public void setTimeRangeWidthPeriods(final int value) {
        _timeRangeWidthPeriods = value;
    }

    public int getTimeRangeEndPeriodsAgo() {
        return _timeRangeEndPeriodsAgo;
    }

    public void setTimeRangeEndPeriodsAgo(final int value) {
        _timeRangeEndPeriodsAgo = value;
    }

    @Override
    public models.internal.impl.GrafanaReportPanelReportSource toInternal() {
        return new models.internal.impl.GrafanaReportPanelReportSource.Builder()
                .setWebPageReportSource(_webPageReportSource.toInternal())
                .setTimeRangePeriod(_timeRangePeriod)
                .setTimeRangeWidthPeriods(_timeRangeWidthPeriods)
                .setTimeRangeEndPeriodsAgo(_timeRangeEndPeriodsAgo)
                .build();
    }

    /**
     * Create a {@code GrafanaReportPanelReportSource} from its internal representation.
     *
     * @param source The internal model.
     * @return The view model.
     */
    public static GrafanaReportPanelReportSource fromInternal(final models.internal.impl.GrafanaReportPanelReportSource source) {
        final GrafanaReportPanelReportSource viewSource = new GrafanaReportPanelReportSource();
        viewSource.setWebPageReportSource(WebPageReportSource.fromInternal(source.getWebPageReportSource()));
        return viewSource;
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

    @JsonProperty("webPageReportSource")
    private WebPageReportSource _webPageReportSource;
    @JsonProperty("timeRangePeriod")
    private ChronoUnit _timeRangePeriod;
    @JsonProperty("timeRangeWidthPeriods")
    private int _timeRangeWidthPeriods;
    @JsonProperty("timeRangeEndPeriodsAgo")
    private int _timeRangeEndPeriodsAgo;
}
