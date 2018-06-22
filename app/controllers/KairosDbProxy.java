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
package controllers;

import akka.stream.javadsl.StreamConverters;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.play.ProxyClient;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collector;
import javax.inject.Inject;

/**
 * A generic proxy controller.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
@Singleton
public class KairosDbProxy extends Controller {
    /**
     * Public constructor.
     *
     * @param configuration Play configuration to configure the proxy
     * @param client ws client to use
     * @param kairosDbClient a KairosDBClient
     * @param mapper ObjectMapper to use for JSON serialization
     */
    @Inject
    public KairosDbProxy(
            final Config configuration,
            final WSClient client,
            final KairosDbClient kairosDbClient,
            final ObjectMapper mapper) {
        final URI kairosURL = URI.create(configuration.getString("kairosdb.uri"));
        _client = new ProxyClient(kairosURL, client);
        _kairosDbClient = kairosDbClient;
        _mapper = mapper;
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
        return proxy();
    }

    /**
     * Proxied tagValues call.
     *
     * @return Proxied tagValues response.
     */
    public CompletionStage<Result> tagValues() {
        return proxy();
    }

    /**
     * Proxied queryTags call.
     *
     * @return Proxied queryTags response.
     */
    public CompletionStage<Result> queryTags() {
        return proxy();
    }

    /**
     * Proxied queryMetrics call.
     *
     * @return Proxied queryMetrics response.
     */
    public CompletionStage<Result> queryMetrics() {
        return proxy();
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
     * @return Cached metric names, filtered by the query string.
     */
    public CompletionStage<Result> metricNames(final String containing) {
        final List<String> metrics = _cache.getIfPresent(METRICS_KEY);

        final CompletionStage<List<String>> response;
        if (metrics != null) {
            response = CompletableFuture.completedFuture(metrics);
        } else {
            // TODO(brandon): Investigate refreshing eagerly or in the background
            // Refresh the cache
            final CompletionStage<List<String>> queryResponse = _kairosDbClient.queryMetricNames()
                    .thenApply(KairosMetricNamesQueryResponse::getResults)
                    .thenApply(list -> {
                        _cache.put(METRICS_KEY, list);
                        _metricsList.set(list);
                        return list;
                    });

            final List<String> metricsList = _metricsList.get();
            if (metricsList != null) {
                response = CompletableFuture.completedFuture(metricsList);
            } else {
                response = queryResponse;
            }
        }

        return response
                .thenApply(list -> filterMetricNames(list, containing))
                .thenApply(list -> new KairosMetricNamesQueryResponse.Builder().setResults(list).build())
                .<JsonNode>thenApply(_mapper::valueToTree)
                .thenApply(Results::ok);
    }

    private static ImmutableList<String> filterMetricNames(final List<String> metricNames, final String containing) {
        if (containing == null || containing.isEmpty()) {
            return ImmutableList.copyOf(metricNames);
        }

        final String lowerContaining = containing.toLowerCase(Locale.ENGLISH);
        final Predicate<String> containsPredicate = s -> s.toLowerCase().contains(lowerContaining);

        return metricNames.stream()
                .filter(IS_PT1M.negate())
                .filter(containsPredicate)
                .collect(Collector.of(
                        ImmutableList::<String>builder,
                        ImmutableList.Builder::add,
                        (a, b) -> a.addAll(b.build()),
                        ImmutableList.Builder::build));
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
    private final KairosDbClient _kairosDbClient;
    private final ObjectMapper _mapper;
    private final Cache<String, List<String>> _cache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
    private final AtomicReference<List<String>> _metricsList = new AtomicReference<>(null);

    private static final Logger LOGGER = LoggerFactory.getLogger(KairosDbProxy.class);
    private static final String METRICS_KEY = "METRICNAMES";
    private static final Predicate<String> IS_PT1M = s -> s.startsWith("PT1M/");

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
        public Void onCompleted(final Response response) throws Exception {
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

