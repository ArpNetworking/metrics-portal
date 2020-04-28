/*
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
import com.arpnetworking.kairos.client.models.MetricDataPoints;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.collect.ImmutableList;
import net.sf.oval.constraint.NotNull;
import scala.compat.java8.FutureConverters;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Client for accessing KairosDB APIs.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class KairosDbClientImpl implements KairosDbClient {
    @Override
    public CompletionStage<MetricsQueryResponse> queryMetrics(final MetricsQuery query) {
        final UUID queryUuid = UUID.randomUUID();
        final JsonNode queryJson = _mapper.valueToTree(query);
        LOGGER.debug()
                .setMessage("starting queryMetrics")
                .addData("queryUuid", queryUuid)
                .addData("query", queryJson)
                .log();
        final HttpRequest request = HttpRequest.POST(createUri(METRICS_QUERY_PATH).toString())
                .withEntity(ContentTypes.APPLICATION_JSON, queryJson.toString());
        final Instant startTime = Instant.now();
        return fireRequest(request, MetricsQueryResponse.class)
                .whenComplete((response, error) -> {
                    LOGGER.debug()
                            .setMessage("finished queryMetrics")
                            .addData("queryUuid", queryUuid)
                            .addData("query", queryJson)
                            .addData("duration", Duration.between(startTime, Instant.now()))
                            .addData("success", error == null)
                            .log();
                });
    }

    @Override
    public CompletionStage<MetricNamesResponse> queryMetricNames() {
        final HttpRequest request = HttpRequest.GET(createUri(METRICS_NAMES_PATH).toString());
        return fireRequest(request, MetricNamesResponse.class);
    }

    @Override
    public CompletionStage<MetricsQueryResponse> queryMetricTags(final TagsQuery query) {
        // The documentation for KairosDb incorrectly shows the response for
        // querying metric tags as differing from the response for querying
        // metrics. However, adhoc testing against KairosDb shows that the
        // responses for the two endpoints are indeed the same.
        //
        // Ref:
        // https://kairosdb.github.io/docs/build/html/restapi/QueryMetricTags.html
        // https://kairosdb.github.io/docs/build/html/restapi/QueryMetrics.html
        try {
            final HttpRequest request = HttpRequest.POST(createUri(TAGS_QUERY_PATH).toString())
                    .withEntity(ContentTypes.APPLICATION_JSON, _mapper.writeValueAsString(query));
            return fireRequest(request, MetricsQueryResponse.class);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletionStage<TagNamesResponse> listTagNames() {
        final HttpRequest request = HttpRequest.GET(createUri(LIST_TAG_NAMES_PATH).toString());
        return fireRequest(request, TagNamesResponse.class);
    }

    @Override
    public CompletionStage<Void> addDataPoints(final ImmutableList<MetricDataPoints> metricDataPoints) {
        try {
            final HttpRequest request = HttpRequest.POST(createUri(ADD_DATA_POINTS_PATH).toString())
                    .withEntity(ContentTypes.APPLICATION_JSON, _mapper.writeValueAsString(metricDataPoints));
            return fireRequest(request, Void.class);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> CompletionStage<T> fireRequest(final HttpRequest request, final Class<T> responseType) {
        return fireRequest(request, TypeFactory.defaultInstance().constructType(responseType));
    }

    private <T> CompletionStage<T> fireRequest(final HttpRequest request, final JavaType responseType) {
        return _http.singleRequest(request.addHeader(AcceptEncoding.create(HttpEncodings.GZIP)), _materializer)
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
                    if (!httpResponse.status().isSuccess()) {
                        return httpResponse.entity().toStrict(_readTimeout.toMillis(), _materializer)
                                .thenCompose(strict -> FutureConverters.toJava(flow.decode(strict.getData(), _materializer)))
                                .thenApply(materializedBody -> {
                                    final String responseBody = materializedBody.utf8String();
                                    if (responseBody.isEmpty()) {
                                        throw new KairosDbRequestException(
                                                httpResponse.status().intValue(),
                                                httpResponse.status().reason(),
                                                URI.create(request.getUri().toString()));
                                    }
                                    throw new KairosDbRequestException(
                                            responseBody,
                                            httpResponse.status().intValue(),
                                            httpResponse.status().reason(),
                                            URI.create(request.getUri().toString()));
                                });
                    }
                    return httpResponse.entity()
                            .toStrict(_readTimeout.toMillis(), _materializer)
                            .thenCompose(strict -> FutureConverters.toJava(flow.decode(strict.getData(), _materializer)));
                })
                .thenApply(body -> {
                    try {
                        if (body.size() > 0) {
                            return _mapper.readValue(body.utf8String(), responseType);
                        } else {
                            return null;
                        }
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private URI createUri(final URI relativePath) {
        return _uri.resolve(relativePath);
    }

    private KairosDbClientImpl(final Builder builder) {
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

    static final URI METRICS_QUERY_PATH = URI.create("/api/v1/datapoints/query");
    static final URI METRICS_NAMES_PATH = URI.create("/api/v1/metricnames");
    static final URI TAGS_QUERY_PATH = URI.create("/api/v1/datapoints/query/tags");
    static final URI LIST_TAG_NAMES_PATH = URI.create("/api/v1/tagnames");
    static final URI ADD_DATA_POINTS_PATH = URI.create("/api/v1/datapoints");
    private static final Logger LOGGER = LoggerFactory.getLogger(KairosDbClientImpl.class);

    /**
     * Implementation of the builder pattern for {@link KairosDbClientImpl}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<KairosDbClientImpl> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(KairosDbClientImpl::new);
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
