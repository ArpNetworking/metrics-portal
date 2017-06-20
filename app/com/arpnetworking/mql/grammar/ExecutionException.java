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
package com.arpnetworking.mql.grammar;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Represents an error executing an MQL expression.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class ExecutionException extends Exception {
    /**
     * Public constructor.
     *
     * @param problems List of errors encountered
     */
    public ExecutionException(final List<String> problems) {
        super(StringUtils.join(problems, ", "));
        _problems = problems;
    }

    public List<String> getProblems() {
        return _problems;
    }

    private final List<String> _problems;
    private static final long serialVersionUID = 1L;
}
