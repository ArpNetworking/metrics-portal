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

package com.arpnetworking.notcommons.akka;

import akka.actor.ExtendedActorSystem;
import akka.serialization.JSerializer;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;

/**
 * Serializer for Akka using Jackson.
 * <br>
 * This should be replaced with akka-jackson-serialization once this project is
 * is on Scala 2.12+ and Akka 2.6+.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class JacksonSerializer extends JSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonSerializer.class);
    private static @Nullable ObjectMapper gObjectMapper;

    /**
     * Constructor used by Akka upon system initialization.
     *
     * @param ignored the actor system
     */
    public JacksonSerializer(final ExtendedActorSystem ignored) {}

    /**
     * Set the object mapper to be used by all instances.
     * <br>
     * This should only be called once, before initialization. This method exists
     * because Akka does not provide any initialization hooks for serializers outside
     * of passing the configuration object.
     * <br>
     * Since we don't want to define the ObjectMapper twice (once in Guice, the other in the
     * configuration) we use this hack instead.
     *
     * @param objectMapper the ObjectMapper to use.
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_STATIC_REP2", justification = "Must take an object mapper")
    public static void setObjectMapper(final ObjectMapper objectMapper) {
        if (gObjectMapper != null) {
            LOGGER.warn("ObjectMapper was already registered.");
        }
        gObjectMapper = objectMapper;
    }


    @Override
    public Object fromBinaryJava(final byte[] bytes, final Class<?> manifest) {
        Preconditions.checkNotNull(manifest, "Jackson deserialization requires a manifest.");
        Preconditions.checkNotNull(gObjectMapper, "The mapper was not configured at startup.");
        try {
            return gObjectMapper.readValue(bytes, manifest);
        } catch (final IOException e) {
            throw new RuntimeException(String.format("Could not deserialize %s", manifest), e);
        }
    }

    @Override
    public int identifier() {
        // Akka allows for this to be any integer >40.
        // Randomly generated from IDE.
        return 564_386_063;
    }

    @Override
    public byte[] toBinary(final Object o) {
        Preconditions.checkNotNull(gObjectMapper, "The mapper was not configured at startup.");
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
