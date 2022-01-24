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

package global;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.Sink;
import com.arpnetworking.metrics.impl.ApacheHttpSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link MainModule}.

 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class MainModuleTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();

    @Test
    public void testCreateMonitoringSinksEmpty() {
        final List<Config> sinkConfigs = Collections.emptyList();
        final List<Sink> sinks = MainModule.createMonitoringSinks(sinkConfigs, OBJECT_MAPPER);
        assertTrue(sinks.isEmpty());
    }

    @Test
    public void testCreateMonitoringSinksSingle() {
        final Config sinkConfigs = ConfigFactory.parseResourcesAnySyntax(
                this.getClass().getClassLoader(),
                "global/MainModuleTest.testCreateMonitoringSinksSingle.conf");
        final List<Sink> sinks = MainModule.createMonitoringSinks(sinkConfigs.getConfigList("sinks"), OBJECT_MAPPER);
        assertEquals(1, sinks.size());
        final Sink sink = sinks.get(0);
        assertThat(sink, Matchers.instanceOf(ApacheHttpSink.class));
        assertEquals(URI.create("http://test.example.com:7090/some/path"), getField(sink, "_uri"));
    }

    @Test
    public void testCreateMonitoringSinksDoesNotExist() {
        final Config sinkConfigs = ConfigFactory.parseResourcesAnySyntax(
                this.getClass().getClassLoader(),
                "global/MainModuleTest.testCreateMonitoringSinksDoesNotExist.conf");
        try {
            MainModule.createMonitoringSinks(sinkConfigs.getConfigList("sinks"), OBJECT_MAPPER);
            // CHECKSTYLE.OFF: IllegalCatch - Need to interrogate the cause
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            assertThat(e.getCause(), Matchers.instanceOf(ClassNotFoundException.class));
        }
    }

    private static Object getField(final Object target, final String fieldName) {
        try {
            final Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
