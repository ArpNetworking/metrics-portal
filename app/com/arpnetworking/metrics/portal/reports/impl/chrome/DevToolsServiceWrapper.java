/*
 * Copyright 2019 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kklisura.cdt.protocol.commands.Network;
import com.github.kklisura.cdt.protocol.types.network.ErrorReason;
import com.github.kklisura.cdt.protocol.types.network.RequestPattern;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Wraps a {@link com.github.kklisura.cdt.services.ChromeDevToolsService} to conform to the {@link DevToolsService} interface.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class DevToolsServiceWrapper implements DevToolsService {
    private final com.github.kklisura.cdt.services.ChromeService _chromeService;
    private final ImmutableMap<String, OriginConfig> _originConfigs;
    private final com.github.kklisura.cdt.services.types.ChromeTab _tab;
    private final com.github.kklisura.cdt.services.ChromeDevToolsService _dts;
    private final ExecutorService _executor;
    private final AtomicBoolean _closed = new AtomicBoolean(false);

    /**
     * Constructor.
     *
     * @param chromeService The {@link com.github.kklisura.cdt.services.ChromeService} that owns the devtools.
     * @param tab The {@link com.github.kklisura.cdt.services.types.ChromeTab} associated with the devtools.
     * @param dts The {@link com.github.kklisura.cdt.services.ChromeDevToolsService} instance to wrap.
     */
    /* package private */ DevToolsServiceWrapper(
            final com.github.kklisura.cdt.services.ChromeService chromeService,
            final ImmutableMap<String, OriginConfig> originConfigs,
            final com.github.kklisura.cdt.services.types.ChromeTab tab,
            final com.github.kklisura.cdt.services.ChromeDevToolsService dts,
            final ExecutorService executor

    ) {
        _chromeService = chromeService;
        _originConfigs = originConfigs;
        _tab = tab;
        _dts = dts;
        _executor = executor;

        configureRequestInterception();
    }

    private void configureRequestInterception() {
        final Network network = _dts.getNetwork();
        network.setRequestInterception(ImmutableList.of(new RequestPattern()));
        network.onRequestIntercepted(event -> {
            final URI uri = URI.create(event.getRequest().getUrl());
            final String origin = uri.getScheme() + "://" + uri.getAuthority();
            final OriginConfig originConfig = _originConfigs.get(origin);
            if (originConfig == null || !originConfig.isRequestAllowed(uri.getPath())) {
                System.out.println("Cancelling request to " + uri);
                network.continueInterceptedRequest(
                        event.getInterceptionId(), ErrorReason.ABORTED, null, null, null, null, null, null
                );
                return;
            }
            final ImmutableMap<String, Object> headers = ImmutableMap.<String, Object>builder()
                    .putAll(event.getRequest().getHeaders())
                    .putAll(originConfig.getAdditionalHeaders())
                    .build();
            network.continueInterceptedRequest(
                    event.getInterceptionId(),
                    null,
                    null,
                    event.getRequest().getUrl(),
                    event.getRequest().getMethod(),
                    event.getRequest().getPostData(),
                    headers,
                    null
            );
        });
    }

    @Override
    public CompletableFuture<Object> evaluate(final String js) {
        if (_closed.get()) {
            throw new IllegalStateException("cannot interact with closed devtools");
        }
        return supplyInExecutor(() -> _dts.getRuntime().evaluate(js).getResult().getValue());
    }

    @Override
    public CompletableFuture<byte[]> printToPdf(final double pageWidthInches, final double pageHeightInches) {
        if (_closed.get()) {
            throw new IllegalStateException("cannot interact with closed devtools");
        }
        return supplyInExecutor(() -> Base64.getDecoder().decode(_dts.getPage().printToPDF(
                false,
                false,
                false,
                1.0,
                pageWidthInches,
                pageHeightInches,
                0.4,
                0.4,
                0.4,
                0.4,
                "",
                true,
                "",
                "",
                true
        )));
    }

    @Override
    public boolean isNavigationAllowed(final String url) {
        final URI uri = URI.create(url);
        final String origin = uri.getScheme() + "://" + uri.getAuthority();
        if (_originConfigs.containsKey(origin)) {
            return _originConfigs.get(origin).isNavigationAllowed(uri.getPath());
        }
        return false;
    }

    @Override
    public CompletableFuture<Void> navigate(final String url) {
        if (_closed.get()) {
            throw new IllegalStateException("cannot interact with closed devtools");
        }
        if (!isNavigationAllowed(url)) {
            throw new IllegalArgumentException("navigation is not allowed to " + url);
        }
        final CompletableFuture<Void> result = new CompletableFuture<>();
        cascadeCancellation(
                result,
                _executor.submit(() -> {
                    _dts.getPage().enable();
                    _dts.getPage().onLoadEventFired(e -> {
                        LOGGER.debug()
                                .setMessage("navigated to")
                                .addData("url", url)
                                .log();
                        result.complete(null);
                    });
                    LOGGER.debug()
                            .setMessage("navigating to")
                            .addData("url", url)
                            .log();
                    _dts.getPage().navigate(url);
                })
        );
        return result;
    }

    @Override
    public CompletableFuture<Void> nowOrOnEvent(final String eventName, final Supplier<Boolean> ready) {
        if (_closed.get()) {
            throw new IllegalStateException("cannot interact with closed devtools");
        }

        final String triggerMessage = eventName + " -- " + UUID.randomUUID();
        final String jsonEventName, jsonTriggerMessage;
        try {
            jsonEventName = OBJECT_MAPPER.writeValueAsString(eventName);
            jsonTriggerMessage = OBJECT_MAPPER.writeValueAsString(triggerMessage);
        } catch (final JsonProcessingException e) {
            throw new AssertionError("json-encoding a String somehow failed", e);
        }

        final CompletableFuture<Void> result = new CompletableFuture<>();
        cascadeCancellation(
                result,
                _executor.submit(() -> {
                    _dts.getConsole().enable();
                    _dts.getConsole().onMessageAdded(e -> {
                        if (e.getMessage().getText().equals(triggerMessage)) {
                            result.complete(null);
                        }
                    });
                    evaluate("window.addEventListener(" + jsonEventName + ", () => console.log(" + jsonTriggerMessage + "))")
                            .thenAccept(nothing -> {
                                if (ready.get()) {
                                    result.complete(null);
                                }
                            });
                })
        );

        return result;
    }

    @Override
    public CompletableFuture<Void> close() {
        if (_closed.getAndSet(true)) {
            return CompletableFuture.completedFuture(null);
        }
        final CompletableFuture<Void> result = new CompletableFuture<>();
        cascadeCancellation(
                result,
                _executor.submit(() -> {
                    _dts.close();
                    _chromeService.closeTab(_tab);
                    result.complete(null);
                })
        );
        return result;
    }

    /**
     * Cancel {@code task} if {@code result} gets cancelled.
     */
    private void cascadeCancellation(final CompletableFuture<?> result, final Future<?> task) {
        result.whenComplete((x, e) -> task.cancel(true));
    }

    /**
     * Run a supplier in a thread in the executor, and when it finishes complete a {@link CompletableFuture}.
     */
    private <T> CompletableFuture<T> supplyInExecutor(final Supplier<T> supplier) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        cascadeCancellation(result, _executor.submit(() -> {
            result.complete(supplier.get());
        }));
        return result;
    }

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger(DevToolsServiceWrapper.class);

}
