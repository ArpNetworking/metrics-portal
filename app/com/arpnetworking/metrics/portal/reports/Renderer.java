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

package com.arpnetworking.metrics.portal.reports;

import models.internal.TimeRange;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A mechanism for rendering a particular kind of {@link ReportSource} into a particular kind of {@link ReportFormat}.
 *
 * @param <S> The type of {@link ReportSource} to render.
 * @param <F> The type of {@link ReportFormat} to render into.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface Renderer<S extends ReportSource, F extends ReportFormat> {
    /**
     * Render a ReportSource.
     *
     * @param source The source to render.
     * @param format The format to render into.
     * @param timeRange The time range to describe in the report.
     * @param builder Will be used to construct a report. All implementations of {@code render} must call `setBytes()`.
     * @param timeout How long the renderer should be allowed to run before aborting.
     * @param <B> The type of builder provided.
     * @return A {@link CompletionStage} that completes when the report has been rendered.
     */
    <B extends RenderedReport.Builder<B, ?>> CompletableFuture<B> render(
            S source,
            F format,
            TimeRange timeRange,
            B builder,
            Duration timeout
    );
}
