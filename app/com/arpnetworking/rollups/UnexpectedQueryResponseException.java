/*
 * Copyright 2020 Dropbox Inc.
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
package com.arpnetworking.rollups;

import com.arpnetworking.kairos.client.models.MetricsQueryResponse;

/**
 * Represents an error from parsing a {@link MetricsQueryResponse}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class UnexpectedQueryResponseException extends Exception {
    /**
     * Public constructor.
     *
     * @param message The exception message.
     * @param queryResult The unexpected query response.
     */
    public UnexpectedQueryResponseException(final String message, final MetricsQueryResponse queryResult) {
        super(message);
        _queryResponse = queryResult;
    }

    /**
     * Gets the unexpected query response that triggered this exception.
     *
     * @return The query response.
     */
    public MetricsQueryResponse getQueryResponse() {
        return _queryResponse;
    }

    private final MetricsQueryResponse _queryResponse;
}
