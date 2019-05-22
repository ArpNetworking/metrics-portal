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

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import models.internal.impl.ChromeScreenshotReportSource;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Utilities for execution of {@link Report}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ReportExecution {

    /**
     * Render and send a report.
     *
     * @param report The report to render and execute.
     * @param injector A Guice injector to pull dependencies out of.
     * @param scheduled The instant the report was scheduled for.
     * @return A CompletionStage that completes when sending has completed for every recipient.
     */
    public static CompletionStage<Report.Result> execute(final Report report, final Injector injector, final Instant scheduled) {
        final ImmutableMultimap<ReportFormat, Recipient> formatToRecipients = report.getRecipientsByFormat()
                .entrySet()
                .stream()
                .collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()));
        final ImmutableMultimap<Recipient, ReportFormat> recipientToFormats = formatToRecipients.inverse();
        return renderAll(injector, formatToRecipients.keySet(), report.getSource(), scheduled)
                .thenCompose(
                        formatToRendered -> sendAll(injector, recipientToFormats, formatToRendered)
                ).thenApply(
                        nothing -> null
                );
    }

    /* package private */ static CompletionStage<ImmutableMap<ReportFormat, RenderedReport>> renderAll(
            final Injector injector,
            final ImmutableSet<ReportFormat> formats,
            final ReportSource source,
            final Instant scheduled
    ) {
        final Map<ReportFormat, RenderedReport> result = Maps.newHashMap(); // TODO(spencerpearson) -- ConcurrentMap?
        final CompletableFuture<?>[] resultSettingFutures = formats
                .stream()
                .map(format -> render(injector, source, format, scheduled)
                        .thenApply(rendered -> result.put(format, rendered))
                        .toCompletableFuture())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(resultSettingFutures).thenApply(nothing -> ImmutableMap.copyOf(result));
    }

    /* package private */ static <S extends ReportSource, F extends ReportFormat> CompletionStage<RenderedReport> render(
            final Injector injector,
            final S source,
            final F format,
            final Instant scheduled
    ) {
        @SuppressWarnings("unchecked")
        final Renderer<S, F> renderer = injector.getInstance(Key.get(Renderer.class, Names.named(getRendererKeyName(source, format))));
        return renderer.render(source, format, scheduled);
    }

    /* package private */ static CompletionStage<Void> sendAll(
            final Injector injector,
            final ImmutableMultimap<Recipient, ReportFormat> recipientToFormats,
            final ImmutableMap<ReportFormat, RenderedReport> formatToRendered
    ) {
        final CompletableFuture<?>[] futures = recipientToFormats
                .asMap()
                .entrySet()
                .stream()
                .map(entry -> {
                    final Sender sender = injector.getInstance(Key.get(Sender.class, Names.named(getSenderKeyName(entry.getKey()))));
                    return sender.send(entry.getKey(), mask(formatToRendered, entry.getValue())).toCompletableFuture();
                })
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /* package private */ static String getSenderKeyName(final Recipient recipient) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, recipient.getType().name());
    }

    /* package private */ static String getRendererKeyName(final ReportSource source, final ReportFormat format) {
        return SOURCE_TYPE_VISITOR.visit(source) + " " + FORMAT_TYPE_VISITOR.visit(format);
    }

    /**
     * Restrict {@code map} to key/value pairs whose keys are in {@code keys}.
     */
    private static <K, V> ImmutableMap<K, V> mask(final ImmutableMap<K, V> map, final Collection<K> keys) {
        return keys.stream().collect(ImmutableMap.toImmutableMap(key -> key, map::get));
    }

    private ReportExecution() {}

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
