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
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import models.internal.TimeRange;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Uses a headless Chrome instance to render a page as HTML.
 *
 * @param <S> the type of {@link ReportSource} to render
 * @param <F> the format to render into.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public abstract class BaseScreenshotRenderer<S extends ReportSource, F extends ReportFormat> implements Renderer<S, F> {

    /**
     * Get, for a given source, whether we can safely ignore a bad certificate.
     *
     * @param source the source we'll be rendering
     * @return whether we can safely ignore a bad certificate
     */
    protected abstract boolean getIgnoreCertificateErrors(S source);

    /**
     * Get the URI to visit in order to render a given source.
     *
     * @param source the source we'll be rendering
     * @return the URI to visit
     */
    protected abstract URI getUri(S source);

    /**
     * Called when the page we want to render has finished loading, i.e. the JavaScript {@code load} event has fired.
     *
     * @param <B> the specific type of {@link RenderedReport.Builder} to populate from the page
     * @param devToolsService a {@link DevToolsService} connected to the page that just loaded
     * @param source the source being rendered
     * @param format the format being rendered into
     * @param timeRange the time range being reported on
     * @param builder the {@link RenderedReport.Builder} to populate from the page
     * @return a {@link CompletionStage} that completes when the builder has been populated
     */
    protected abstract <B extends RenderedReport.Builder<B, ?>> CompletionStage<B> whenLoaded(
            DevToolsService devToolsService,
            S source,
            F format,
            TimeRange timeRange,
            B builder
    );

    @Override
    public void validateRender(final S source, final F format) throws IllegalArgumentException {
        final URI uri = getUri(source);
        if (!_devToolsFactory.getOriginConfigs().isNavigationAllowed(uri)) {
            throw new IllegalArgumentException("not allowed to visit uri: " + uri);
        }
    }

    @Override
    public <B extends RenderedReport.Builder<B, ?>> CompletableFuture<B> render(
            final S source,
            final F format,
            final TimeRange timeRange,
            final B builder,
            final Duration timeout
    ) {
        final DevToolsService dts = _devToolsFactory.create(getIgnoreCertificateErrors(source));
        LOGGER.debug()
                .setMessage("rendering")
                .addData("source", source)
                .addData("format", format)
                .addData("timeRange", timeRange)
                .log();
        final CompletableFuture<Void> navigate = dts.navigate(getUri(source).toString());
        final CompletableFuture<B> result = navigate
                .thenCompose(whatever -> {
                    LOGGER.debug()
                            .setMessage("page load completed")
                            .addData("source", source)
                            .addData("format", format)
                            .addData("timeRange", timeRange)
                            .log();
                    return whenLoaded(dts, source, format, timeRange, builder);
                });

        result.whenComplete((x, e) -> {
            LOGGER.debug()
                    .setMessage("rendering completed")
                    .addData("source", source)
                    .addData("format", format)
                    .addData("timeRange", timeRange)
                    .addData("result", x)
                    .setThrowable(e)
                    .log();
            navigate.cancel(true);
            dts.close();
        });

        TIMEOUT_SERVICE.schedule(() -> result.cancel(true), timeout.toNanos(), TimeUnit.NANOSECONDS);

        return result;
    }

    /**
     * Protected constructor.
     *
     * @param devToolsFactory The {@link DevToolsFactory} to use to open tabs and drive them.
     */
    protected BaseScreenshotRenderer(
            final DevToolsFactory devToolsFactory
    ) {
        _devToolsFactory = devToolsFactory;
    }

    private final DevToolsFactory _devToolsFactory;

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseScreenshotRenderer.class);
    private static final ScheduledExecutorService TIMEOUT_SERVICE = new ScheduledThreadPoolExecutor(1);
}
