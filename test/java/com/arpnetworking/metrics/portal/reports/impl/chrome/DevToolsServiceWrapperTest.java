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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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
    private com.github.kklisura.cdt.protocol.commands.Fetch _fetch;
    @Mock
    private com.github.kklisura.cdt.services.ChromeDevToolsService _wrapped;

    private DevToolsService _dts;
    private AutoCloseable _mocks;

    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        Mockito.doReturn(_page).when(_wrapped).getPage();
        Mockito.doReturn(_fetch).when(_wrapped).getFetch();
        _tab = new com.github.kklisura.cdt.services.types.ChromeTab();
        _dts = new DevToolsServiceWrapper(
                _service,
                new PerOriginConfigs.Builder().setByOrigin(ImmutableMap.of(
                        "https://whitelisted.com", new OriginConfig.Builder()
                                .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-.*"))
                                .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-.*"))
                                .setAdditionalHeaders(ImmutableMap.of("X-Extra-Header", "extra header value"))
                                .build()
                )).build(),
                _tab,
                _wrapped,
                new ScheduledThreadPoolExecutor(1),
                DevToolsNetworkConfigurationProtocol.FETCH
        );
        Mockito.verify(_fetch).onRequestPaused(Mockito.any());
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
                Mockito.anyBoolean(),
                Mockito.any()
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
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("navigation is not allowed"));
        }
    }

}
