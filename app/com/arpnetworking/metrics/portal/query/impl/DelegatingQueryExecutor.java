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

package com.arpnetworking.metrics.portal.query.impl;

import com.arpnetworking.metrics.portal.query.QueryWindow;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.query.QueryExecutorRegistry;
import com.google.inject.Inject;
import models.internal.BoundedMetricsQuery;
import models.internal.MetricsQuery;
import models.internal.MetricsQueryFormat;
import models.internal.MetricsQueryResult;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A query executor implementation that delegates queries to registered executors
 * within the wrapped {@link QueryExecutorRegistry}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DelegatingQueryExecutor implements QueryExecutor {
    private QueryExecutorRegistry _executors;

    /**
     * Default Constructor.
     *
     * @param executorRegistry Executor registry to delgate to
     */
    @Inject
    public DelegatingQueryExecutor(final QueryExecutorRegistry executorRegistry) {
        _executors = executorRegistry;
    }

    @Override
    public CompletionStage<MetricsQueryResult> executeQuery(final BoundedMetricsQuery query) {
        return CompletableFuture.completedFuture(null).thenCompose(ignored -> executeQueryInner(query));
    }

    @Override
    public Optional<Duration> evaluationPeriodHint(final MetricsQuery query) {
        return _executors.getExecutor(query.getQueryFormat()).flatMap(exec -> exec.evaluationPeriodHint(query));
    }

    private CompletionStage<MetricsQueryResult> executeQueryInner(final BoundedMetricsQuery query) {
        final MetricsQueryFormat format = query.getQueryFormat();
        final QueryExecutor executor =  _executors.getExecutor(format).orElseThrow(() ->
                new IllegalArgumentException("No registered executor for format: " + format)
        );
        return executor.executeQuery(query);
    }

    @Override
    public QueryWindow queryWindow(final MetricsQuery query) {
        final MetricsQueryFormat format = query.getQueryFormat();
        return _executors.getExecutor(query.getQueryFormat())
                .map(exec -> exec.queryWindow(query))
                .orElseThrow(() -> new IllegalArgumentException("No registered executor for format: " + format));
    }
}
