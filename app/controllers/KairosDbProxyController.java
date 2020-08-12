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
package controllers;

import akka.stream.javadsl.StreamConverters;
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.kairos.service.KairosDbService;
import com.arpnetworking.play.ProxyClient;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import play.http.HttpEntity;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.AsyncCompletionHandler;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.HttpResponseHeaders;
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus;
import play.shaded.ahc.org.asynchttpclient.Response;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * KairosDb proxy controller.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
@Singleton
public class KairosDbProxyController extends Controller {
    /**
     * Public constructor.
     * @param configuration Play configuration to configure the proxy
     * @param client ws client to use
     * @param mapper ObjectMapper to use for JSON serialization
     * @param kairosService The KairosDb service to proxy requests to
     */
    @Inject
    public KairosDbProxyController(
            final Config configuration,
            final WSClient client,
            final ObjectMapper mapper,
            final KairosDbService kairosService) {
        final URI kairosURL = URI.create(configuration.getString("kairosdb.uri"));
        _client = new ProxyClient(kairosURL, client);
        _mapper = mapper;
        _filterRollups = configuration.getBoolean("kairosdb.proxy.filterRollups");
        _requireAggregators = configuration.getBoolean("kairosdb.proxy.requireAggregators");
        _addMergeAggregator = configuration.getBoolean("kairosdb.proxy.addMergeAggregator");

        final ImmutableSet<String> excludedTagNames = ImmutableSet.copyOf(
                configuration.getStringList("kairosdb.proxy.excludedTagNames"));

        _kairosService = kairosService;
    }

    /**
     * Proxied status call.
     *
     * @return Proxied status response.
     */
    public CompletionStage<Result> status() {
        return proxy();
    }

    /**
     * Proxied healthcheck call.
     *
     * @return Proxied health check response.
     */
    public CompletionStage<Result> healthCheck() {
        return proxy();
    }

    /**
     * Proxied tagNames call.
     *
     * @return Proxied tagNames response.
     */
    public CompletionStage<Result> tagNames() {
        return _kairosService.listTagNames()
                .<JsonNode>thenApply(_mapper::valueToTree)
                .thenApply(Results::ok);
    }

    /**
     * Proxied tagValues call.
     *
     * @return Proxied tagValues response.
     */
    public CompletionStage<Result> tagValues() {
        return proxy();
    }

    private static CompletionStage<Result> noJsonFoundResponse() {
        return CompletableFuture.completedFuture(Results.badRequest(
                "no JSON found in request; did you remember to set Content-Type: application/json "
                        + "in the HTTP header?"));
    }

    /**
     * Proxied queryTags call.
     *
     * @return Proxied queryTags response.
     */
    public CompletionStage<Result> queryTags() {
        try {
            final JsonNode jsonBody = request().body().asJson();
            if (jsonBody == null) {
                return noJsonFoundResponse();
            }

            final TagsQuery metricsQuery = _mapper.treeToValue(jsonBody, TagsQuery.class);
            return _kairosService.queryMetricTags(metricsQuery)
                    .<JsonNode>thenApply(_mapper::valueToTree)
                    .thenApply(Results::ok);
        } catch (final IOException e) {
            return CompletableFuture.completedFuture(Results.internalServerError(e.getMessage()));
        }
    }

    /**
     * Proxied queryMetrics call.
     *
     * @return Proxied queryMetrics response.
     */
    public CompletionStage<Result> queryMetrics() {
        try {
            final JsonNode jsonBody = request().body().asJson();
            if (jsonBody == null) {
                return noJsonFoundResponse();
            }

            MetricsQuery metricsQuery = _mapper.treeToValue(jsonBody, MetricsQuery.class);
            if (_requireAggregators
                    && metricsQuery.getMetrics().stream().anyMatch(metric -> metric.getAggregators().isEmpty())) {
                return CompletableFuture.completedFuture(
                        Results.badRequest("All queried metrics must have at least one aggregator"));
            }

            if (_addMergeAggregator) {
                metricsQuery = checkAndAddMergeAggregator(metricsQuery);
            }

            return _kairosService.queryMetrics(metricsQuery)
                    .<JsonNode>thenApply(_mapper::valueToTree)
                    .thenApply(Results::ok);
        } catch (final IOException e) {
            return CompletableFuture.completedFuture(Results.internalServerError(e.getMessage()));
        }
    }

    /* package private */ MetricsQuery checkAndAddMergeAggregator(final MetricsQuery metricsQuery) {
        final List<Metric> newMetrics = new ArrayList<>();
        for (final Metric metric : metricsQuery.getMetrics()) {
            if (needMergeAggregator(metric.getAggregators())) {
                final List<Aggregator> newAggregators = new ArrayList<>();
                final Optional<Aggregator> aggregatorWithSampling = metric.getAggregators().stream().filter(
                        aggregator -> aggregator.getSampling().isPresent()).findFirst();
                newAggregators.add(aggregatorWithSampling.map(
                        aggregator -> ThreadLocalBuilder.build(Aggregator.Builder.class, b -> {
                            b.setName("merge");
                            aggregator.getSampling().ifPresent(b::setSampling);
                            aggregator.getAlignStartTime().ifPresent(b::setAlignStartTime);
                            aggregator.getAlignSampling().ifPresent(b::setAlignSampling);
                            aggregator.getAlignEndTime().ifPresent(b::setAlignEndTime);
                        })).
                        orElseGet(() -> ThreadLocalBuilder.build(Aggregator.Builder.class, b -> b.setName("merge"))));
                newAggregators.addAll(metric.getAggregators());
                final ImmutableList<Aggregator> finalNewAggregators = ImmutableList.copyOf(newAggregators);
                newMetrics.add(ThreadLocalBuilder.clone(
                        metric, Metric.Builder.class, b->b.setAggregators(finalNewAggregators)));
            } else {
                newMetrics.add(metric);
            }
        }

        final ImmutableList<Metric> finalNewMetrics = ImmutableList.copyOf(newMetrics);
        return ThreadLocalBuilder.clone(metricsQuery, MetricsQuery.Builder.class, b->b.setMetrics(finalNewMetrics));
    }

    private Boolean needMergeAggregator(final ImmutableList<Aggregator> aggregators) {
        return !aggregators.isEmpty()
                && !(aggregators.get(0).getName().equals("merge"))
                && aggregators.stream().anyMatch(aggregator -> !NON_HISTOGRAM_AGGREGATORS.contains(aggregator.getName()));
    }

    /**
     * Proxied version call.
     *
     * @return Proxied version response.
     */
    public CompletionStage<Result> version() {
        return proxy();
    }

    /**
     * Caching metricNames call.
     *
     * @param containing simple string match filter for metric names
     * @param prefix prefix that returned metric names must have (case-insensitive)
     * @return Cached metric names, filtered by the query string.
     */
    public CompletionStage<Result> metricNames(@Nullable final String containing, @Nullable final String prefix) {
        return _kairosService.queryMetricNames(Optional.ofNullable(containing), Optional.ofNullable(prefix), _filterRollups)
                .<JsonNode>thenApply(_mapper::valueToTree)
                .thenApply(Results::ok);
    }

    /**
     * Proxy a request.
     *
     * @return the proxied {@link Result}
     */
    private CompletionStage<Result> proxy() {
        final String path = request().uri();
        LOGGER.debug().setMessage("proxying call to kairosdb")
                .addData("from", path)
                .log();
        final CompletableFuture<Result> promise = new CompletableFuture<>();
        final Http.Request request = request();
        final boolean isHttp10 = request.version().equals("HTTP/1.0");
        final Http.Response configResponse = response();
        _client.proxy(
                path.startsWith("/") ? path : "/" + path,
                request,
                new ResponseHandler(configResponse, promise, isHttp10));
        return promise;
    }

    private final ProxyClient _client;
    private final ObjectMapper _mapper;
    private final boolean _filterRollups;
    private final boolean _requireAggregators;
    private final boolean _addMergeAggregator;
    private final KairosDbService _kairosService;

    private static final ImmutableSet<String> NON_HISTOGRAM_AGGREGATORS = ImmutableSet.of("sum", "count", "avg", "max", "min");

    private static final Logger LOGGER = LoggerFactory.getLogger(KairosDbProxyController.class);

    private static class ResponseHandler extends AsyncCompletionHandler<Void> {
        ResponseHandler(
                final Http.Response response,
                final CompletableFuture<Result> promise,
                final boolean isHttp10) {
            try {
                _outputStream = new PipedOutputStream();
                _inputStream = new PipedInputStream(_outputStream);
                _response = response;
                _promise = promise;
                _isHttp10 = isHttp10;
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public State onStatusReceived(final HttpResponseStatus status) {
            _status = status.getStatusCode();
            return State.CONTINUE;
        }

        @Override
        public State onBodyPartReceived(final HttpResponseBodyPart content) throws Exception {
            _outputStream.write(content.getBodyPartBytes());

            if (content.isLast()) {
                _outputStream.flush();
                _outputStream.close();
            }
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(final HttpResponseHeaders headers) {
            try {
                final HttpHeaders entries = headers.getHeaders();
                Optional<Long> length = Optional.empty();
                if (entries.contains(CONTENT_LENGTH)) {
                    final String clen = entries.get(CONTENT_LENGTH);
                    length = Optional.of(Long.parseLong(clen));
                }
                final String contentType;
                if (entries.get(CONTENT_TYPE) != null) {
                    contentType = entries.get(CONTENT_TYPE);
                } else if (length.isPresent() && length.get() == 0) {
                    contentType = "text/html";
                } else {
                    contentType = null;
                }

                entries.entries()
                        .stream()
                        .filter(entry -> !FILTERED_HEADERS.contains(entry.getKey()))
                        .forEach(entry -> _response.setHeader(entry.getKey(), entry.getValue()));

                if (_isHttp10) {
                    // Strip the transfer encoding header as chunked isn't supported in 1.0
                    _response.getHeaders().remove(TRANSFER_ENCODING);
                    // Strip the connection header since we don't support keep-alives in 1.0
                    _response.getHeaders().remove(CONNECTION);
                }

                final play.mvc.Result result = Results.status(_status).sendEntity(
                        new HttpEntity.Streamed(
                                StreamConverters.fromInputStream(() -> _inputStream, DEFAULT_CHUNK_SIZE),
                                length,
                                Optional.ofNullable(contentType)));

                _promise.complete(result);
                return State.CONTINUE;
                // CHECKSTYLE.OFF: IllegalCatch - We need to return a response no matter what
            } catch (final Throwable e) {
                // CHECKSTYLE.ON: IllegalCatch
                _promise.completeExceptionally(e);
                throw e;
            }
        }

        @Override
        public void onThrowable(final Throwable t) {
            try {
                _outputStream.close();
                _promise.completeExceptionally(t);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            super.onThrowable(t);
        }

        @Override
        public Void onCompleted(final Response response) {
            try {
                _outputStream.flush();
                _outputStream.close();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        private int _status;
        private final PipedOutputStream _outputStream;
        private final Http.Response _response;
        private final PipedInputStream _inputStream;
        private final CompletableFuture<Result> _promise;
        private final boolean _isHttp10;
        private static final int DEFAULT_CHUNK_SIZE = 8 * 1024;
        private static final Set<String> FILTERED_HEADERS = Sets.newHashSet(CONTENT_TYPE, CONTENT_LENGTH, TRANSFER_ENCODING);
    }
}

