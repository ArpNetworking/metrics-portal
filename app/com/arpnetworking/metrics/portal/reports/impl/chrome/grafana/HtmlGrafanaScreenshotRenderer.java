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

package com.arpnetworking.metrics.portal.reports.impl.chrome.grafana;

import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.arpnetworking.metrics.portal.reports.impl.chrome.DevToolsFactory;
import com.arpnetworking.metrics.portal.reports.impl.chrome.DevToolsService;
import jakarta.inject.Inject;
import models.internal.TimeRange;
import models.internal.impl.GrafanaReportPanelReportSource;
import models.internal.impl.HtmlReportFormat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

/**
 * Uses a headless Chrome instance to render a page as HTML.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class HtmlGrafanaScreenshotRenderer extends BaseGrafanaScreenshotRenderer<HtmlReportFormat> {

    @Override
    protected <B extends RenderedReport.Builder<B, ?>> CompletionStage<B> whenReportRendered(
            final DevToolsService devToolsService,
            final GrafanaReportPanelReportSource source,
            final HtmlReportFormat format,
            final TimeRange timeRange,
            final B builder
    ) {
        return devToolsService.evaluate("document.getElementsByClassName('rendered-markdown-container')[0].srcdoc")
                .thenApply(html -> {
                    return builder.setBytes(((String) html).getBytes(StandardCharsets.UTF_8));
                });
    }

    /**
     * Public constructor.
     *
     * @param factory The {@link DevToolsFactory} to use to open tabs and drive them.
     */
    @Inject
    public HtmlGrafanaScreenshotRenderer(final DevToolsFactory factory) {
        super(factory);
    }
}
