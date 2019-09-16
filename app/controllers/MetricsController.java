/*
 * Copyright 2017 Smartsheet
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
package controllers;

import com.arpnetworking.metrics.portal.query.QueryExecutionException;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.query.QueryExecutorRegistry;
import com.arpnetworking.play.metrics.ProblemHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import models.internal.MetricsQueryResult;
import models.internal.Problem;
import models.view.MetricsQuery;
import play.Environment;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Controller to vend metrics.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Singleton
public class MetricsController extends Controller {

    /**
     * Public constructor.
     *
     * @param mapper {@link ObjectMapper} for the app
     * @param queryExecutorRegistry registry of query executors
     * @param environment environment we're executing in
     */
    @Inject
    public MetricsController(final ObjectMapper mapper, final QueryExecutorRegistry queryExecutorRegistry, final Environment environment) {
        _mapper = mapper;
        _queryExecutorRegistry = queryExecutorRegistry;
        _environment = environment;
    }

    /**
     * Execute a metrics query.
     *
     * @return Future {@link Result} of the query response data.
     */
    public CompletionStage<Result> query() {
        final JsonNode body = request().body().asJson();
        if (body == null) {
            return CompletableFuture.completedFuture(
                    Results.badRequest(ProblemHelper.createErrorJson(
                            new Problem.Builder().setProblemCode("request.UNEXPECTED_EMPTY_BODY").build())));
        }

        try {
            final MetricsQuery query = _mapper.treeToValue(body, MetricsQuery.class);
            final QueryExecutor queryExecutor = _queryExecutorRegistry.getExecutor(query.getExecutor());
            if (queryExecutor == null) {
                throw new QueryExecutionException(
                        "Unknown query executor",
                        ImmutableList.of(
                                new Problem.Builder()
                                        .setProblemCode("query_problem.UNKNOWN_EXECUTOR")
                                        .build()));
            }
            final CompletionStage<MetricsQueryResult> response = queryExecutor.executeQuery(query.toInternal());
            return response
                    .thenApply(models.view.MetricsQueryResult::fromInternal)
                    .<JsonNode>thenApply(_mapper::valueToTree)
                    .thenApply(Results::ok);

            // CHECKSTYLE.OFF: IllegalCatch - Translate any failure to bad input.
        } catch (final RuntimeException ex) {
            // CHECKSTYLE.ON: IllegalCatch
            return CompletableFuture.completedFuture(Results.internalServerError(
                    ProblemHelper.createErrorJson(_environment, ex, "request.UNKNOWN_ERROR")));
        } catch (final QueryExecutionException ex) {
            return CompletableFuture.completedFuture(Results.badRequest(
                    ProblemHelper.createErrorJson(ex.getProblems())));
        } catch (final JsonProcessingException ex) {
            return CompletableFuture.completedFuture(Results.badRequest(
                    ProblemHelper.createErrorJson(_environment, ex, "request.BAD_REQUEST")));
        }
    }

    private final ObjectMapper _mapper;
    private final QueryExecutorRegistry _queryExecutorRegistry;
    private final Environment _environment;

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsController.class);
}
