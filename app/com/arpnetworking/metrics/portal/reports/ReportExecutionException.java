/*
 * Copyright 2019 Dropbox
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

import com.google.common.collect.ImmutableList;
import models.internal.Problem;

/**
 * Represents an error executing a query.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class ReportExecutionException extends Exception {
    /**
     * Public constructor.
     *
     * @param message The detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     * @param problems List of errors encountered
     */
    public ReportExecutionException(final String message, final ImmutableList<Problem> problems) {
        super(message);
        _problems = problems;
    }

    public ImmutableList<Problem> getProblems() {
        return _problems;
    }

    private final ImmutableList<Problem> _problems;
    private static final long serialVersionUID = 1L;
}
