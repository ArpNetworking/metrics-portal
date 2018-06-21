/**
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

import com.arpnetworking.metrics.portal.query.ExecutionException;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.mql.grammar.CollectingErrorListener;
import com.arpnetworking.mql.grammar.MqlLexer;
import com.arpnetworking.mql.grammar.MqlParser;
import com.arpnetworking.mql.grammar.QueryRunner;
import com.arpnetworking.mql.grammar.TimeSeriesResult;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;

import java.util.concurrent.CompletionStage;

/**
 * A query executor to execute MQL.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Singleton
public class MqlQueryExecutor implements QueryExecutor {
    /**
     * Public constructor.
     *
     * @param queryRunnerFactory factory for creating {@link QueryRunner}
     */
    @Inject
    public MqlQueryExecutor(final Provider<QueryRunner> queryRunnerFactory) {
        _queryRunnerFactory = queryRunnerFactory;
    }

    @Override
    public CompletionStage<TimeSeriesResult> executeQuery(final String query) throws ExecutionException {
        final QueryRunner queryRunner = _queryRunnerFactory.get();
        final MqlParser.StatementContext statement = parseQuery(query);
        return queryRunner.visitStatement(statement);
    }

    private MqlParser.StatementContext parseQuery(final String query) throws ExecutionException {
        final MqlLexer lexer = new MqlLexer(new ANTLRInputStream(query));
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

    private final Provider<QueryRunner> _queryRunnerFactory;
}
