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
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import jakarta.inject.Inject;
import models.internal.TimeRange;
import models.internal.impl.GrafanaReportPanelReportSource;
import models.internal.reports.ReportFormat;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

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
        final CompletableFuture<B> result = new CompletableFuture<>();
        LOGGER.debug()
                .setMessage("waiting for reportrendered event")
                .addData("source", source)
                .addData("format", format)
                .addData("timeRange", timeRange)
                .log();

        final CompletableFuture<?> successFuture = devToolsService.nowOrOnEvent(
                "reportrendered",
                () -> {
                    final Object html;
                    try {
                        // TODO(spencerpearson): get rid of this .get(). It's not a big deal, since
                        //   (a) this method is executed in a threadpool dedicated to doing Chrome stuff, and
                        //   (b) evaluate() should be pretty quick,
                        //   but this .get() still results in two of that pool's threads blocking instead of just one.
                        html = devToolsService.evaluate(
                                "document.getElementsByClassName('rendered-markdown-container')[0].srcdoc"
                        ).get();
                    } catch (final InterruptedException | ExecutionException e) {
                        throw new CompletionException(e);
                    }
                    final boolean ready = html instanceof String && !((String) html).isEmpty();
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
        }).thenApply(result::complete);

        final CompletableFuture<?> failureFuture = devToolsService.nowOrOnEvent(
                "reportrenderfailed",
                () -> {
                    // TODO(spencerpearson): ^ this might miss a very fast failure.
                    //   How can we avoid this race condition?
                    final Object rawHtml;
                    try {
                        rawHtml = devToolsService.evaluate("document.documentElement.outerHTML").get();
                    } catch (final InterruptedException | ExecutionException e) {
                        throw new CompletionException(e);
                    }
                    LOGGER.debug()
                            .setMessage("snapshot of html looking for failiure")
                            .addData("bodySrc", rawHtml)
                            .log();
                    return false;
                }
        ).thenApply(whatever -> {
            LOGGER.debug()
                    .setMessage("browser fired reportrenderfailed event")
                    .addData("source", source)
                    .addData("format", format)
                    .addData("timeRange", timeRange)
                    .log();
            result.completeExceptionally(new BrowserReportedFailure());
            return null;
        });

        // Ensure that if the result gets cancelled, so do the event-listener futures
        result.whenComplete((x, e) -> {
            successFuture.cancel(true);
            failureFuture.cancel(true);
        });

        return result;
    }

    /**
     * Public constructor.
     *
     * @param factory The {@link DevToolsFactory} to use to open tabs and drive them.
     */
    @Inject
    /* package private */ BaseGrafanaScreenshotRenderer(final DevToolsFactory factory) {
        super(factory);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseGrafanaScreenshotRenderer.class);

    /**
     * Indicates that a report failed to render because of something in JavaScript-land.
     */
    public static final class BrowserReportedFailure extends Exception {
        private static final long serialVersionUID = 3176627859502569121L;
    }

}
