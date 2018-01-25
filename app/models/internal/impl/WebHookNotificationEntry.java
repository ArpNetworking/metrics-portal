/**
 * Copyright 2018 Smartsheet.com
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
package models.internal.impl;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.mql.grammar.AlertTrigger;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Injector;
import models.internal.Alert;
import models.internal.NotificationEntry;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Internal modle representing a webhook notification entry.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class WebHookNotificationEntry implements NotificationEntry {
    @Override
    public CompletionStage<Void> notifyRecipient(final Alert alert, final AlertTrigger trigger, final Injector injector) {
        LOGGER.debug().setMessage("executing webhook call").addData("address", _address).log();
        final ActorSystem actorSystem = injector.getInstance(ActorSystem.class);
        final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);
        final Http http = Http.get(actorSystem);
        final ObjectNode body = mapper.createObjectNode();
        body.set("alert", mapper.valueToTree(alert));
        body.set("trigger", mapper.valueToTree(trigger));

        return http
                .singleRequest(
                        HttpRequest.POST(_address.toASCIIString())
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, body.toString())))
                .thenApply(response -> null);
    }

    @Override
    public models.view.NotificationEntry toView() {
        final models.view.WebHookNotificationEntry view = new models.view.WebHookNotificationEntry();
        view.setAddress(_address);
        return view;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_address);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WebHookNotificationEntry other = (WebHookNotificationEntry) o;
        return Objects.equals(_address, other._address);
    }

    public URI getAddress() {
        return _address;
    }

    private WebHookNotificationEntry(final Builder builder) {
        _address = builder._address;
    }

    private final URI _address;

    private static final Logger LOGGER = LoggerFactory.getLogger(WebHookNotificationEntry.class);

    /**
     * Implementation of the builder pattern for a {@link WebHookNotificationEntry}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<WebHookNotificationEntry> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(WebHookNotificationEntry::new);
        }

        /**
         * The webhook address. Required. Cannot be null or empty.
         *
         * @param value The email address.
         * @return This instance of {@link Builder}.
         */
        public Builder setAddress(final URI value) {
            _address = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private URI _address;
    }
}
