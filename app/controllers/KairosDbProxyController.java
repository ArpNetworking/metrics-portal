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
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.kairos.service.DefaultQueryContext;
import com.arpnetworking.kairos.service.KairosDbService;
import com.arpnetworking.kairos.service.QueryContext;
import com.arpnetworking.kairos.service.QueryOrigin;
import com.arpnetworking.play.ProxyClient;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        _minAggregationPeriod = Optional.ofNullable(configuration.getDuration("kairosdb.proxy.minAggregationPeriod"));

        final ImmutableSet<String> excludedTagNames = ImmutableSet.copyOf(
                configuration.getStringList("kairosdb.proxy.excludedTagNames"));

        _kairosService = kairosService;
    }

    /**
     * Proxied status call.
     *
     * @return Proxied status response.
     */
    public CompletionStage<Result> status(final Http.Request request) {
        return proxy(request);
    }

    /**
     * Proxied healthcheck call.
     *
     * @return Proxied health check response.
     */
    public CompletionStage<Result> healthCheck(final Http.Request request) {
        return proxy(request);
    }

    /**
     * Proxied tagNames call.
     *
     * @return Proxied tagNames response.
     */
    public CompletionStage<Result> tagNames(final Http.Request request) {
        return _kairosService.listTagNames()
                .<JsonNode>thenApply(_mapper::valueToTree)
                .thenApply(Results::ok);
    }

    /**
     * Proxied tagValues call.
     *
     * @return Proxied tagValues response.
     */
    public CompletionStage<Result> tagValues(final Http.Request request) {
        return proxy(request);
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
    public CompletionStage<Result> queryTags(final Http.Request request) {
        try {
            final JsonNode jsonBody = request.body().asJson();
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
    public CompletionStage<Result> queryMetrics(final Http.Request request) {
        try {
            final JsonNode jsonBody = request.body().asJson();
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

            metricsQuery = clampAggregationPeriod(metricsQuery);

            final QueryContext context = new DefaultQueryContext.Builder()
                    .setOrigin(QueryOrigin.EXTERNAL_REQUEST)
                    .build();

            return _kairosService.queryMetrics(context, metricsQuery)
                    .<JsonNode>thenApply(_mapper::valueToTree)
                    .thenApply(Results::ok);
        } catch (final IOException e) {
            return CompletableFuture.completedFuture(Results.internalServerError(e.getMessage()));
        }
    }

    /* package private */ MetricsQuery clampAggregationPeriod(final MetricsQuery metricsQuery) {
        final List<Metric> newMetrics = new ArrayList<>();
        for (final Metric metric : metricsQuery.getMetrics()) {
            final ImmutableList<Aggregator> newAggregators = metric.getAggregators()
                    .stream()
                    .map(aggregator -> {
                        if (aggregator.getSampling().isPresent()) {
                            final Sampling sampling = aggregator.getSampling().get();
                            final Duration samplingDuration = Duration.of(
                                    sampling.getValue(),
                                    SamplingUnit.toChronoUnit(sampling.getUnit()));
                            if (_minAggregationPeriod.isPresent() && samplingDuration.compareTo(_minAggregationPeriod.get()) < 0) {
                                return ThreadLocalBuilder.clone(aggregator, Aggregator.Builder.class, b -> {
                                    b.setSampling(Sampling.Builder.clone(sampling, Sampling.Builder.class, builder -> {
                                        builder.setValue((int) _minAggregationPeriod.get().getSeconds());
                                        builder.setUnit(SamplingUnit.SECONDS);
                                    }));
                                });
                            }
                        }
                        return aggregator;
                    })
                    .collect(ImmutableList.toImmutableList());

            newMetrics.add(ThreadLocalBuilder.clone(
                    metric, Metric.Builder.class, b->b.setAggregators(newAggregators)));

        }
        final ImmutableList<Metric> finalNewMetrics = ImmutableList.copyOf(newMetrics);
        return ThreadLocalBuilder.clone(metricsQuery, MetricsQuery.Builder.class, b->b.setMetrics(finalNewMetrics));
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
                            aggregator.getAlignStartTime().ifPresent(b::setAlignStartTime);
                            aggregator.getAlignSampling().ifPresent(b::setAlignSampling);
                            aggregator.getAlignEndTime().ifPresent(b::setAlignEndTime);
                            // moving window sampling is a faux sampling interval that takes a window of datapoints
                            // but generates a data point for a single unit of time.
                            if (aggregator.getName().equals("movingWindow") && aggregator.getSampling().isPresent()) {
                                b.setSampling(new Sampling.Builder().setValue(1).setUnit(aggregator.getSampling().get().getUnit()).build());
                            } else {
                                aggregator.getSampling().ifPresent(b::setSampling);
                            }
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
    public CompletionStage<Result> version(final Http.Request request) {
        return proxy(request);
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
    private CompletionStage<Result> proxy(final Http.Request request) {
        final String path = request.uri();
        LOGGER.debug().setMessage("proxying call to kairosdb")
                .addData("from", path)
                .log();
        return _client.proxy(
                path.startsWith("/") ? path : "/" + path,
                request);
    }

    private final ProxyClient _client;
    private final ObjectMapper _mapper;
    private final boolean _filterRollups;
    private final boolean _requireAggregators;
    private final boolean _addMergeAggregator;
    private final Optional<Duration> _minAggregationPeriod;
    private final KairosDbService _kairosService;

    private static final ImmutableSet<String> NON_HISTOGRAM_AGGREGATORS = ImmutableSet.of("sum", "count", "avg", "max", "min");

    private static final Logger LOGGER = LoggerFactory.getLogger(KairosDbProxyController.class);

}

