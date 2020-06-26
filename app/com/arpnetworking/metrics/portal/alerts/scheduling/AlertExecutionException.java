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
package com.arpnetworking.metrics.portal.alerts.scheduling;

import com.google.common.collect.ImmutableList;
import models.internal.Problem;

import java.util.List;

/**
 * Represents an error executing an {@link AlertJob}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class AlertExecutionException extends Exception {
    /**
     * Public constructor.
     *
     * @param message The detail message.
     * @param problems List of problems encountered
     */
    public AlertExecutionException(final String message, final List<Problem> problems) {
        super(message);
        _problems = ImmutableList.copyOf(problems);
    }

    public ImmutableList<Problem> getProblems() {
        return _problems;
    }

    private final ImmutableList<Problem> _problems;
    private static final long serialVersionUID = 1L;
}
