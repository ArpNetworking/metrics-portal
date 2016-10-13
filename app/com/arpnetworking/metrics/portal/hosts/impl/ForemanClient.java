/**
 * Copyright 2016 Smartsheet.com
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Client for HTTP Foreman API.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class ForemanClient {
    /**
     * Calls the Foreman API to get a page of hosts.
     *
     * @param page The page number to start at
     * @return A Promise containing a {@link HostPageResponse}
     */
    public CompletionStage<HostPageResponse> getHostPage(final int page) {
        return getHostPage(page, 250);
    }

    /**
     * Calls the Foreman API to get a page of hosts.
     *
     * @param page The page number to start at
     * @param perPage The number of hosts per page
     * @return A Promise containing a {@link HostPageResponse}
     */
    public CompletionStage<HostPageResponse> getHostPage(final int page, final int perPage) {
            return _client
                    .url(_baseUrl + String.format("/api/hosts?page=%d&per_page=%d", page, perPage))
                    .get()
                    .thenApply(this::parseWSResponse);
    }

    private HostPageResponse parseWSResponse(final WSResponse page) {
        try {
            if (page.getStatus() / 100 != 2) {
                throw new IllegalArgumentException(
                        String.format(
                                "Non-200 response %d from Consul",
                                page.getStatus()));
            } else {
                final JsonNode jsonNode = OBJECT_MAPPER.readTree(page.getBody());
                if (jsonNode.isObject()) {
                    return OBJECT_MAPPER.treeToValue(jsonNode, HostPageResponse.class);
                } else if (jsonNode.isArray()) {
                    // NOTE: treeToValue does not support TypeReference
                    // See: https://github.com/FasterXML/jackson-databind/issues/1294
                    final ImmutableList<ForemanHostContainer> foremanHostContainers = OBJECT_MAPPER.readValue(
                            OBJECT_MAPPER.treeAsTokens(jsonNode),
                            OBJECT_MAPPER.getTypeFactory().constructType(FOREMAN_HOST_CONTAINER_LIST_TYPE_REFERENCE));
                    // NOTE: Guava does not yet have Collectors for its immutable variants
                    // See: https://github.com/google/guava/issues/1582
                    final ImmutableList<ForemanHost> foremanHosts = ImmutableList.copyOf(
                            foremanHostContainers
                                    .stream()
                                    .map(foremanHostContainer -> foremanHostContainer.getHost())
                                    .collect(Collectors.toList()));
                    return new HostPageResponse.Builder()
                            .setResults(foremanHosts)
                            .setPage(1)
                            .setPerPage(foremanHosts.size())
                            .setSubtotal(foremanHosts.size())
                            .setTotal(foremanHosts.size())
                            .build();
                }
                throw new IllegalArgumentException("Unsupported response format");
            }
        } catch (final IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private ForemanClient(final Builder builder) {
        _baseUrl = builder._baseUrl;
        _client = builder._client;
    }

    private final URI _baseUrl;
    private final WSClient _client;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final TypeReference<ImmutableList<ForemanHostContainer>> FOREMAN_HOST_CONTAINER_LIST_TYPE_REFERENCE =
            new TypeReference<ImmutableList<ForemanHostContainer>>() {};

    /**
     * Implementation of the Builder pattern for ForemanClient.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<ForemanClient> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(ForemanClient::new);
        }

        /**
         * Sets the base URL of the Foreman API. Required. Cannot be null. Cannot be empty.
         *
         * @param value The url.
         * @return This {@link Builder}.
         */
        public Builder setBaseUrl(final URI value) {
            _baseUrl = value;
            return this;
        }

        /**
         * Sets the {@link play.libs.ws.WSClient} to use to make the calls. Required. Cannot be null.
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
        @NotNull
        private WSClient _client;
    }

    /**
     * Represents a listing of hosts from the Foreman API.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class HostPageResponse {
        public int getTotal() {
            return _total;
        }

        public int getSubtotal() {
            return _subtotal;
        }

        public int getPage() {
            return _page;
        }

        public int getPerPage() {
            return _perPage;
        }

        public ImmutableList<ForemanHost> getResults() {
            return _results;
        }

        private HostPageResponse(final Builder builder) {
            _total = builder._total;
            _subtotal = builder._subtotal;
            _page = builder._page;
            _perPage = builder._perPage;
            _results = builder._results;
        }

        private final int _total;
        private final int _subtotal;
        private final int _page;
        private final int _perPage;
        private final ImmutableList<ForemanHost> _results;

        /**
         * Implementation of the builder pattern for {@link HostPageResponse}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends OvalBuilder<HostPageResponse> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(HostPageResponse::new);
            }

            /**
             * Sets the total number of hosts.  Required. Cannot be null.
             *
             * @param value The total number of hosts
             * @return this {@link Builder}
             */
            public Builder setTotal(final Integer value) {
                _total = value;
                return this;
            }

            /**
             * Sets the Subtotal.  Required. Cannot be null.
             *
             * @param value The subtotal
             * @return this {@link Builder}
             */
            public Builder setSubtotal(final Integer value) {
                _subtotal = value;
                return this;
            }

            /**
             * Sets the current page.  Required. Cannot be null.
             *
             * @param value The current page number (1-based)
             * @return this {@link Builder}
             */
            public Builder setPage(final Integer value) {
                _page = value;
                return this;
            }

            /**
             * Sets the number of hosts per page.  Required. Cannot be null.
             *
             * @param value Number of hosts per page
             * @return this {@link Builder}
             */
            @JsonProperty("per_page")
            public Builder setPerPage(final Integer value) {
                _perPage = value;
                return this;
            }

            /**
             * Sets the list of hosts.  Required. Cannot be null.
             *
             * @param value List of hosts
             * @return this {@link Builder}
             */
            public Builder setResults(final ImmutableList<ForemanHost> value) {
                _results = value;
                return this;
            }

            @NotNull
            private Integer _total;

            @NotNull
            private Integer _subtotal;

            @NotNull
            private Integer _page;

            @NotNull
            private Integer _perPage;

            @NotNull
            private ImmutableList<ForemanHost> _results;
        }
    }

    /**
     * Represents a Host from the ForemanAPI.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class ForemanHost {
        public String getName() {
            return _name;
        }

        private ForemanHost(final Builder builder) {
            _name = builder._name;
        }

        private final String _name;

        /**
         * Implementation of the builder pattern for {@link ForemanHost}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends OvalBuilder<ForemanHost> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(ForemanHost::new);
            }

            /**
             * Sets the name of the host. Required. Cannot be null.  Cannot be empty.
             *
             * @param value The name of the host
             * @return this {@link Builder}
             */
            public Builder setName(final String value) {
                _name = value;
                return this;
            }

            @NotNull
            @NotEmpty
            private String _name;
        }
    }

    /**
     * Wraps a ForemanHost from the ForemanAPI. This was used in at least version 1.8.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
     */
    public static final class ForemanHostContainer {
        public ForemanHost getHost() {
            return _host;
        }

        private ForemanHostContainer(final Builder builder) {
            _host = builder._host;
        }

        private final ForemanHost _host;

        /**
         * Implementation of the builder pattern for {@link ForemanHostContainer}.
         *
         * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
         */
        public static final class Builder extends OvalBuilder<ForemanHostContainer> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(ForemanHostContainer::new);
            }

            /**
             * Sets the ForemanHost instance. Required. Cannot be null.
             *
             * @param value The {@code ForemanHost} instance
             * @return this {@link Builder}
             */
            public Builder setHost(final ForemanHost value) {
                _host = value;
                return this;
            }

            @NotNull
            private ForemanHost _host;
        }
    }
}
