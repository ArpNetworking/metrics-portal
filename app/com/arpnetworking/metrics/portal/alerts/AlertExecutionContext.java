/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.alerts;

import com.arpnetworking.kairos.client.models.DataPoint;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.service.KairosDbService;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlertEvaluationResult;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Utility class for scheduling and evaluating alerts.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class AlertExecutionContext {
    private final KairosDbService _kairosDbService;
    private final ObjectMapper _objectMapper;
    private final Duration _executionInterval;

    @Inject
    public AlertExecutionContext(
            final KairosDbService service,
            final ObjectMapper objectMapper,
            final Duration executionInterval
    ) {
        _kairosDbService = service;
        _objectMapper = objectMapper;
        _executionInterval = executionInterval;
    }

    public CompletionStage<AlertEvaluationResult> execute(final Alert alert, final Instant scheduled) {
        final MetricsQuery kdbQuery;
        try {
            kdbQuery = _objectMapper.readValue(
                    alert.getQuery().getQuery(),
                    MetricsQuery.class
            );
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return _kairosDbService.queryMetrics(kdbQuery)
                .thenApply(res -> {
                    if (res.getQueries().size() != 1) {
                        throw new RuntimeException("Unexpected number of queries in response.");
                    }
                    final ImmutableList<MetricsQueryResponse.QueryResult> results = res.getQueries().get(0).getResults();
                    final ImmutableList<Map<String, String>> firingTags = results.stream()
                            .flatMap(queryResult -> {
                                final List<DataPoint> values = queryResult.getValues();
                                if (!values.isEmpty()) {
                                    return queryResult.getGroupBy().stream()
                                            .filter(queryGroupBy -> queryGroupBy instanceof MetricsQueryResponse.QueryTagGroupBy)
                                            .map(queryGroupBy -> (MetricsQueryResponse.QueryTagGroupBy) queryGroupBy)
                                            .map(MetricsQueryResponse.QueryTagGroupBy::getGroup);
                                }
                                return Stream.empty();
                            }).collect(ImmutableList.toImmutableList());
                    return new DefaultAlertEvaluationResult.Builder()
                            .setFiringTags(firingTags)
                            .build();
                });
    }

    /**
     * Get an evaluation schedule for this alert.
     *
     * This will attempt to find the largest possible schedule that will still
     * guarantee alert evalation will not fall behind.
     *
     * If this is not possible, then a minimally
     *
     * @param alert The alert.
     * @return a schedule
     */
    public Schedule getSchedule(final Alert alert) {
        return new PeriodicSchedule.Builder()
                .setRunAtAndAfter(Instant.MIN)
                .setOffset(Duration.ofSeconds(0))
                .setPeriod(ChronoUnit.MINUTES)
                .setZone(ZoneOffset.UTC)
                .build();
    }
}
