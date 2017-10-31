/**
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

import com.arpnetworking.mql.grammar.CollectingErrorListener;
import com.arpnetworking.mql.grammar.ExecutionException;
import com.arpnetworking.mql.grammar.MqlLexer;
import com.arpnetworking.mql.grammar.MqlParser;
import com.arpnetworking.mql.grammar.QueryRunner;
import com.arpnetworking.mql.grammar.TimeSeriesResult;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.view.MetricsQuery;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.jetbrains.annotations.NotNull;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Provider;
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
     * @param queryRunnerFactory Provider to create {@link QueryRunner}
     */
    @Inject
    public MetricsController(final ObjectMapper mapper, final Provider<QueryRunner> queryRunnerFactory) {
        _mapper = mapper;
        _queryRunnerFactory = queryRunnerFactory;
    }

    /**
     * Execute an MQL query.
     *
     * @return Future {@link Result} of the query response data.
     */
    public CompletionStage<Result> query() {
        try {
            final MetricsQuery query = parseQueryJson();
            final MqlParser.StatementContext statement = parseQuery(query);
            final QueryRunner queryRunner = _queryRunnerFactory.get();
            final CompletionStage<TimeSeriesResult> response;
            response = queryRunner.visitStatement(statement);
            return response.<JsonNode>thenApply(_mapper::valueToTree).thenApply(Results::ok);
            // CHECKSTYLE.OFF: IllegalCatch - Translate any failure to bad input.
        } catch (final RuntimeException ex) {
            // CHECKSTYLE.ON: IllegalCatch
            return CompletableFuture.completedFuture(Results.badRequest(createErrorJson(ex)));
        } catch (final ExecutionException ex) {
            return CompletableFuture.completedFuture(Results.badRequest(createErrorJson(ex.getProblems())));
        }
    }

    private MqlParser.StatementContext parseQuery(final MetricsQuery query) throws ExecutionException {
        final MqlLexer lexer = new MqlLexer(new ANTLRInputStream(query.getQuery()));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final MqlParser parser = new MqlParser(tokens);
        final CollectingErrorListener errorListener = new CollectingErrorListener();
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        MqlParser.StatementContext statement;
        try {
             statement = parser.statement(); // STAGE 1
            // CHECKSTYLE.OFF: IllegalCatch - Translate any failure to bad input.
        } catch (final RuntimeException ex) {
            // CHECKSTYLE.ON: IllegalCatch
            tokens.reset(); // rewind input stream
            parser.reset();
            parser.getInterpreter().setPredictionMode(PredictionMode.LL);
            statement = parser.statement();  // STAGE 2
        }

        if (parser.getNumberOfSyntaxErrors() != 0) {
            // Build the error object
            throw new ExecutionException(errorListener.getErrors());
        }
        return statement;
    }

    @NotNull
    private ObjectNode createErrorJson(final RuntimeException ex) {
        if (ex.getMessage() != null) {
            return createErrorJson(ex.getMessage());
        } else {
            return createErrorJson(ex.toString());
        }
    }

    @NotNull
    private ObjectNode createErrorJson(final String message) {
        final ObjectNode errorJson = Json.newObject();
        final ArrayNode errors = errorJson.putArray("errors");
        errors.add(message);
        return errorJson;
    }

    @NotNull
    private ObjectNode createErrorJson(final List<String> messages) {
        final ObjectNode errorJson = Json.newObject();
        final ArrayNode errors = errorJson.putArray("errors");
        messages.forEach(errors::add);
        return errorJson;
    }

    private MetricsQuery parseQueryJson() {
        final JsonNode body = request().body().asJson();
        if (body == null) {
            throw new RuntimeException("null body for query");
        }

        final MetricsQuery query;
        try {
            query = _mapper.treeToValue(body, MetricsQuery.class);
        } catch (final IOException e) {
            throw new RuntimeException("invalid body for query");
        }
        return query;
    }

    private final ObjectMapper _mapper;
    private final Provider<QueryRunner> _queryRunnerFactory;

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsController.class);
}
