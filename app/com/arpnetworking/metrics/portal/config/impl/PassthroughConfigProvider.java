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

package com.arpnetworking.metrics.portal.config.impl;

import com.arpnetworking.metrics.portal.config.ConfigProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * A config provider that serializes and passes down the given object to its
 * downstream consumer.
 * <p>
 * The registered update callback will be invoked exactly once, at startup.
 * <p>
 * This is only intended for use internally.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class PassthroughConfigProvider implements ConfigProvider {
    private final Object _config;
    private final ObjectMapper _mapper;

    /**
     * Create a config provider that will supply the given alerts.
     *
     * @param config The configuration to supply.
     * @param mapper The object mapper to use for serialization.
     */
    public PassthroughConfigProvider(final Object config, final ObjectMapper mapper) {
        _config = config;
        _mapper = mapper;
    }

    @Override
    public void start(final Consumer<InputStream> update) {
        final byte[] byteArray;
        try {
            byteArray = _mapper.writeValueAsBytes(_config);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        update.accept(new ByteArrayInputStream(byteArray));
    }

    @Override
    public void stop() { }
}
