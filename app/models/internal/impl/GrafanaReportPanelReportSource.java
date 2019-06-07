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

    private GrafanaReportPanelReportSource(final Builder builder) {
        _webPageReportSource = builder._webPageReportSource;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("webPageReportSource", _webPageReportSource)
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GrafanaReportPanelReportSource that = (GrafanaReportPanelReportSource) o;
        return Objects.equals(_webPageReportSource, that._webPageReportSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_webPageReportSource);
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

        @NotNull
        private WebPageReportSource _webPageReportSource;

    }
}
