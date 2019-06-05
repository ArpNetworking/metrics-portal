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
package com.arpnetworking.metrics.portal.reports;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.reports.impl.EmailSender;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.typesafe.config.ConfigFactory;
import models.internal.impl.HtmlReportFormat;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.simplejavamail.email.Email;
import org.simplejavamail.mailer.Mailer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

/**
 * Tests class {@link EmailSender}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class EmailSenderTest {

    private static final Instant T0 = Instant.parse("2019-01-01T00:00:00.000Z");

    @Captor
    private ArgumentCaptor<Email> _message;
    @Mock
    private Mailer _mailer;
    private EmailSender _sender;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        _sender = new EmailSender(_mailer, ConfigFactory.parseMap(ImmutableMap.of(
                "type", "com.arpnetworking.metrics.portal.reports.impl.EmailSender",
                "fromAddress", "me@invalid.net"
        )));
    }

    @Test
    public void testSend() throws InterruptedException, ExecutionException {
        final RenderedReport report = TestBeanFactory.createRenderedReportBuilder()
                .setFormat(new HtmlReportFormat.Builder().build())
                .setBytes("report content".getBytes(StandardCharsets.UTF_8))
                .build();
        _sender.send(
                TestBeanFactory.createReportBuilder().setName("P75 TTI").build(),
                TestBeanFactory.createRecipient(),
                ImmutableMap.of(report.getFormat(), report),
                Instant.parse("2019-01-01T00:00:00.000Z")
        ).toCompletableFuture().get();

        Mockito.verify(_mailer).sendMail(_message.capture());
        Assert.assertEquals("[Report] P75 TTI for 2019-01-01T00:00Z", _message.getValue().getSubject());
        Assert.assertEquals("report content", _message.getValue().getHTMLText());
    }

    @Test
    public void testSendSucceedsWithNoFormats() throws InterruptedException, ExecutionException {
        _sender.send(
                TestBeanFactory.createReportBuilder().setName("P75 TTI").build(),
                TestBeanFactory.createRecipient(),
                ImmutableMap.of(),
                Instant.parse("2019-01-01T00:00:00.000Z")
        ).toCompletableFuture().get();

        Mockito.verify(_mailer).sendMail(_message.capture());
        Assert.assertEquals("[Report] P75 TTI for 2019-01-01T00:00Z", _message.getValue().getSubject());
        Assert.assertNull(_message.getValue().getHTMLText());
        Assert.assertEquals(Lists.newArrayList(), _message.getValue().getAttachments());
    }

    @Test(expected = MailException.class)
    public void testSendFailsIfExceptionThrown() throws MailException, InterruptedException {
        Mockito.doThrow(new MailException()).when(_mailer).sendMail(Mockito.any());
        try {
            _sender.send(
                    TestBeanFactory.createReportBuilder().setName("P75 TTI").build(),
                    TestBeanFactory.createRecipient(),
                    ImmutableMap.of(),
                    Instant.parse("2019-01-01T00:00:00.000Z")
            ).toCompletableFuture().get();
        } catch (final ExecutionException e) {
            throw (MailException) e.getCause();
        }
    }

    private static final class MailException extends RuntimeException {}
}
