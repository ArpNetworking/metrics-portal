/*
 * Copyright 2014 Groupon.com
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
package com.arpnetworking.play.metrics;

import com.arpnetworking.metrics.Counter;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Timer;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.Strings;
import play.libs.typedmap.TypedEntry;
import play.libs.typedmap.TypedKey;
import play.libs.typedmap.TypedMap;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.routing.Router;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Play action wrapper that wraps each call in a metrics timer.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class MetricsActionWrapper extends Action.Simple {
    /**
     * Public constructor.
     *
     * @param metricsFactory Instance of {@code MetricsFactory}.
     */
    public MetricsActionWrapper(final MetricsFactory metricsFactory) {
        _metricsFactory = metricsFactory;
    }

    @Override
    public CompletionStage<Result> call(final Http.Request request) {
        final Http.Request requestWithMetrics  = initMetrics(request);
        final Metrics metrics = requestWithMetrics.attrs().get(Attrs.METRICS);
        final BiFunction<Result, Throwable, Result> handler = new HandlerFunction(request, metrics);
        try {
            // Async controllers we can just chain the handler
            return delegate.call(request).handle(handler);
            // CHECKSTYLE.OFF: IllegalCatch - Need to be able to propagate any fault
        } catch (final Throwable t1) {
            // Sync controllers actually throw so we need to do some mapping
            try {
                handler.apply(null, t1);
                // CHECKSTYLE.OFF: EmptyBlock - This is an artifact of re-using the handler
            } catch (final Exception t2) {
                // Ignore this (it's just e1 again)
            }
            // CHECKSTYLE.ON: EmptyBlock
            // CHECKSTYLE.ON: IllegalCatch
            final CompletableFuture<Result> future = new CompletableFuture<>();
            future.completeExceptionally(t1);
            return future;
        }
    }

    /**
     * Create the name of the timer from a {@code Http.Request}.
     *
     * @param request the HTTP request.
     * @return Name of the timer for the request/response.
     */
    private static String createRouteMetricName(final Http.Request request) {
        final StringBuilder metricNameBuilder = new StringBuilder("rest_service/");
        metricNameBuilder.append(request.method());

        final String route = ROUTE_PATTERN_REGEX.matcher(
                Optional.ofNullable(request.attrs().get(Router.Attrs.HANDLER_DEF).path()).orElse(""))
                .replaceAll(":$1");

        if (!Strings.isNullOrEmpty(route)) {
            if (!route.startsWith("/")) {
                metricNameBuilder.append("/");
            }
            metricNameBuilder.append(route);
            if (!route.endsWith("/")) {
                metricNameBuilder.append("/");
            }
        }

        return metricNameBuilder.toString();
    }

    private Http.Request initMetrics(final Http.Request request) {
        final Optional<Metrics> metrics = request.attrs().getOptional(Attrs.METRICS);
        if (metrics.isEmpty()) {
            return request.withAttrs(TypedMap.create(new TypedEntry<>(Attrs.METRICS, _metricsFactory.create())));
        } else {
            LOGGER.warn()
                    .setMessage("Found metrics in request context; possible issue")
                    .addData("metrics", metrics)
                    .log();
            return request;
        }
    }

    private final MetricsFactory _metricsFactory;

    private static final int STATUS_2XX = 2;
    private static final int STATUS_3XX = 3;
    private static final int STATUS_4XX = 4;
    private static final int STATUS_5XX = 5;
    private static final Pattern ROUTE_PATTERN_REGEX = Pattern.compile("\\$([^<]+)<([^>]+)>");
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsActionWrapper.class);

    private static final class HandlerFunction implements BiFunction<Result, Throwable, Result> {

        @Override
        public Result apply(final Result r, final Throwable t) {
            final Counter counter2xx = _metrics.createCounter(_routeMetricName + "status/2xx");
            final Counter counter3xx = _metrics.createCounter(_routeMetricName + "status/3xx");
            final Counter counter4xx = _metrics.createCounter(_routeMetricName + "status/4xx");
            final Counter counter5xx = _metrics.createCounter(_routeMetricName + "status/5xx");
            final long requestSize = _request.body().asBytes().size();
            final long responseSize;
            if (t != null) {
                counter5xx.increment();
                responseSize = 0;
            } else {
                final int resultFamily = r.status() / 100;
                if (resultFamily == STATUS_2XX) {
                    counter2xx.increment();
                } else if (resultFamily == STATUS_3XX) {
                    counter3xx.increment();
                } else if (resultFamily == STATUS_4XX) {
                    counter4xx.increment();
                } else if (resultFamily == STATUS_5XX) {
                    counter5xx.increment();
                }
                responseSize = r.body().contentLength().orElse(0L);
            }
            _metrics.incrementCounter(_routeMetricName + "request_size", requestSize);
            _metrics.incrementCounter(_routeMetricName + "response_size", responseSize);
            _timer.stop();
            _metrics.close();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t != null) {
                throw new RuntimeException(t);
            }
            return r;
        }

        /* package private */ HandlerFunction(
                final Http.Request request,
                final Metrics metrics) {
            _request = request;
            _metrics = metrics;

            _routeMetricName = createRouteMetricName(request);
            _timer = _metrics.createTimer(_routeMetricName + "latency");
        }

        private final Http.Request _request;
        private final Metrics _metrics;
        private final Timer _timer;
        private final String _routeMetricName;
    }

    private static final class Attrs {
        private static final TypedKey<Metrics> METRICS = TypedKey.create("metrics");
    }
}
