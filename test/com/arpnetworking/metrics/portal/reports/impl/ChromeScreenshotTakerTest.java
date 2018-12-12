/*
 * Copyright 2018 Dropbox
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
package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Report;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Tests class <code>EmailReportSink</code>.
 *
 * @author Spencer Pearson
 */
public class ChromeScreenshotTakerTest {

    @Captor
    ArgumentCaptor<Runnable> loadHandler;
    @Captor
    ArgumentCaptor<Consumer<String>> logHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCallbackRegistration() throws InterruptedException {
        ChromeDevToolsService dts = Mockito.mock(ChromeDevToolsService.class);
        Mockito.doReturn("page html").when(dts).evaluate("document.body.outerHTML");
        Mockito.doReturn("page pdf".getBytes()).when(dts).printToPdf(8.5, 11.0);

        ChromeScreenshotReportSpec spec = new ChromeScreenshotReportSpec(
                "http://foo.bar.com:8080/baz?quuz",
                true,
                "console.log('here is some js')",
                "someevent",
                Duration.of(1, ChronoUnit.MINUTES),
                8.5,
                11.0
        );

        final AtomicReference<Report> r = new AtomicReference<>();
        CompletionStage<Void> cs = new ChromeScreenshotTaker().render(spec, dts).thenAccept(r::set);

        Mockito.verify(dts, Mockito.never()).evaluate("(() => {console.log('here is some js')})()");
        Mockito.verify(dts).onLoad(loadHandler.capture());
        loadHandler.getValue().run();
        Mockito.verify(dts).evaluate("(() => {console.log('here is some js')})()");
        dts.evaluate("window.addEventListener(\"someevent\", () => console.log(\""+ChromeScreenshotTaker.TRIGGER_MESSAGE+"\"))");

        Mockito.verify(dts).onLog(logHandler.capture());

        Assert.assertNull(r.get());
        logHandler.getValue().accept("meaningless message");
        Assert.assertNull(r.get());

        Mockito.verify(dts, Mockito.never()).close();
        logHandler.getValue().accept(ChromeScreenshotTaker.TRIGGER_MESSAGE);
        Assert.assertNotNull(r.get());
        Mockito.verify(dts).close();
    }

}
