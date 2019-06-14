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
import com.arpnetworking.metrics.portal.reports.Renderer;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import models.internal.TimeRange;
import models.internal.impl.PdfReportFormat;
import models.internal.impl.WebPageReportSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Uses a headless Chrome instance to render a page as PDF.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PdfScreenshotRenderer implements Renderer<WebPageReportSource, PdfReportFormat> {
    @Override
    public <B extends RenderedReport.Builder<B, ?>> CompletionStage<B> render(
            final WebPageReportSource source,
            final PdfReportFormat format,
            final TimeRange timeRange,
            final B builder
    ) {
        final DevToolsService dts = _devToolsFactory.create(source.ignoresCertificateErrors());
        final CompletableFuture<B> result = new CompletableFuture<>();
        dts.onLoad(() -> result.complete(builder.setBytes(dts.printToPdf(format.getWidthInches(), format.getHeightInches()))));
        dts.navigate(source.getUri().toString());
        return result;
    }

    /**
     * Public constructor.
     *
     * @param config the configuration for this renderer. Meaningful keys:
     * <ul>
     *   <li>{@code chromePath} -- the path to the Chrome binary to use to render pages.</li>
     * </ul>
     */
    @Inject
    public PdfScreenshotRenderer(@Assisted final Config config) {
        _devToolsFactory = new DevToolsFactory(config.getString("chromePath"));
    }

    private final DevToolsFactory _devToolsFactory;
}
