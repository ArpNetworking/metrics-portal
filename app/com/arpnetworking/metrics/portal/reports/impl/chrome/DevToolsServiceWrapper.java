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

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Wraps a {@link com.github.kklisura.cdt.services.ChromeDevToolsService} to conform to the {@link DevToolsService} interface.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class DevToolsServiceWrapper implements DevToolsService {
    private final com.github.kklisura.cdt.services.ChromeService _chromeService;
    private final com.github.kklisura.cdt.services.types.ChromeTab _tab;
    private final com.github.kklisura.cdt.services.ChromeDevToolsService _dts;
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
            final com.github.kklisura.cdt.services.types.ChromeTab tab,
            final com.github.kklisura.cdt.services.ChromeDevToolsService dts
    ) {
        _chromeService = chromeService;
        _tab = tab;
        _dts = dts;
    }

    @Override
    public Object evaluate(final String js) {
        return _dts.getRuntime().evaluate(js).getResult().getValue();
    }

    @Override
    public byte[] printToPdf(final double pageWidthInches, final double pageHeightInches) {
        return Base64.getDecoder().decode(_dts.getPage().printToPDF(
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
        ));
    }

    @Override
    public void navigate(final String url) throws InterruptedException, ExecutionException {
        final CompletableFuture<Void> loaded = new CompletableFuture<>();
        _dts.getPage().enable();
        _dts.getPage().onLoadEventFired(e -> {
            LOGGER.debug()
                    .setMessage("navigated to")
                    .addData("url", url)
                    .log();
            loaded.complete(null);
        });
        LOGGER.debug()
                .setMessage("navigating to")
                .addData("url", url)
                .log();
        _dts.getPage().navigate(url);
        loaded.get();
    }

    @Override
    public CompletionStage<Void> nowOrOnEvent(final String eventName, final Supplier<Boolean> ready) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        waitForEvent(eventName).thenAccept(foo -> result.complete(null));
        if (ready.get()) {
            result.complete(null);
        }
        return result;
    }

    private CompletionStage<Void> waitForEvent(final String eventName) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        final String triggerMessage = eventName + " -- " + UUID.randomUUID();
        final String jsonEventName, jsonTriggerMessage;
        try {
            jsonEventName = OBJECT_MAPPER.writeValueAsString(eventName);
            jsonTriggerMessage = OBJECT_MAPPER.writeValueAsString(triggerMessage);
        } catch (final JsonProcessingException e) {
            throw new AssertionError("json-encoding a String somehow failed", e);
        }
        _dts.getConsole().enable();
        _dts.getConsole().onMessageAdded(e -> {
            if (e.getMessage().getText().equals(triggerMessage)) {
                result.complete(null);
            }
        });
        evaluate("window.addEventListener(" + jsonEventName + ", () => console.log(" + jsonTriggerMessage + "))");
        return result;
    }

    @Override
    public void close() {
        if (_closed.getAndSet(true)) {
            return;
        }
        _chromeService.closeTab(_tab);
        _dts.close();
    }

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger(DevToolsServiceWrapper.class);

}
