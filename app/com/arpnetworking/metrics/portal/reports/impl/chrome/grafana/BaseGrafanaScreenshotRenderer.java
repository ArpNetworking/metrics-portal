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
import com.arpnetworking.metrics.portal.reports.impl.chrome.DevToolsService;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.typesafe.config.Config;
import models.internal.TimeRange;
import models.internal.impl.GrafanaReportPanelReportSource;
import models.internal.reports.ReportFormat;

import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Uses a headless Chrome instance to render a page as HTML.
 *
 * @param <F> the format to render into.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public abstract class BaseGrafanaScreenshotRenderer<F extends ReportFormat>
        extends com.arpnetworking.metrics.portal.reports.impl.chrome.BaseScreenshotRenderer<GrafanaReportPanelReportSource, F> {

    /**
     * Like {@link #whenLoaded}, but called when the Grafana report panel has been populated.
     *
     * @param <B> as {@link #whenLoaded}
     * @param devToolsService as {@link #whenLoaded}
     * @param source as {@link #whenLoaded}
     * @param format as {@link #whenLoaded}
     * @param timeRange as {@link #whenLoaded}
     * @param builder as {@link #whenLoaded}
     * @return a {@link CompletionStage} that completes when the builder has been populated
     */
    protected abstract <B extends RenderedReport.Builder<B, ?>> CompletionStage<B> whenReportRendered(
            DevToolsService devToolsService,
            GrafanaReportPanelReportSource source,
            F format,
            TimeRange timeRange,
            B builder
    );

    @Override
    public boolean getIgnoreCertificateErrors(final GrafanaReportPanelReportSource source) {
        return source.getWebPageReportSource().ignoresCertificateErrors();
    }

    @Override
    public URI getUri(final GrafanaReportPanelReportSource source) {
        return source.getWebPageReportSource().getUri();
    }

    @Override
    public <B extends RenderedReport.Builder<B, ?>> CompletionStage<B> whenLoaded(
            final DevToolsService devToolsService,
            final GrafanaReportPanelReportSource source,
            final F format,
            final TimeRange timeRange,
            final B builder
    ) {
        return devToolsService.nowOrOnEvent(
                "reportrendered",
                () -> {
                    final Object html = devToolsService.evaluate(
                            "document.getElementsByClassName('rendered-markdown-container')[0].srcdoc"
                    );
                    final boolean ready = (html instanceof String) && !((String) html).isEmpty();
                    LOGGER.debug()
                            .setMessage("checked for readiness")
                            .addData("source", source)
                            .addData("format", format)
                            .addData("timeRange", timeRange)
                            .addData("ready", ready)
                            .log();
                    return ready;
                }
        ).thenCompose(whatever -> {
            LOGGER.debug()
                    .setMessage("reportrendered event received or detected")
                    .addData("source", source)
                    .addData("format", format)
                    .addData("timeRange", timeRange)
                    .log();
            return whenReportRendered(devToolsService, source, format, timeRange, builder);
        });
    }

    /**
     * Public constructor.
     *
     * @param config the configuration for this renderer. Meaningful keys:
     * <ul>
     *   <li>{@code chromePath} -- the path to the Chrome binary to use to render pages.</li>
     * </ul>
     * @param renderExecutor used to run individual rendering operations
     * @param timeoutExecutor used to schedule timeouts on individual rendering operations
     */
    /* package private */ BaseGrafanaScreenshotRenderer(
            final Config config,
            final ExecutorService renderExecutor,
            final ScheduledExecutorService timeoutExecutor
    ) {
        super(config, renderExecutor, timeoutExecutor);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseGrafanaScreenshotRenderer.class);

}
