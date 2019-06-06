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
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Uses a headless Chrome instance to render a page as HTML.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class HtmlScreenshotRenderer implements Renderer<WebPageReportSource, HtmlReportFormat> {
    @Override
    public <B extends RenderedReport.Builder<B, R>, R extends RenderedReport> CompletionStage<B> render(
            final WebPageReportSource source,
            final HtmlReportFormat format,
            final B builder
    ) {
        final DevToolsService dts = _devToolsFactory.create(source.ignoresCertificateErrors());
        final CompletableFuture<B> result = new CompletableFuture<>();
        dts.onLoad(() -> result.complete(builder.setBytes(
                ((String) dts.evaluate("document.documentElement.outerHTML")).getBytes(StandardCharsets.UTF_8)
        )));
        return result;
    }

    /**
     * Public constructor.
     *
     * @param devToolsFactory the {@link DevToolsFactory} to use to create tabs.
     */
    @Inject
    public HtmlScreenshotRenderer(final DevToolsFactory devToolsFactory) {
        _devToolsFactory = devToolsFactory;
    }

    private final DevToolsFactory _devToolsFactory;
}
