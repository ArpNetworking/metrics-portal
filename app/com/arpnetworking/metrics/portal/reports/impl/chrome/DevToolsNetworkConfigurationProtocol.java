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

import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.github.kklisura.cdt.protocol.commands.Fetch;
import com.github.kklisura.cdt.protocol.commands.Network;
import com.github.kklisura.cdt.protocol.types.fetch.HeaderEntry;
import com.github.kklisura.cdt.protocol.types.network.ErrorReason;
import com.github.kklisura.cdt.protocol.types.network.Request;
import com.github.kklisura.cdt.protocol.types.network.RequestPattern;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;

@SuppressFBWarnings("SE_BAD_FIELD") // False positive: https://github.com/spotbugs/spotbugs/issues/740
/* package private */ enum DevToolsNetworkConfigurationProtocol {

    @Deprecated
    NETWORK(DevToolsNetworkConfigurationProtocol::configureWithNetwork),
    FETCH(DevToolsNetworkConfigurationProtocol::configureWithFetch);

    DevToolsNetworkConfigurationProtocol(final BiConsumer<ChromeDevToolsService, PerOriginConfigs> configure) {
        _configure = configure;
    }

    public void configure(final ChromeDevToolsService dts, final PerOriginConfigs originConfigs) {
        _configure.accept(dts, originConfigs);
    }

    private final BiConsumer<ChromeDevToolsService, PerOriginConfigs> _configure;

    @SuppressWarnings("deprecation")
    private static void configureWithNetwork(final ChromeDevToolsService dts, final PerOriginConfigs originConfigs) {

        final Network network = dts.getNetwork();
        network.setRequestInterception(ImmutableList.of(new RequestPattern()));
        network.onRequestIntercepted(event -> {
            final String url = event.getRequest().getUrl();
            if (!originConfigs.isRequestAllowed(url)) {
                LOGGER.warn()
                        .setMessage("rejecting request")
                        .addData("url", url)
                        .log();
                network.continueInterceptedRequest(
                        event.getInterceptionId(), ErrorReason.ABORTED, null, null, null, null, null, null
                );
                return;
            }
            final ImmutableMap<String, Object> headers = ImmutableMap.<String, Object>builder()
                    .putAll(event.getRequest().getHeaders())
                    .putAll(originConfigs.getAdditionalHeaders(url))
                    .build();
            network.continueInterceptedRequest(
                    event.getInterceptionId(),
                    null,
                    null,
                    url,
                    event.getRequest().getMethod(),
                    event.getRequest().getPostData(),
                    headers,
                    null
            );
        });
    }

    private static void configureWithFetch(final ChromeDevToolsService dts, final PerOriginConfigs originConfigs) {
        final Fetch fetch = dts.getFetch();
        fetch.enable();
        fetch.onRequestPaused(event -> {
            final String url = event.getRequest().getUrl();
            if (!originConfigs.isRequestAllowed(url)) {
                LOGGER.warn()
                        .setMessage("rejecting request")
                        .addData("url", url)
                        .log();
                fetch.failRequest(event.getRequestId(), ErrorReason.ABORTED);
                return;
            }
            final ImmutableMap<String, String> headers = ImmutableMap.<String, String>builder()
                    .putAll(getRequestHeaders(event.getRequest()))
                    .putAll(originConfigs.getAdditionalHeaders(url))
                    .build();
            fetch.continueRequest(
                    event.getRequestId(),
                    url,
                    event.getRequest().getMethod(),
                    event.getRequest().getPostData(),
                    headerMapToList(headers)
            );
        });
    }

    private static ImmutableMap<String, String> getRequestHeaders(final Request request) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (final Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
            if (entry.getValue() instanceof String) {
                builder = builder.put(entry.getKey(), (String) entry.getValue());
            }
        }
        return builder.build();
    }

    /* package private */ static ImmutableList<HeaderEntry> headerMapToList(final ImmutableMap<String, String> headers) {
        return headers.entrySet().stream()
                .map(entry -> {
                    final HeaderEntry headerEntry = new HeaderEntry();
                    headerEntry.setName(entry.getKey());
                    headerEntry.setValue(entry.getValue());
                    return headerEntry;
                })
                .collect(ImmutableList.toImmutableList());
    }

    /* package private */ static ImmutableMap<String, String> headerListToMap(final Collection<HeaderEntry> headers) {
        return headers.stream().collect(ImmutableMap.toImmutableMap(HeaderEntry::getName, HeaderEntry::getValue));
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DevToolsNetworkConfigurationProtocol.class);

}
