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

import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import models.internal.TimeRange;
import models.internal.impl.DefaultRenderedReport;
import models.internal.impl.DefaultReportResult;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;
import play.Environment;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utilities for execution of {@link Report}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ReportExecutionContext {

    /**
     * Render and send a report.
     *
     * Renders the report into all necessary formats (using the given Injector to look up the necessary {@link Renderer}s)
     * and then sends each recipient their requested formats of {@link RenderedReport} (using the Injector to look up the {@link Sender}s).
     *
     * @param report The report to render and execute.
     * @param scheduled The instant the report was scheduled for.
     * @return A CompletionStage that completes when sending has completed for every recipient.
     */
    public CompletionStage<Report.Result> execute(final Report report, final Instant scheduled) {
        final ChronoUnit resolution = report.getSchedule().accept(TimeRangeVisitor.getInstance());
        final TimeRange timeRange = new TimeRange(
                resolution.addTo(scheduled.truncatedTo(resolution), -1),
                scheduled.truncatedTo(resolution)
        );
        final ImmutableMultimap<ReportFormat, Recipient> formatToRecipients = report.getRecipientsByFormat()
                .entrySet()
                .stream()
                .collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()));
        final ImmutableMultimap<Recipient, ReportFormat> recipientToFormats = formatToRecipients.inverse();

        return CompletableFuture.supplyAsync(() -> {
            verifyDependencies(report);
            return null;
        }).thenCompose(nothing ->
                renderAll(formatToRecipients.keySet(), report, timeRange)
        ).thenCompose(formatToRendered ->
                sendAll(report, recipientToFormats, formatToRendered, timeRange)
        ).thenApply(nothing ->
                new DefaultReportResult()
        );
    }

    /**
     * Verify that the given Injector has everything necessary to render the given Report.
     *
     * @throws IllegalArgumentException if anything is missing.
     */
    /* package private */ void verifyDependencies(final Report report) {
        for (final ReportFormat format : report.getRecipientsByFormat().keySet()) {
            getRenderer(report.getSource(), format);
        }
        final Collection<Recipient> allRecipients = report.getRecipientsByFormat().values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        for (final Recipient recipient : allRecipients) {
            getSender(recipient);
        }
    }

    /**
     * Render a ReportSource into many different formats.
     *
     * @return a CompletionStage mapping each given format to its RenderedReport. Completes when every render has finished.
     * @throws IllegalArgumentException if any necessary {@link Renderer} is missing from the given Injector.
     */
    /* package private */ CompletionStage<ImmutableMap<ReportFormat, RenderedReport>> renderAll(
            final ImmutableSet<ReportFormat> formats,
            final Report report,
            final TimeRange timeRange
    ) {
        final Map<ReportFormat, RenderedReport> result = Maps.newConcurrentMap();
        final CompletableFuture<?>[] resultSettingFutures = formats
                .stream()
                .map(format ->
                        getRenderer(report.getSource(), format)
                        .render(
                                report.getSource(),
                                format,
                                timeRange,
                                new DefaultRenderedReport.Builder()
                                        .setReport(report)
                                        .setFormat(format)
                                        .setGeneratedAt(_clock.instant())
                                        .setTimeRange(timeRange),
                                report.getTimeout()
                        )
                        .thenApply(builder -> result.put(format, builder.build()))
                        .toCompletableFuture())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(resultSettingFutures).thenApply(nothing -> ImmutableMap.copyOf(result));
    }

    /**
     * Send RenderedReports to many different recipients.
     *
     * @param recipientToFormats specifies which formats of report each recipient should receive.
     * @param formatToRendered maps each format to the report rendered into that format.
     *   The {@code keySet()} must contain every format in the union of {@code recipientToFormats.values()}.
     * @return a CompletionStage that completes when every send has finished.
     * @throws IllegalArgumentException if any necessary {@link Sender} is missing from the given Injector.
     */
    /* package private */ CompletionStage<Void> sendAll(
            final Report report,
            final ImmutableMultimap<Recipient, ReportFormat> recipientToFormats,
            final ImmutableMap<ReportFormat, RenderedReport> formatToRendered,
            final TimeRange timeRange
    ) {
        final CompletableFuture<?>[] futures = recipientToFormats
                .asMap()
                .entrySet()
                .stream()
                .map(entry -> getSender(entry.getKey())
                        .send(entry.getKey(), mask(formatToRendered, entry.getValue()))
                        .toCompletableFuture())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /* package private */ <S extends ReportSource, F extends ReportFormat> Renderer<S, F> getRenderer(
            final S source,
            final F format
    ) {
        final Renderer<?, ?> result = _renderers.getOrDefault(source.getType(), ImmutableMap.of()).get(format.getMimeType());
        if (result == null) {
            throw new IllegalArgumentException(
                    "no Renderer exists for source type " + source.getType() + ", MIME type " + format.getMimeType()
            );
        }
        @SuppressWarnings("unchecked")
        final Renderer<S, F> uncheckedResult = (Renderer<S, F>) result;
        return uncheckedResult;
    }

    /* package private */ Sender getSender(final Recipient recipient) {
        final Sender result = _senders.get(recipient.getType());
        if (result == null) {
            throw new IllegalArgumentException(
                    "no Sender exists for recipient type " + recipient.getType()
            );
        }
        return result;
    }

    /**
     * Restrict {@code map} to key/value pairs whose keys are in {@code keys}.
     */
    private static <K, V> ImmutableMap<K, V> mask(final ImmutableMap<K, V> map, final Collection<K> keys) {
        return keys.stream().collect(ImmutableMap.toImmutableMap(key -> key, map::get));
    }

    /**
     * Public constructor.
     *
     * @param clock Clock to use to get the current time.
     * @param injector Guice Injector to load the classes specified in the config.
     * @param environment Environment used to load the classes specified in the config.
     * @param config Config to identify {@link Renderer}s / {@link Sender}s / other necessary objects for report execution.
     */
    @Inject
    public ReportExecutionContext(final Clock clock, final Injector injector, final Environment environment, final Config config) {
        _clock = clock;
        if (config.hasPath("reporting")) {
            _renderers = transformMap(
                    this.<Renderer<?, ?>>loadMapMapObject(injector, environment, config.getObject("reporting.renderers")),
                    SourceType::valueOf,
                    v -> transformMap(v, MediaType::parse, v2 -> v2)
            );
            _senders = transformMap(
                    this.<Sender>loadMapObject(injector, environment, config.getObject("reporting.senders")),
                    RecipientType::valueOf,
                    v -> v
            );
        } else {
            _renderers = ImmutableMap.of();
            _senders = ImmutableMap.of();
        }
    }

    // CHECKSTYLE.OFF: MethodTypeParameterNameCheck
    private static <K1, K2, V1, V2> ImmutableMap<K2, V2> transformMap(
            final Map<K1, V1> map,
            final Function<K1, K2> k,
            final Function<V1, V2> v
    ) {
    // CHECKSTYLE.ON: MethodTypeParameterNameCheck
        return map
                .entrySet()
                .stream()
                .collect(ImmutableMap.toImmutableMap(
                        e -> k.apply(e.getKey()),
                        e -> v.apply(e.getValue())
                ));
    }

    /**
     * Helpers to instantiates maps of POJOs from a ConfigObject specification.
     */
    private <T> ImmutableMap<String, T> loadMapObject(
            final Injector injector,
            final Environment environment,
            final ConfigObject config
    ) {
        return config.entrySet().stream().collect(ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                e -> ConfigurationHelper.toInstance(injector, environment, ((ConfigObject) e.getValue()).toConfig())
        ));
    }
    private <T> ImmutableMap<String, ImmutableMap<String, T>> loadMapMapObject(
            final Injector injector,
            final Environment environment,
            final ConfigObject config
    ) {
        return config.entrySet().stream().collect(ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                e -> loadMapObject(injector, environment, (ConfigObject) e.getValue())
        ));
    }

    private final Clock _clock;
    private final ImmutableMap<SourceType, ImmutableMap<MediaType, Renderer<?, ?>>> _renderers;
    private final ImmutableMap<RecipientType, Sender> _senders;

    private static final class TimeRangeVisitor extends Schedule.Visitor<ChronoUnit> {
        private static final TimeRangeVisitor INSTANCE = new TimeRangeVisitor();

        public static TimeRangeVisitor getInstance() {
            return INSTANCE;
        }

        @Override
        public ChronoUnit visitPeriodic(final PeriodicSchedule schedule) {
            return schedule.getPeriod();
        }

        @Override
        public ChronoUnit visitOneOff(final OneOffSchedule schedule) {
            return ChronoUnit.DAYS;
        }

        @Override
        public ChronoUnit visitNever(final NeverSchedule schedule) {
            return ChronoUnit.DAYS;
        }
    }

}
