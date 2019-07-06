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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Wraps a {@link com.github.kklisura.cdt.services.ChromeDevToolsService} to conform to the {@link DevToolsService} interface.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class DevToolsServiceWrapper implements DevToolsService {
    private final com.github.kklisura.cdt.services.ChromeDevToolsService _dts;

    /**
     * Public constructor.
     *
     * @param dts The {@link com.github.kklisura.cdt.services.ChromeDevToolsService} instance to wrap.
     */
    public DevToolsServiceWrapper(final com.github.kklisura.cdt.services.ChromeDevToolsService dts) {
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
    public CompletionStage<Void> navigate(final String url) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        _dts.getPage().enable();
        _dts.getPage().onLoadEventFired(e -> {
            result.complete(null);
        });
        _dts.getPage().navigate(url);
        return result;
    }

    @Override
    public CompletionStage<Void> nowOrOnEvent(final String eventName, final Supplier<Boolean> ready) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        waitForEvent(eventName).thenAccept(foo -> {
            result.complete(null);
        });
        if (ready.get()) {
            result.complete(null);
        } else {
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
        _dts.close();
    }

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createInstance();

}
