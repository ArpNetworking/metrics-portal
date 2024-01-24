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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import com.arpnetworking.metrics.portal.health.HealthProvider;
import com.arpnetworking.metrics.portal.health.StatusActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import models.view.StatusResponse;
import models.view.VersionInfo;
import play.mvc.Controller;
import play.mvc.Result;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
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
     * @param objectMapper Instance of {@code ObjectMapper}.
     * @param actorSystem Instance of Akka {@code ActorSystem}.
     * @param healthProvider Instance of {@link HealthProvider}.
     * @param configuration Play configuration for the app.
     * @param statusActor Reference to Akka {@link StatusActor}.
     */
    @Inject
    public MetaController(
            final ObjectMapper objectMapper,
            final ActorSystem actorSystem,
            final HealthProvider healthProvider,
            final Config configuration,
            @Named("status") final ActorRef statusActor) {
        _objectMapper = objectMapper;
        _actorSystem = actorSystem;
        _healthProvider = healthProvider;
        _configuration = configuration;
        _statusActor = statusActor;
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
     * Endpoint implementation to retrieve service health as JSON.
     *
     * @return Serialized response containing service health.
     */
    public Result ping() {
        final boolean healthy = _healthProvider.isHealthy();
        final ObjectNode result = JsonNodeFactory.instance.objectNode();
        if (healthy) {
            result.put("status", HEALTHY_STATE);
            return ok(result).withHeader(CACHE_CONTROL, "private, no-cache, no-store, must-revalidate");
        }
        result.put("status", UNHEALTHY_STATE);
        return internalServerError(result).withHeader(CACHE_CONTROL, "private, no-cache, no-store, must-revalidate");
    }

    /**
     * Endpoint implementation to retrieve service status as JSON.
     *
     * @return Serialized response containing service status.
     */
    public CompletionStage<Result> status() {
        return Patterns.ask(
                _statusActor,
                new StatusActor.StatusRequest(),
                Duration.ofSeconds(1))
                .exceptionally(t -> new StatusResponse.Builder()
                        .setLocalAddress(_actorSystem.provider().getDefaultAddress())
                        .build())
                .thenApply(status -> ok(_objectMapper.<JsonNode>valueToTree(status)));

    }

    /**
     * Endpoint implementation to retrieve service version as JSON.
     *
     * @return Serialized response containing service version.
     */
    public Result version() {
        return ok(_objectMapper.<JsonNode>valueToTree(VersionInfo.getInstance()));
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

    private final ObjectMapper _objectMapper;
    private final ActorSystem _actorSystem;
    private final HealthProvider _healthProvider;
    private final Config _configuration;
    private final ActorRef _statusActor;

    private static final String UNHEALTHY_STATE = "UNHEALTHY";
    private static final String HEALTHY_STATE = "HEALTHY";
}
