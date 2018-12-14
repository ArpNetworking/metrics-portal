/*
 * Copyright 2016 Inscope Metrics Inc.
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
package com.arpnetworking.metrics.portal.hosts.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Client for HTTP Consul API.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class ConsulClient {

    /**
     * Calls the Consul API to getJob a list of hosts.
     *
     * @return A Promise containing a {@code List} of {@link Host}
     */
    public CompletionStage<ImmutableList<Host>> getHostList() {
        return _client
                .url(_baseUrl + "/v1/catalog/nodes" + _query.orElse(""))
                .get()
                .thenApply(this::parseWSResponse);
    }

    private ImmutableList<Host> parseWSResponse(final WSResponse response) {
        try {
            if (response.getStatus() / 100 != 2) {
                throw new IOException(
                        String.format(
                                "Non-200 response %d from Consul",
                                response.getStatus()));
            } else {
                return OBJECT_MAPPER.readValue(
                        response.getBody(),
                        HOST_LIST_RESPONSE_TYPE_REFERENCE);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ConsulClient(final Builder builder) {
        _baseUrl = builder._baseUrl;
        _client = builder._client;
        _query = Optional.ofNullable(builder._query);
    }

    private final URI _baseUrl;
    private final WSClient _client;
    private final Optional<String> _query;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final TypeReference<ImmutableList<Host>> HOST_LIST_RESPONSE_TYPE_REFERENCE =
            new TypeReference<ImmutableList<Host>>() {};

    /**
     * Implementation of the Builder pattern for ConsulClient.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
     */
    public static final class Builder extends OvalBuilder<ConsulClient> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(ConsulClient::new);
        }

        /**
         * Sets the base URL of the Consul API. Required. Cannot be null or empty.
         *
         * @param value The url.
         * @return This {@link Builder}.
         */
        public Builder setBaseUrl(final URI value) {
            _baseUrl = value;
            return this;
        }

        /**
         * Sets the query to the Consul API. Optional. Cannot be empty.
         *
         * @param value The query.
         * @return This {@link Builder}.
         */
        public Builder setQuery(final String value) {
            _query = value;
            return this;
        }

        /**
         * Sets the {@link WSClient} to use to make the calls. Required. Cannot be null.
         *
         * For example, to query nodes in a particular data center use: {@code ?dc=my-dc}
         *
         * @param value The WSClient.
         * @return this {@link Builder}
         */
        public Builder setClient(final WSClient value) {
            _client = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private URI _baseUrl;
        @NotEmpty
        private String _query;
        @NotNull
        private WSClient _client;
    }

    /**
     * Represents a host from the Consul API.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
     */
    public static final class Host {
        public String getNode() {
            return _node;
        }

        public String getAddress() {
            return _address;
        }

        private Host(final Builder builder) {
            _node = builder._node;
            _address = builder._address;
        }

        private final String _node;
        private final String _address;

        /**
         * Implementation of the builder pattern for {@link Host}.
         *
         * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
         */
        public static final class Builder extends OvalBuilder<Host> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(Host::new);
            }

            /**
             * Sets the node. Required. Cannot be null or empty.
             *
             * @param value The node.
             * @return this {@link Builder}
             */
            @JsonProperty("Node")
            public Builder setNode(final String value) {
                _node = value;
                return this;
            }

            /**
             * Sets the address. Required. Cannot be null or empty.
             *
             * @param value The address.
             * @return this {@link Builder}
             */
            @JsonProperty("Address")
            public Builder setAddress(final String value) {
                _address = value;
                return this;
            }

            @NotNull
            @NotEmpty
            private String _node;

            @NotNull
            @NotEmpty
            private String _address;
        }
    }
}
