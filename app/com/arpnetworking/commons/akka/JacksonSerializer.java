/*
 * Copyright 2021 Dropbox, Inc.
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

package com.arpnetworking.commons.akka;

import akka.actor.ExtendedActorSystem;
import akka.serialization.JSerializer;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class JacksonSerializer extends JSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonSerializer.class);
    private static ObjectMapper gObjectMapper;

    public JacksonSerializer(final ExtendedActorSystem ignored) {
        if (gObjectMapper == null) {
            throw new IllegalStateException("ObjectMapper not registered before instantiation.");
        }
    }

    public static void setObjectMapper(final ObjectMapper objectMapper) {
        if (gObjectMapper != null) {
            LOGGER.warn("ObjectMapper was already registered.");
        }
        gObjectMapper = objectMapper;
    }

    @Override
    public Object fromBinaryJava(final byte[] bytes, @Nullable final Class<?> manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("Cannot deserialize with null manifest");
        }
        LOGGER.info()
                .addData("manifest", manifest)
                .addData("bytes", new String(bytes, StandardCharsets.UTF_8))
                .log();
        try {
            return gObjectMapper.readValue(bytes, manifest);
        } catch (final IOException e) {
            throw new RuntimeException(String.format("Could not deserialize %s", manifest), e);
        }
    }

    @Override
    public int identifier() {
        // Can be any integer >40.
        return 564_386_063;
    }

    @Override
    public byte[] toBinary(final Object o) {
        try {
            return gObjectMapper.writeValueAsBytes(o);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(String.format("Could not serialize %s", o.getClass().getName()), e);
        }
    }

    @Override
    public boolean includeManifest() {
        return true;
    }
}
