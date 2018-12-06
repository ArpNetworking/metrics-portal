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
import com.arpnetworking.metrics.portal.query.QueryProblem;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.internal.MetricsQueryResult;
import models.view.MetricsQuery;
import play.Environment;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import java.util.List;
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
     * @param queryExecutor executor to use to execute the query
     * @param environment environment we're executing in
     */
    @Inject
    public MetricsController(final ObjectMapper mapper, final QueryExecutor queryExecutor, final Environment environment) {
        _mapper = mapper;
        _queryExecutor = queryExecutor;
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
            return CompletableFuture.completedFuture(Results.badRequest(createErrorJson("null body for query")));
        }

        try {
            final MetricsQuery query = _mapper.treeToValue(body, MetricsQuery.class);
            final CompletionStage<MetricsQueryResult> response = _queryExecutor.executeQuery(query.getQuery());
            return response
                    .thenApply(models.view.MetricsQueryResult::fromInternal)
                    .<JsonNode>thenApply(_mapper::valueToTree)
                    .thenApply(Results::ok);

            // CHECKSTYLE.OFF: IllegalCatch - Translate any failure to bad input.
        } catch (final RuntimeException ex) {
            // CHECKSTYLE.ON: IllegalCatch
            return CompletableFuture.completedFuture(Results.internalServerError(createErrorJson(ex)));
        } catch (final QueryExecutionException ex) {
            return CompletableFuture.completedFuture(Results.badRequest(createErrorJson(ex.getProblems())));
        } catch (final JsonProcessingException e) {
            return CompletableFuture.completedFuture(Results.badRequest(createErrorJson(e)));
        }
    }

    private ObjectNode createErrorJson(final Exception ex) {
        if (_environment.isDev()) {
            if (ex.getMessage() != null) {
                return createErrorJson(ex.getMessage());
            } else {
                return createErrorJson(ex.toString());
            }
        }
        return createErrorJson("An error occurred in the processing of your query");
    }

    private ObjectNode createErrorJson(final String message) {
        final ObjectNode errorJson = Json.newObject();
        final ArrayNode errors = errorJson.putArray("errors");
        errors.add(message);
        return errorJson;
    }

    private ObjectNode createErrorJson(final List<QueryProblem> problems) {
        final ObjectNode errorJson = Json.newObject();
        final ArrayNode errors = errorJson.putArray("errors");
        problems.forEach(problem -> errors.add(translateProblem(problem)));
        return errorJson;
    }

    private String translateProblem(final QueryProblem problem) {
        return Http.Context.current().messages().at("query_problem." + problem.getProblemCode(), problem.getArgs().toArray());
    }

    private final ObjectMapper _mapper;
    private final QueryExecutor _queryExecutor;
    private final Environment _environment;

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsController.class);
}
