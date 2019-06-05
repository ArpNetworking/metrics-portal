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
import models.internal.impl.DefaultRenderedReport;
import models.internal.impl.WebPageReportSource;
import models.internal.reports.ReportFormat;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * Common code for renderers that use Chrome to load and scrape pages.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
/* package private */ abstract class BaseScreenshotRenderer<F extends ReportFormat> implements Renderer<WebPageReportSource, F> {

    protected abstract CompletionStage<byte[]> getPageContent(DevToolsService dts, WebPageReportSource source, F format);

    @Override
    public CompletionStage<RenderedReport> render(
            final WebPageReportSource source,
            final F format,
            final Instant scheduled
    ) {
        final DevToolsService dts = _devToolsFactory.create(source.ignoresCertificateErrors());
        return getPageContent(dts, source, format)
                .thenApply(bytes -> new DefaultRenderedReport.Builder()
                        .setFormat(format)
                        .setScheduledFor(scheduled)
                        .setGeneratedAt(Instant.now())
                        .setBytes(bytes)
                        .build());
    }

    /**
     * Public constructor.
     *
     * @param devToolsFactory the {@link DevToolsFactory} to use to create tabs.
     */
    @Inject
    BaseScreenshotRenderer(final DevToolsFactory devToolsFactory) {
        _devToolsFactory = devToolsFactory;
    }

    private final DevToolsFactory _devToolsFactory;
}
