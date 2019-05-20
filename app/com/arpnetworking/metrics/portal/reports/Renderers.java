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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import models.internal.impl.ChromeScreenshotReportSource;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Utilities for rendering reports.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class Renderers {

    /**
     * Renders a source into each of several formats, yielding a map from each format to that rendering.
     *
     * @param injector The injector to retrieve the appropriate renderers from.
     * @param formats The {@link ReportFormat}s to be rendered.
     * @param source The {@link ReportSource} to be rendered.
     * @return TODO(spencerpearson).
     */
    public static CompletionStage<ImmutableMap<ReportFormat, RenderedReport>> renderAll(
            final Injector injector,
            final ImmutableSet<ReportFormat> formats,
            final ReportSource source
            ) {
        final Map<ReportFormat, RenderedReport> result = Maps.newHashMap(); // TODO(spencerpearson) -- ConcurrentMap?
        final CompletableFuture<?>[] resultSettingFutures = formats
                .stream()
                .map(format -> render(injector, source, format)
                        .thenApply(rendered -> result.put(format, rendered))
                        .toCompletableFuture())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(resultSettingFutures).thenApply(nothing -> ImmutableMap.copyOf(result));
    }

    /* package private */ static <S extends ReportSource, F extends ReportFormat> CompletionStage<RenderedReport> render(
            final Injector injector,
            final S source,
            final F format
    ) {
        @SuppressWarnings("unchecked")
        final Renderer<S, F> renderer = injector.getInstance(Key.get(Renderer.class, Names.named(getKeyName(source, format))));
        return renderer.render(source, format);
    }

    private Renderers() {}

    /* package private */ static String getKeyName(final ReportSource source, final ReportFormat format) {
        return SOURCE_TYPE_VISITOR.visit(source) + " " + FORMAT_TYPE_VISITOR.visit(format);
    }

    private static final ReportSource.Visitor<String> SOURCE_TYPE_VISITOR = new ReportSource.Visitor<String>() {
        @Override
        public String visit(final ChromeScreenshotReportSource source) {
            return "web";
        }
    };
    private static final ReportFormat.Visitor<String> FORMAT_TYPE_VISITOR = new ReportFormat.Visitor<String>() {
        @Override
        public String visit(final HtmlReportFormat format) {
            return "html";
        }
        @Override
        public String visit(final PdfReportFormat format) {
            return "pdf";
        }
    };
}
