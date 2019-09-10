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

import com.github.kklisura.cdt.protocol.events.network.RequestIntercepted;
import com.github.kklisura.cdt.protocol.support.types.EventHandler;
import com.github.kklisura.cdt.protocol.types.network.ErrorReason;
import com.github.kklisura.cdt.protocol.types.network.Request;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Tests for {@link BaseScreenshotRenderer}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class DevToolsServiceWrapperTest {

    @Mock
    private com.github.kklisura.cdt.services.ChromeService _service;
    private com.github.kklisura.cdt.services.types.ChromeTab _tab;
    @Mock
    private com.github.kklisura.cdt.protocol.commands.Page _page;
    @Mock
    private com.github.kklisura.cdt.protocol.commands.Network _network;
    @Captor
    private ArgumentCaptor<EventHandler<RequestIntercepted>> _requestInterceptorCaptor;
    private EventHandler<RequestIntercepted> _requestInterceptor;
    @Mock
    private com.github.kklisura.cdt.services.ChromeDevToolsService _wrapped;

    private DevToolsService _dts;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Mockito.doReturn(_page).when(_wrapped).getPage();
        Mockito.doReturn(_network).when(_wrapped).getNetwork();
        _tab = new com.github.kklisura.cdt.services.types.ChromeTab();
        _dts = new DevToolsServiceWrapper(
                _service,
                ImmutableMap.of(
                        "https://whitelisted.com", new OriginConfig.Builder()
                                .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-.*"))
                                .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-.*"))
                                .setAdditionalHeaders(ImmutableMap.of("X-Extra-Header", "extra header value"))
                                .build()
                ),
                _tab,
                _wrapped,
                new ScheduledThreadPoolExecutor(1)
        );
        Mockito.verify(_network).onRequestIntercepted(_requestInterceptorCaptor.capture());
        _requestInterceptor = _requestInterceptorCaptor.getValue();
    }

    @Test
    public void testCancellingNavigateFreesThread() {
        Mockito.doAnswer(args -> {
            Thread.sleep(99999);
            return null;
        }).when(_page).navigate(Mockito.anyString());

        final CompletableFuture<Void> navigate = _dts.navigate("https://whitelisted.com/allowed-nav-1");
        _dts.close();

        Mockito.verify(_service, Mockito.after(500).never()).closeTab(Mockito.any());
        navigate.cancel(true);
        Mockito.verify(_service, Mockito.timeout(500)).closeTab(_tab);
    }

    @Test
    public void testCancellingPrintFreesThread() {
        Mockito.doAnswer(args -> {
            Thread.sleep(99999);
            return null;
        }).when(_page).printToPDF(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyDouble(),
                Mockito.anyDouble(),
                Mockito.anyDouble(),
                Mockito.anyDouble(),
                Mockito.anyDouble(),
                Mockito.anyDouble(),
                Mockito.anyDouble(),
                Mockito.anyString(),
                Mockito.anyBoolean(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyBoolean()
        );

        final CompletableFuture<byte[]> print = _dts.printToPdf(1d, 2d);
        _dts.close();

        Mockito.verify(_service, Mockito.after(500).never()).closeTab(Mockito.any());
        print.cancel(true);
        Mockito.verify(_service, Mockito.timeout(500)).closeTab(_tab);
    }

    @Test
    public void testFiltersNavigation() {
        Assert.assertTrue(_dts.isNavigationAllowed("https://whitelisted.com/allowed-nav-1"));
        Assert.assertFalse(_dts.isNavigationAllowed("https://whitelisted.com/disallowed-path"));
        Assert.assertFalse(_dts.isNavigationAllowed("https://not-whitelisted.com/allowed-nav-1"));
        _dts.navigate("https://whitelisted.com/allowed-nav-1");
        _dts.navigate("https://whitelisted.com/allowed-nav-2");
        _dts.navigate("https://whitelisted.com/allowed-nav-3");
        try {
            _dts.navigate("https://whitelisted.com/disallowed-path");
            Assert.fail("navigate() to illegal path should have raised an exception");
        } catch (final IllegalArgumentException e) {}
    }

    @Test
    public void testFiltersRequests() {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://whitelisted.com/allowed-req-1");
        request.setPostData("data");
        request.setHeaders(ImmutableMap.of());
        final RequestIntercepted event = new RequestIntercepted();
        event.setInterceptionId(UUID.randomUUID().toString());
        event.setRequest(request);

        _requestInterceptor.onEvent(event);
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
        _requestInterceptor.onEvent(event);
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
        _requestInterceptor.onEvent(event);
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
    public void testAddsHeaders() {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://whitelisted.com/allowed-req-1");
        request.setPostData("data");
        request.setHeaders(ImmutableMap.of("Original-Header", "original header value"));
        final RequestIntercepted event = new RequestIntercepted();
        event.setRequest(request);
        event.setInterceptionId(UUID.randomUUID().toString());

        _requestInterceptor.onEvent(event);
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

}
