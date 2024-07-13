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

package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import jakarta.inject.Inject;
import models.internal.TimeRange;
import models.internal.impl.PdfReportFormat;
import models.internal.impl.WebPageReportSource;

import java.net.URI;
import java.util.concurrent.CompletionStage;

/**
 * Uses a headless Chrome instance to render a page as PDF.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PdfScreenshotRenderer extends BaseScreenshotRenderer<WebPageReportSource, PdfReportFormat> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlScreenshotRenderer.class);

    @Override
    protected boolean getIgnoreCertificateErrors(final WebPageReportSource source) {
        return source.ignoresCertificateErrors();
    }

    @Override
    protected URI getUri(final WebPageReportSource source) {
        return source.getUri();
    }

    @Override
    protected <B extends RenderedReport.Builder<B, ?>> CompletionStage<B> whenLoaded(
            final DevToolsService devToolsService,
            final WebPageReportSource source,
            final PdfReportFormat format,
            final TimeRange timeRange,
            final B builder
    ) {
        LOGGER.debug()
                .setMessage("Rendering page content to HTML")
                .addData("source", source)
                .addData("format", format)
                .addData("timeRange", timeRange)
                .addData("builder", builder)
                .log();
        return devToolsService.printToPdf(format.getWidthInches(), format.getHeightInches()).thenApply(builder::setBytes);
    }

    /**
     * Public constructor.
     *
     * @param factory The {@link DevToolsFactory} to use to open tabs and drive them.
     */
    @Inject
    public PdfScreenshotRenderer(final DevToolsFactory factory) {
        super(factory);
    }
}
