/*
 * Copyright 2014 Brandon Arp
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
package controllers;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.health.HealthProvider;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Metrics portal generic Play controller.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
@Singleton
public final class MetaController extends Controller {

    /**
     * Public constructor.
     *
     * @param healthProvider Instace of {@link HealthProvider}.
     * @param configuration Play configuration for the app.
     */
    @Inject
    public MetaController(final HealthProvider healthProvider, final Config configuration) {
        _healthProvider = healthProvider;
        _configuration = configuration;
    }

    /**
     * Endpoint implementation to dump Play application configuration.
     *
     * @return Serialized response containing configuration.
     */
    public Result config() {
        final JsonNode node = getConfigNode(_configuration);
        return ok(node);
    }

    /**
     * Endpoint implementation to retrieve service version as JSON.
     *
     * @return Serialized response containing service version.
     */
    public Result version() {
        return ok(VERSION_JSON);
    }

    /**
     * Endpoint implementation to retrieve service health as JSON.
     *
     * @return Serialized response containing service health.
     */
    public Result ping() {
        final boolean healthy = _healthProvider.isHealthy();
        final ObjectNode result = JsonNodeFactory.instance.objectNode();
        response().setHeader(CACHE_CONTROL, "private, no-cache, no-store, must-revalidate");
        if (healthy) {
            result.put("status", HEALTHY_STATE);
            return ok(result);
        }
        result.put("status", UNHEALTHY_STATE);
        return internalServerError(result);
    }

    // TODO(vkoskela): Convert this to a JSON serializer [MAI-65]
    private static JsonNode getConfigNode(final Object element) {
        if (element instanceof Config) {
            final Config config = (Config) element;
            final ObjectNode node = JsonNodeFactory.instance.objectNode();
            for (final Map.Entry<String, ConfigValue> entry : config.entrySet()) {
                put(node, entry.getKey(), entry.getValue());
            }
            return node;
        } else if (element instanceof String) {
            final String config = (String) element;
            return JsonNodeFactory.instance.textNode(config);
        } else if (element instanceof Integer) {
            final Integer integer = (Integer) element;
            return JsonNodeFactory.instance.numberNode(integer);
        } else if (element instanceof Double) {
            final Double d = (Double) element;
            return JsonNodeFactory.instance.numberNode(d);
        } else if (element instanceof ArrayList) {
            final ArrayNode arr = JsonNodeFactory.instance.arrayNode();
            @SuppressWarnings("unchecked")
            final ArrayList<Object> list = (ArrayList<Object>) element;
            for (final Object o : list) {
                arr.add(getConfigNode(o));
            }
            return arr;
        }
        return JsonNodeFactory.instance.textNode("UNKNOWN TYPE: " + element.getClass().getCanonicalName());
    }

    // TODO(vkoskela): Convert this to a JSON serializer [MAI-65]
    private static void put(final ObjectNode node, final String remaining, final ConfigValue value) {
        final int dotIndex = remaining.indexOf('.');
        final boolean leaf = dotIndex == -1;
        if (leaf) {
            node.set(remaining, getConfigNode(value.unwrapped()));
        } else {
            final String firstChunk = remaining.substring(0, dotIndex);
            ObjectNode child = (ObjectNode) node.get(firstChunk);
            if (child == null || child.isNull()) {
                child = JsonNodeFactory.instance.objectNode();
                node.set(firstChunk, child);
            }
            put(child, remaining.substring(dotIndex + 1), value);
        }
    }

    private final HealthProvider _healthProvider;
    private final Config _configuration;

    private static final Logger LOGGER = LoggerFactory.getLogger(MetaController.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final String UNHEALTHY_STATE = "UNHEALTHY";
    private static final String HEALTHY_STATE = "HEALTHY";
    private static final JsonNode VERSION_JSON;

    static {
        JsonNode versionJson = JsonNodeFactory.instance.objectNode();
        try {
            versionJson = OBJECT_MAPPER.readTree(
                    Resources.toString(
                            Resources.getResource("version.json"),
                            Charsets.UTF_8));
            // CHECKSTYLE.OFF: IllegalCatch - Prevent program shutdown
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error("Resource load failure; resource=version.json", e);
        }
        VERSION_JSON = versionJson;
    }
}
