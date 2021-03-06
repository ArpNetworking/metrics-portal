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
import com.github.kklisura.cdt.protocol.types.page.PrintToPDFTransferMode;

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
    private final PerOriginConfigs _originConfigs;
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
            final PerOriginConfigs originConfigs,
            final com.github.kklisura.cdt.services.types.ChromeTab tab,
            final com.github.kklisura.cdt.services.ChromeDevToolsService dts,
            final ExecutorService executor,
            final DevToolsNetworkConfigurationProtocol configProtocol
    ) {
        _chromeService = chromeService;
        _originConfigs = originConfigs;
        _tab = tab;
        _dts = dts;
        _executor = executor;

        configProtocol.configure(_dts, _originConfigs);
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
        return supplyInExecutor(() -> Base64.getDecoder().decode(
                _dts.getPage().printToPDF(
                    false, // landscape
                    false, // displayHeaderFooter
                    false, // printBackground
                    1.0, // scale
                    pageWidthInches, // paperWidth
                    pageHeightInches, // paperHeight
                    0.4, // marginTop
                    0.4, // marginBottom
                    0.4, // marginLeft
                    0.4, // marginRight
                    "", // pageRanges
                    true, // ignoreInvalidPageRanges
                    "", // headerTemplate
                    "", // footerTemplate
                    true, // preferCSSPageSize
                    PrintToPDFTransferMode.RETURN_AS_BASE_64 // transferMode
                ).getData()
        ));
    }

    @Override
    public boolean isNavigationAllowed(final String url) {
        return _originConfigs.isNavigationAllowed(url);
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
