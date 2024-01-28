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
import models.internal.impl.PdfReportFormat;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Uses a headless Chrome instance to render a page as HTML.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PdfGrafanaScreenshotRenderer extends BaseGrafanaScreenshotRenderer<PdfReportFormat> {
    @Override
    public boolean getIgnoreCertificateErrors(final GrafanaReportPanelReportSource source) {
        return source.getWebPageReportSource().ignoresCertificateErrors();
    }

    @Override
    public URI getUri(final GrafanaReportPanelReportSource source) {
        return source.getWebPageReportSource().getUri();
    }

    @Override
    public <B extends RenderedReport.Builder<B, ?>> CompletionStage<B> whenReportRendered(
            final DevToolsService devToolsService,
            final GrafanaReportPanelReportSource source,
            final PdfReportFormat format,
            final TimeRange timeRange,
            final B builder
    ) {
        final CompletableFuture<B> result = new CompletableFuture<>();
        return devToolsService.evaluate(
                "(() => {\n"
                        + "  var body = document.getElementsByClassName('rendered-markdown-container')[0].srcdoc;\n"
                        + "  document.open(); document.write(body); document.close();\n"
                        + "})();\n"
        ).thenCompose(
                anything -> devToolsService.printToPdf(format.getWidthInches(), format.getHeightInches())
        ).thenApply(builder::setBytes);
    }

    /**
     * Public constructor.
     *
     * @param factory The {@link DevToolsFactory} to use to open tabs and drive them.
     */
    @Inject
    public PdfGrafanaScreenshotRenderer(final DevToolsFactory factory) {
        super(factory);
    }
}
