/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.reports;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.reports.impl.EmailSender;
import com.google.common.collect.ImmutableMap;
import models.internal.impl.DefaultRenderedReport;
import models.internal.impl.HtmlReportFormat;
import models.internal.reports.ReportFormat;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.simplejavamail.email.Email;
import org.simplejavamail.mailer.Mailer;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

/**
 * Tests class <code>EmailReportSink</code>.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class EmailSenderTest {

    private static final Instant T0 = Instant.parse("2019-01-01T00:00:00.000Z");

    @Captor
    private ArgumentCaptor<Email> message;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSend() throws InterruptedException, ExecutionException {
        Mailer mailer = Mockito.mock(Mailer.class);
        final EmailSender sender = new EmailSender(mailer);
        final ReportFormat format = new HtmlReportFormat.Builder().build();
        final RenderedReport rendered = new DefaultRenderedReport.Builder()
                .setFormat(format)
                .setGeneratedAt(T0)
                .setScheduledFor(T0)
                .setBytes("my html".getBytes())
                .build();

        sender.send(
                TestBeanFactory.createReportBuilder().setName("P75 TTI").build(),
                TestBeanFactory.createRecipient(),
                ImmutableMap.of(format, rendered),
                Instant.parse("2019-01-01T00:00:00.000Z")
        ).toCompletableFuture().get();
        Mockito.verify(mailer).sendMail(message.capture());

        Assert.assertEquals("[Report] P75 TTI for 2019-01-01T00:00Z[UTC]", message.getValue().getSubject());
        Assert.assertEquals("my html", message.getValue().getHTMLText());
    }

}