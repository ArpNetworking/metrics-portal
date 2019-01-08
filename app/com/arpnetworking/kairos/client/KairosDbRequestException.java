/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.kairos.client;

import akka.http.javadsl.model.Uri;

/**
 * An exception that represents a non-2xx KairosDB request.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class KairosDbRequestException extends RuntimeException {

    public int getHttpStatus() {
        return _httpStatus;
    }

    public String getHttpMessage() {
        return _httpMessage;
    }

    public Uri getRequestUri() {
        return _requestUri;
    }

    /**
     * Minimal constructor.
     *
     * @param httpStatus the status code from the http response
     * @param httpMessage the status message from the http response
     * @param requestUri the uri requested
     */
    public KairosDbRequestException(final int httpStatus, final String httpMessage, final Uri requestUri) {
        _httpStatus = httpStatus;
        _httpMessage = httpMessage;
        _requestUri = requestUri;
    }

    /**
     * Constructor with the inclusion of a specific exception message.
     *
     * @param message the exception message
     * @param httpStatus the status code from the http response
     * @param httpMessage the status message from the http response
     * @param requestUri the uri requested
     */
    public KairosDbRequestException(final String message, final int httpStatus, final String httpMessage, final Uri requestUri) {
        super(message);
        _httpStatus = httpStatus;
        _httpMessage = httpMessage;
        _requestUri = requestUri;
    }

    /**
     * Constructor with the inclusion of a specific exception message and cause.
     *
     * @param message the exception message
     * @param cause the cause of this exception
     * @param httpStatus the status code from the http response
     * @param httpMessage the status message from the http response
     * @param requestUri the uri requested
     */
    public KairosDbRequestException(final String message,
                                    final Throwable cause,
                                    final int httpStatus,
                                    final String httpMessage,
                                    final Uri requestUri) {
        super(message, cause);
        _httpStatus = httpStatus;
        _httpMessage = httpMessage;
        _requestUri = requestUri;
    }

    private final int _httpStatus;
    private final String _httpMessage;
    private final Uri _requestUri;
    private static final long serialVersionUID = 6622759488133086527L;
}
