/*
 * Copyright 2023 Inscope Metrics Inc
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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.alerts.AlertNotifier;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import play.libs.Json;
import play.libs.ws.WSClient;
import scala.collection.mutable.StringBuilder;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * An {@link AlertNotifier} that sends messages to Slack.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public final class SlackAlertNotifier implements AlertNotifier {
    private SlackAlertNotifier(final Builder builder) {
        _apiKey = builder._apiKey;
        _messagePostUrl = builder._messagePostUrl;
        _wsClient = builder._wsClient;
        _defaultChannelId = builder._defaultChannelId;
        _channelIdMap = builder._channelIdMap;
    }

    @Override
    public CompletionStage<Void> notify(final Alert alert, final AlertEvaluationResult result) {
        final String channelId = Optional.ofNullable(_channelIdMap.get(alert.getName())).orElse(_defaultChannelId);
        final StringBuilder message = new StringBuilder();
        message.append(String.format(
                "%s is in alarm%n%n"
                        + "%s%n%n"
                        + "from %s to %s.%n%n",
                alert.getName(),
                alert.getDescription(),
                DateTimeFormatter.RFC_1123_DATE_TIME.format(result.getQueryStartTime().atZone(ZoneOffset.UTC)),
                DateTimeFormatter.RFC_1123_DATE_TIME.format(result.getQueryEndTime().atZone(ZoneOffset.UTC))
                ));

        message.append(String.format("Firing tags:%n"));
        message.append(String.format("----------------%n"));
        for (final ImmutableMap<String, String> tag : result.getFiringTags()) {
            for (final Map.Entry<String, String> entry : tag.entrySet()) {
                message.append(String.format(
                        "%s: %s%n%n",
                        entry.getKey(),
                        entry.getValue()));
            }
            message.append(String.format("----------------%n"));
        }

        final ObjectNode object = Json.newObject()
            .put("channel", channelId)
            .put("text", message.toString());

        return _wsClient
                .url(_messagePostUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", String.format("Bearer %s", _apiKey))
                .post(object)
                .thenApply(response -> {
                    if (response.getStatus() / 100 != 2) {
                        throw new RuntimeException(new IOException(
                                String.format(
                                        "Non-200 response %d from SlackAlertNotifier",
                                        response.getStatus())));
                    }
                    return null;
                });
    }

    private final String _apiKey;
    private final String _messagePostUrl;
    private final WSClient _wsClient;
    private final String _defaultChannelId;
    private final Map<String, String> _channelIdMap;

    /**
     * Builder for instances of {@link SlackAlertNotifier}.
     */
    public static class Builder extends OvalBuilder<SlackAlertNotifier> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(SlackAlertNotifier::new);
        }


        /**
         * Sets the Slack API key. Cannot be null or empty.
         *
         * @param apiKey the Slack API key
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setApiKey(final String apiKey) {
            _apiKey = apiKey;
            return this;
        }


        /**
         * Sets the URL to post messages to. Cannot be null or empty.
         *
         * @param messagePostUrl the url to post the messages to
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setMessagePostUrl(final String messagePostUrl) {
            _messagePostUrl = messagePostUrl;
            return this;
        }

        /**
         * Sets the default channel id to send messages to. Cannot be null or empty.
         *
         * @param defaultChannelId the default channel id to post the messages to
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setDefaultChannelId(final String defaultChannelId) {
            _defaultChannelId = defaultChannelId;
            return this;
        }

        /**
         * Sets the map of alert name to channel id. Optional.
         *
         * @param channelIdMap the map of alert name to channel id
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setChannelIdMap(final Map<String, String> channelIdMap) {
            _channelIdMap = channelIdMap;
            return this;
        }

        /**
         * Sets the {@link WSClient} to use. Required. Cannot be null.
         *
         * @param wsClient the {@link WSClient}
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setWSClient(final WSClient wsClient) {
            _wsClient = wsClient;
            return this;
        }

        @NotNull(message = "Must have an API key")
        @NotEmpty(message = "API key must not be empty")
        private String _apiKey;
        @NotNull(message = "Must have a message post URL")
        @NotEmpty(message = "Message post URL must not be empty")
        private String _messagePostUrl;
        @NotNull
        @JacksonInject
        private WSClient _wsClient;
        @NotNull(message = "Must have a default channel id")
        @NotEmpty(message = "Default channel id must not be empty")
        private String _defaultChannelId;
        @NotNull(message = "Channel id map must not be null")
        private Map<String, String> _channelIdMap = Maps.newHashMap();
    }
}
