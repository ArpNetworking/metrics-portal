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

package com.arpnetworking.commons.serialization;

/**
 * An exception that occurred during Deserialization.
 *
 * @see Deserializer
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DeserializationException extends Exception {
    /**
     * Construct a DeserializationException with the given message.
     *
     * @param message the error message
     */
    public DeserializationException(final String message) {
        super(message);
    }

    /**
     * Construct a SerializationException with the given cause.
     *
     * @param cause the direct cause of this exception
     */
    public DeserializationException(final Throwable cause) {
        super(cause);
    }

    /**
     * Construct a SerializationException with the given message and cause.
     *
     * @param message the error message
     * @param cause the direct cause of this exception
     */
    public DeserializationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
