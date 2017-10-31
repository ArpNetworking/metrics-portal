/**
 * Copyright 2017 Smartsheet
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
package com.arpnetworking.kairos.client;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.AcceptEncoding;
import akka.http.javadsl.model.headers.HttpEncoding;
import akka.http.javadsl.model.headers.HttpEncodings;
import akka.http.scaladsl.coding.Coder;
import akka.http.scaladsl.coding.Deflate$;
import akka.http.scaladsl.coding.Gzip$;
import akka.http.scaladsl.coding.NoCoding$;
import akka.stream.ActorMaterializer;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.oval.constraint.NotNull;
import scala.compat.java8.FutureConverters;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Client for accessing KairosDB APIs.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class KairosDbClient {
    /**
     * Executes a query for datapoints from  KairosDB.
     *
     * @param query the query
     * @return the response
     */
    public CompletionStage<MetricsQueryResponse> queryMetrics(final MetricsQuery query) {
        try {
            final HttpRequest request = HttpRequest.POST(createUri(METRICS_QUERY_PATH).toString())
                    .withEntity(ContentTypes.APPLICATION_JSON, _mapper.writeValueAsString(query));
            return fireRequest(request, MetricsQueryResponse.class);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Queries KairosDB for metric names.
     *
     * @return the response
     */
    public CompletionStage<KairosMetricNamesQueryResponse> queryMetricNames() {
        final HttpRequest request = HttpRequest.GET(createUri(METRICS_NAMES_PATH).toString());
        return fireRequest(request, KairosMetricNamesQueryResponse.class);
    }

    private <T> CompletionStage<T> fireRequest(final HttpRequest request, final Class<T> responseType) {
        return _http.singleRequest(request.addHeader(AcceptEncoding.create(HttpEncodings.GZIP)))
                .thenCompose(httpResponse -> {
                    final HttpEncoding encoding = httpResponse.encoding();
                    final Coder flow;
                    if (HttpEncodings.GZIP.equals(encoding)) {
                        flow = Gzip$.MODULE$;
                    } else if (HttpEncodings.DEFLATE.equals(encoding)) {
                        flow = Deflate$.MODULE$;
                    } else {
                        flow = NoCoding$.MODULE$;
                    }
                    return httpResponse.entity()
                            .toStrict(_readTimeout.toMillis(), _materializer)
                            .thenCompose(strict -> FutureConverters.toJava(flow.decode(strict.getData(), _materializer)));
                })
                .thenApply(body -> {
                    try {
                        return _mapper.readValue(body.utf8String(), responseType);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private URI createUri(final URI relativePath) {
        return _uri.resolve(relativePath);
    }

    private KairosDbClient(final Builder builder) {
        final ActorSystem actorSystem = builder._actorSystem;
        _mapper = builder._mapper;
        _uri = builder._uri;

        _http = Http.get(actorSystem);
        _materializer = ActorMaterializer.create(actorSystem);
        _readTimeout = builder._readTimeout;
    }

    private final ObjectMapper _mapper;
    private final Http _http;
    private final ActorMaterializer _materializer;
    private final URI _uri;
    private final FiniteDuration _readTimeout;

    private static final URI METRICS_QUERY_PATH = URI.create("/api/v1/datapoints/query");
    private static final URI METRICS_NAMES_PATH = URI.create("/api/v1/metricnames");

    /**
     * Implementation of the builder pattern for {@link KairosDbClient}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<KairosDbClient> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(KairosDbClient::new);
        }

        /**
         * Sets the actor system to perform operations on.
         *
         * @param value the actor system
         * @return this Builder
         */
        public Builder setActorSystem(final ActorSystem value) {
            _actorSystem = value;
            return this;
        }

        /**
         * Sets the object mapper to use for serialization.
         *
         * @param value the object mapper
         * @return this Builder
         */
        public Builder setMapper(final ObjectMapper value) {
            _mapper = value;
            return this;
        }

        /**
         * Sets the base URI.
         *
         * @param value the base URI
         * @return this Builder
         */
        public Builder setUri(final URI value) {
            _uri = value;
            return this;
        }

        /**
         * Sets the read timeout. Optional. Defaults to 1 hour.
         *
         * @param value the read timeout
         * @return this Builder
         */
        public Builder setReadTimeout(final FiniteDuration value) {
            _readTimeout = value;
            return this;
        }

        @NotNull
        @JacksonInject
        private ActorSystem _actorSystem;
        @NotNull
        @JacksonInject
        private ObjectMapper _mapper;
        @NotNull
        private URI _uri;
        @NotNull
        private FiniteDuration _readTimeout = FiniteDuration.apply(1, TimeUnit.HOURS);
    }
}
