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
package com.arpnetworking.metrics.util;

import com.datastax.driver.extras.codecs.ParsingCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import javax.inject.Inject;

/**
 * Codec to be used to serialize objects with Jackson.
 *
 * @param <T> Class that this codec can serialize
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class JacksonCodec<T> extends ParsingCodec<T> {
    /**
     * Public constructor.
     *
     * @param mapper {@link ObjectMapper} to use to serialize objects
     * @param clazz class this codec can serialize
     */
    @Inject
    public JacksonCodec(final ObjectMapper mapper, final Class<T> clazz) {
        super(clazz);
        _mapper = mapper;
    }

    @Override
    protected String toString(final T value) {
        try {
            return _mapper.writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected T fromString(final String value) {
        try {
            return (T) _mapper.readValue(value, getJavaType().getRawType());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final ObjectMapper _mapper;
}
