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
package com.arpnetworking.metrics.portal.query;

import com.google.common.collect.ImmutableList;

/**
 * Represents an error executing a query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class QueryExecutionException extends Exception {
    /**
     * Public constructor.
     *
     * @param message The detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     * @param problems List of errors encountered
     */
    public QueryExecutionException(final String message, final ImmutableList<QueryProblem> problems) {
        super(message);
        _problems = problems;
    }

    public ImmutableList<QueryProblem> getProblems() {
        return _problems;
    }

    private final ImmutableList<QueryProblem> _problems;
    private static final long serialVersionUID = 1L;
}
