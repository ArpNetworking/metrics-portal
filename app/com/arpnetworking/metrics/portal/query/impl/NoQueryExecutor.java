/*
 * Copyright 2018 Smartsheet
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
import com.arpnetworking.metrics.portal.query.QueryProblem;
import com.google.common.collect.ImmutableList;
import models.internal.MetricsQueryResult;

import java.util.concurrent.CompletionStage;
import javax.inject.Singleton;

/**
 * Implementation of a {@link QueryExecutor} that just returns an error result.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Singleton
public class NoQueryExecutor implements QueryExecutor {
    @Override
    public CompletionStage<MetricsQueryResult> executeQuery(final String query) throws QueryExecutionException {
        final QueryProblem notEnabledProblem = new QueryProblem.Builder().setProblemCode("NOT_ENABLED").build();
        throw new QueryExecutionException("Queries are not enabled", ImmutableList.of(notEnabledProblem));
    }
}
