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
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import play.libs.ws.WSClient;

import java.util.concurrent.CompletionStage;

public class SlackAlertNotifier implements AlertNotifier {
    private SlackAlertNotifier(final Builder builder) {
        _apiKey = builder._apiKey;
        _messagePostUrl = builder._messagePostUrl;
        _wsClient = builder._wsClient;
    }

    @Override
    public CompletionStage<Void> notify(Alert alert, AlertEvaluationResult result, String message) {
        return null;
    }

    private final String _apiKey;
    private final String _messagePostUrl;
    private final WSClient _wsClient;

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

        @NotNull
        @NotEmpty
        private String _apiKey;
        @NotNull
        @NotEmpty
        private String _messagePostUrl;
        @NotNull
        @JacksonInject
        private WSClient _wsClient;
    }
}
