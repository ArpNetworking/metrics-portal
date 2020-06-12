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

import com.arpnetworking.metrics.portal.query.QueryExecutionException;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.query.QueryExecutorRegistry;
import com.google.inject.Inject;
import models.internal.MetricsQuery;
import models.internal.MetricsQueryFormat;
import models.internal.MetricsQueryResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
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
    public CompletionStage<MetricsQueryResult> executeQuery(final MetricsQuery query) {
        final CompletableFuture<MetricsQueryResult> result = new CompletableFuture<>();
        try {
            return executeQueryInner(query);
            // CHECKSTYLE.OFF: IllegalCatch - Execution errors should occur asynchronously
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            result.completeExceptionally(e);
            return result;
        }
    }

    private CompletionStage<MetricsQueryResult> executeQueryInner(final MetricsQuery query) throws QueryExecutionException {
        final MetricsQueryFormat format = query.getQueryFormat();
        final QueryExecutor executor =  _executors.getExecutorFor(format).orElseThrow(() -> {
            throw new UnsupportedOperationException("No registered executor for format: " + format);
        });
        return executor.executeQuery(query);
    }
}
