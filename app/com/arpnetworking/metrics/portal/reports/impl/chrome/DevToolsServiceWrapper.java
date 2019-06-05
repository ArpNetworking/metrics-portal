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

/**
 * Wraps a <code>com.github.kklisura.cdt.services.DevToolsService</code> to conform to the {@link DevToolsService} interface.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class DevToolsServiceWrapper implements DevToolsService {
    private final com.github.kklisura.cdt.services.ChromeDevToolsService _dts;

    /**
     * @param dts The <code>com.github.kklisura.cdt.services.DevToolsService</code> instance to wrap.
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
    public void navigate(final String url) {
        _dts.getPage().navigate(url);
    }

    @Override
    public void onLoad(final Runnable callback) {
        _dts.getPage().enable(); _dts.getPage().onLoadEventFired(e -> callback.run());
    }

    @Override
    public void onEvent(final String eventName, final Runnable callback) {
        final String callbackId = UUID.randomUUID().toString();
        final String jsonEventName, jsonCallbackId;
        try {
            jsonEventName = OBJECT_MAPPER.writeValueAsString(eventName);
            jsonCallbackId = OBJECT_MAPPER.writeValueAsString(callbackId);
        } catch (final JsonProcessingException e) {
            throw new AssertionError("json-encoding a String somehow failed", e);
        }
        _dts.getConsole().enable();
        _dts.getConsole().onMessageAdded(e -> {
            if (e.getMessage().getText().equals(callbackId)) {
                callback.run();
            }
        });
        evaluate("window.addEventListener(" + jsonEventName + ", () -> console.log(" + jsonCallbackId + "))");
    }

    @Override
    public void close() {
        _dts.close();
    }

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createInstance();

}
