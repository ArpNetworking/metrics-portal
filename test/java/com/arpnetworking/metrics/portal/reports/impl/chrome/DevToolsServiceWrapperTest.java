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
    private com.github.kklisura.cdt.services.ChromeDevToolsService _wrapped;

    private DevToolsService _dts;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Mockito.doReturn(_page).when(_wrapped).getPage();
        _tab = new com.github.kklisura.cdt.services.types.ChromeTab();
        _dts = new DevToolsServiceWrapper(_service, _tab, _wrapped, new ScheduledThreadPoolExecutor(1));
    }

    @Test
    public void testCancellingNavigateFreesThread() {
        Mockito.doAnswer(args -> {
            Thread.sleep(99999);
            return null;
        }).when(_page).navigate(Mockito.anyString());

        final CompletableFuture<Void> navigate = _dts.navigate("http://example.com");
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

}
