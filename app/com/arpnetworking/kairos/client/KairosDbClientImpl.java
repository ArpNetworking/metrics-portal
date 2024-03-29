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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.kairos.client.models.MetricDataPoints;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.kairos.service.KairosDbServiceImpl;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.steno.LogBuilder;
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
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.AcceptEncoding;
import org.apache.pekko.http.javadsl.model.headers.HttpEncoding;
import org.apache.pekko.http.javadsl.model.headers.HttpEncodings;
import org.apache.pekko.http.scaladsl.coding.Coder;
import org.apache.pekko.http.scaladsl.coding.Coders;
import org.apache.pekko.stream.Materializer;
import scala.concurrent.duration.FiniteDuration;
import scala.jdk.javaapi.FutureConverters;

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
        final Metrics metrics = _metricsFactory.create();
        final UUID queryUuid = UUID.randomUUID();
        final JsonNode queryJson = _mapper.valueToTree(query);
        LOGGER.trace()
                .setMessage("starting queryMetrics")
                .addData("queryUuid", queryUuid)
                .addData("query", queryJson)
                .log();
        final HttpRequest request = HttpRequest.POST(createUri(METRICS_QUERY_PATH).toString())
                .withEntity(ContentTypes.APPLICATION_JSON, queryJson.toString());
        final Instant startTime = Instant.now();
        return fireRequest(request, MetricsQueryResponse.class)
                .whenComplete((response, error) -> {
                    final LogBuilder logBuilder = LOGGER.trace()
                            .setMessage("finished queryMetrics")
                            .addData("queryUuid", queryUuid)
                            .addData("query", queryJson)
                            .addData("duration", Duration.between(startTime, Instant.now()));
                    if (error != null) {
                        logBuilder.setThrowable(error);
                    }
                    logBuilder.log();
                    metrics.incrementCounter("kairosClient/queryMetrics/success", error == null ? 1 : 0);
                    metrics.close();
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
        final Instant startTime = Instant.now();
        return _http.singleRequest(request.addHeader(AcceptEncoding.create(HttpEncodings.GZIP)))
                .thenCompose(httpResponse -> {
                    final HttpEncoding encoding = httpResponse.encoding();
                    final Coder flow;
                    if (HttpEncodings.GZIP.equals(encoding)) {
                        flow = Coders.Gzip();
                    } else if (HttpEncodings.DEFLATE.equals(encoding)) {
                        flow = Coders.Deflate();
                    } else {
                        flow = Coders.NoCoding();
                    }
                    if (!httpResponse.status().isSuccess()) {
                        return httpResponse.entity().toStrict(_readTimeout.toMillis(), _materializer)
                                .thenCompose(strict -> FutureConverters.asJava(flow.decode(strict.getData(), _materializer)))
                                .thenApply(materializedBody -> {
                                    final String responseBody = materializedBody.utf8String();
                                    if (responseBody.isEmpty()) {
                                        throw new KairosDbRequestException(
                                                httpResponse.status().intValue(),
                                                httpResponse.status().reason(),
                                                URI.create(request.getUri().toString()),
                                                Duration.between(startTime, Instant.now()));
                                    }
                                    throw new KairosDbRequestException(
                                            responseBody,
                                            httpResponse.status().intValue(),
                                            httpResponse.status().reason(),
                                            URI.create(request.getUri().toString()),
                                            Duration.between(startTime, Instant.now()));
                                });
                    }
                    return httpResponse.entity()
                            .toStrict(_readTimeout.toMillis(), _materializer)
                            .thenCompose(strict -> FutureConverters.asJava(flow.decode(strict.getData(), _materializer)));
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
        _materializer = Materializer.createMaterializer(actorSystem);
        _readTimeout = builder._readTimeout;
        _metricsFactory = builder._metricsFactory;
    }

    private final ObjectMapper _mapper;
    private final Http _http;
    private final Materializer _materializer;
    private final URI _uri;
    private final FiniteDuration _readTimeout;
    private final MetricsFactory _metricsFactory;

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

        /**
         * Sets the {@link MetricsFactory} to use. Cannot be null.
         *
         * @param value the {@link MetricsFactory} to use
         * @return this {@link KairosDbServiceImpl.Builder}
         */
        public Builder setMetricsFactory(final MetricsFactory value) {
            _metricsFactory = value;
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
        @NotNull
        private MetricsFactory _metricsFactory;
    }
}
