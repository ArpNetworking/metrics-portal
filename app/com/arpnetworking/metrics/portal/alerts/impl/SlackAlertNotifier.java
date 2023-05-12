package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.commons.builder.Builder;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.alerts.AlertNotifier;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class SlackAlertNotifier implements AlertNotifier {
    private SlackAlertNotifier(final Builder builder) {
        _apiKey = builder._apiKey;
        _messagePostUrl = builder._messagePostUrl;
    }

    @Override
    public CompletionStage<Void> notify(Alert alert, AlertEvaluationResult result, String message) {
        return null;
    }

    private final String _apiKey;
    private final String _messagePostUrl;

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
    }
}
