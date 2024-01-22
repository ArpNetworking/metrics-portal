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

import com.github.kklisura.cdt.protocol.events.fetch.RequestPaused;
import com.github.kklisura.cdt.protocol.support.types.EventHandler;
import com.github.kklisura.cdt.protocol.types.network.ErrorReason;
import com.github.kklisura.cdt.protocol.types.network.Request;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

/**
 * Tests for {@link DevToolsNetworkConfigurationProtocol}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@SuppressWarnings("deprecation")
public class DevToolsNetworkConfigurationProtocolTest {

    @Mock
    private ChromeDevToolsService _dts;
    @Mock
    private com.github.kklisura.cdt.protocol.commands.Network _network;
    @Mock
    private com.github.kklisura.cdt.protocol.commands.Fetch _fetch;
    @Captor
    private ArgumentCaptor<EventHandler<com.github.kklisura.cdt.protocol.events.network.RequestIntercepted>> _requestInterceptorCaptor;
    @Captor
    private ArgumentCaptor<EventHandler<RequestPaused>> _requestPausedCaptor;
    private final PerOriginConfigs _originConfigs = new PerOriginConfigs.Builder().setByOrigin(ImmutableMap.of(
            "https://whitelisted.com", new OriginConfig.Builder()
                    .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-.*"))
                    .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-.*"))
                    .setAdditionalHeaders(ImmutableMap.of("X-Extra-Header", "extra header value"))
                    .build()
    )).build();
    private AutoCloseable _mocks;


    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        Mockito.doReturn(_network).when(_dts).getNetwork();
        Mockito.doReturn(_fetch).when(_dts).getFetch();
    }

    @After
    public void tearDown() {
        if (_mocks != null) {
            try {
                _mocks.close();
                // CHECKSTYLE.OFF: IllegalCatch - Ignore all errors when closing the mock
            } catch (final Exception ignored) { }
                // CHECKSTYLE.ON: IllegalCatch
        }
    }

    @Test
    public void testNetworkFiltersRequests() {
        DevToolsNetworkConfigurationProtocol.NETWORK.configure(_dts, _originConfigs);
        Mockito.verify(_network).onRequestIntercepted(_requestInterceptorCaptor.capture());
        final EventHandler<com.github.kklisura.cdt.protocol.events.network.RequestIntercepted> callback =
                _requestInterceptorCaptor.getValue();

        final Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://whitelisted.com/allowed-req-1");
        request.setPostData("data");
        request.setHeaders(ImmutableMap.of());
        final com.github.kklisura.cdt.protocol.events.network.RequestIntercepted event =
                new com.github.kklisura.cdt.protocol.events.network.RequestIntercepted();
        event.setInterceptionId(UUID.randomUUID().toString());
        event.setRequest(request);

        callback.onEvent(event);
        Mockito.verify(_network).continueInterceptedRequest(
                Mockito.eq(event.getInterceptionId()),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.eq("https://whitelisted.com/allowed-req-1"),
                Mockito.eq("POST"),
                Mockito.eq("data"),
                Mockito.any(),
                Mockito.isNull()
        );

        request.setUrl("https://whitelisted.com/disallowed-path");
        Mockito.reset(_network);
        callback.onEvent(event);
        Mockito.verify(_network).continueInterceptedRequest(
                event.getInterceptionId(),
                ErrorReason.ABORTED,
                null,
                null,
                null,
                null,
                null,
                null
        );

        request.setUrl("https://not-whitelisted.com/allowed-req-1");
        Mockito.reset(_network);
        callback.onEvent(event);
        Mockito.verify(_network).continueInterceptedRequest(
                event.getInterceptionId(),
                ErrorReason.ABORTED,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    public void testNetworkAddsHeaders() {
        DevToolsNetworkConfigurationProtocol.NETWORK.configure(_dts, _originConfigs);
        Mockito.verify(_network).onRequestIntercepted(_requestInterceptorCaptor.capture());
        final EventHandler<com.github.kklisura.cdt.protocol.events.network.RequestIntercepted> callback =
                _requestInterceptorCaptor.getValue();

        final Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://whitelisted.com/allowed-req-1");
        request.setPostData("data");
        request.setHeaders(ImmutableMap.of("Original-Header", "original header value"));
        final com.github.kklisura.cdt.protocol.events.network.RequestIntercepted event =
                new com.github.kklisura.cdt.protocol.events.network.RequestIntercepted();
        event.setRequest(request);
        event.setInterceptionId(UUID.randomUUID().toString());

        callback.onEvent(event);
        Mockito.verify(_network).continueInterceptedRequest(
                event.getInterceptionId(),
                null,
                null,
                "https://whitelisted.com/allowed-req-1",
                "POST",
                "data",
                ImmutableMap.of(
                        "Original-Header", "original header value",
                        "X-Extra-Header", "extra header value"
                ),
                null
        );
    }

    @Test
    public void testFetchFiltersRequests() {
        DevToolsNetworkConfigurationProtocol.FETCH.configure(_dts, _originConfigs);
        Mockito.verify(_fetch).onRequestPaused(_requestPausedCaptor.capture());
        final EventHandler<RequestPaused> callback = _requestPausedCaptor.getValue();

        final Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://whitelisted.com/allowed-req-1");
        request.setPostData("data");
        request.setHeaders(ImmutableMap.of());
        final RequestPaused event = new RequestPaused();
        event.setRequestId(UUID.randomUUID().toString());
        event.setRequest(request);

        callback.onEvent(event);
        Mockito.verify(_fetch).continueRequest(
                Mockito.eq(event.getRequestId()),
                Mockito.eq("https://whitelisted.com/allowed-req-1"),
                Mockito.eq("POST"),
                Mockito.eq("data"),
                Mockito.any()
        );

        request.setUrl("https://whitelisted.com/disallowed-path");
        Mockito.reset(_fetch);
        callback.onEvent(event);
        Mockito.verify(_fetch).failRequest(
                event.getRequestId(),
                ErrorReason.ABORTED
        );

        request.setUrl("https://not-whitelisted.com/allowed-req-1");
        Mockito.reset(_fetch);
        callback.onEvent(event);
        Mockito.verify(_fetch).failRequest(
                event.getRequestId(),
                ErrorReason.ABORTED
        );
    }

    @Test
    public void testFetchAddsHeaders() {
        DevToolsNetworkConfigurationProtocol.FETCH.configure(_dts, _originConfigs);
        Mockito.verify(_fetch).onRequestPaused(_requestPausedCaptor.capture());
        final EventHandler<RequestPaused> callback = _requestPausedCaptor.getValue();

        final Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://whitelisted.com/allowed-req-1");
        request.setPostData("data");
        request.setHeaders(ImmutableMap.of("Original-Header", "original header value"));
        final RequestPaused event = new RequestPaused();
        event.setRequest(request);
        event.setRequestId(UUID.randomUUID().toString());

        callback.onEvent(event);
        Mockito.verify(_fetch).continueRequest(
                Mockito.eq(event.getRequestId()),
                Mockito.eq("https://whitelisted.com/allowed-req-1"),
                Mockito.eq("POST"),
                Mockito.eq("data"),
                Mockito.argThat(headers -> DevToolsNetworkConfigurationProtocol.headerListToMap(headers).equals(ImmutableMap.of(
                        "Original-Header", "original header value",
                        "X-Extra-Header", "extra header value"
                        ))
                )
        );
    }

}
