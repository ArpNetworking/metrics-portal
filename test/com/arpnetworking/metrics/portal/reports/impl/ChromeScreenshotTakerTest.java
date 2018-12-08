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
import com.github.kklisura.cdt.protocol.support.types.EventHandler;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests class <code>EmailReportSink</code>.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class ChromeScreenshotTakerTest {

    @Captor
    ArgumentCaptor<EventHandler<Object>> handler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCallbackRegistration() throws InterruptedException {
        ChromeDevToolsService dts = Mockito.mock(ChromeDevToolsService.class);

        ChromeScreenshotReportSpec spec = new ChromeScreenshotReportSpec(
                "http://foo.bar.com:8080/baz?quuz",
                "Report Title",
                true,
                "someevent",
                Duration.of(1, ChronoUnit.MINUTES),
                8.5,
                11.0
        );

        final AtomicReference<Report> r = new AtomicReference<>();
        CompletionStage<Void> cs = new ChromeScreenshotTaker().render(spec, dts).thenAccept(r::set);

        Mockito.verify(dts).addEventListener("foo.bar.com:8080", "someevent", handler.capture(), Object.class);

        cs.wait(100);
        Assert.assertNull(r.get());
        handler.getValue().onEvent(new Object());
        cs.wait(100);
        Assert.assertNotNull(r.get());
    }

}
