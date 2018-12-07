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
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests class <code>EmailReportSink</code>.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class ChromeScreenshotReportGeneratorTest {

    @Test
    public void testSend() throws IOException {
//        ChromeDevToolsService dts = Mockito.mock(ChromeDevToolsService.class);
//
//        AtomicInteger nChecks = new AtomicInteger(0);
//        ChromeScreenshotReportSpec generator = new ChromeScreenshotReportSpec(
//                "http://example.com",
//                "Report Title",
//                _dts -> (nChecks.getAndIncrement() > 0),
//                true,
//                10000,
//                8.5,
//                11.0
//        );
//        EmailReportSink sink = new EmailReportSink("recip@invalid", mailer);
//
//        sink.send(new Report("Today's P75 TTI", "my html", "my pdf".getBytes()));
//        Mockito.verify(mailer).sendMail(message.capture());
//
//        Assert.assertEquals("[Report] Today's P75 TTI", message.getValue().getSubject());
//        Assert.assertEquals("my html", message.getValue().getHTMLText());
//        Assert.assertEquals("my pdf", message.getValue().getAttachments().get(0).readAllData());
    }

}
