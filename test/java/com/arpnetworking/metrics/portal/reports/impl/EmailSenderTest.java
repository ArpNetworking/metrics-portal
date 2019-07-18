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
package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.dumbster.smtp.SimpleSmtpServer;
import com.dumbster.smtp.SmtpMessage;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.internal.TimeRange;
import models.internal.impl.HtmlReportFormat;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.simplejavamail.MailException;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Tests class {@link EmailSender}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class EmailSenderTest {

    private static final Instant T0 = Instant.parse("2019-01-01T00:00:00.000Z");

    private Mailer _mailer;
    private EmailSender _sender;
    private SimpleSmtpServer _server;
    private Config _config;

    @Before
    public void setUp() throws IOException {
        _server = SimpleSmtpServer.start(getFreePort());
        _mailer = MailerBuilder.withSMTPServer("localhost", _server.getPort()).buildMailer();
        _config = ConfigFactory.parseMap(ImmutableMap.of(
                "type", "com.arpnetworking.metrics.portal.reports.impl.EmailSender",
                "fromAddress", "me@invalid.net"
        ));

        MockitoAnnotations.initMocks(this);
        _sender = new EmailSender(_mailer, _config);
    }

    @Test
    public void testSend() throws InterruptedException, ExecutionException {
        final RenderedReport report = TestBeanFactory.createRenderedReportBuilder()
                .setReport(TestBeanFactory.createReportBuilder().setName("P75 TTI").build())
                .setFormat(new HtmlReportFormat.Builder().build())
                .setBytes("report content".getBytes(StandardCharsets.UTF_8))
                .setTimeRange(new TimeRange(T0, T0.plus(Duration.ofDays(1))))
                .build();
        _sender.send(
                TestBeanFactory.createRecipient(),
                ImmutableMap.of(report.getFormat(), report),
                Duration.ofSeconds(1)
        ).toCompletableFuture().get();

        final List<SmtpMessage> emails = _server.getReceivedEmails();
        Assert.assertEquals(1, emails.size());
        final SmtpMessage email = emails.get(0);

        Assert.assertEquals("[Report] P75 TTI for 2019-01-01T00:00Z", email.getHeaderValue("Subject"));
        Assert.assertTrue(email.getBody().contains("report content")); // server provides no good way to extract message parts
    }

    @Test
    public void testNoEmailIsSentWithNoFormats() throws InterruptedException, ExecutionException {
        _sender.send(
                TestBeanFactory.createRecipient(),
                ImmutableMap.of(),
                Duration.ofSeconds(1)
        ).toCompletableFuture().get();

        Assert.assertEquals(0, _server.getReceivedEmails().size());
    }

    @Test(expected = MailException.class)
    public void testSendFailsIfExceptionThrown() throws MailException, ExecutionException, InterruptedException {
        _server.stop(); // so we should get an exception when trying to connect to it
        final RenderedReport report = TestBeanFactory.createRenderedReportBuilder().build();
        try {
            _sender.send(
                    TestBeanFactory.createRecipient(),
                    ImmutableMap.of(new HtmlReportFormat.Builder().build(), report),
                    Duration.ofSeconds(1)
            ).toCompletableFuture().get();
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof MailException) {
                throw (MailException) e.getCause();
            }
            throw e;    
        }
    }

    @Test
    public void testTimeout() throws Exception {
        final Mailer mailer = Mockito.mock(Mailer.class);
        Mockito.doAnswer(x -> {
            Thread.sleep(100000);
            return null;
        }).when(mailer).sendMail(Mockito.any());
        final EmailSender sender = new EmailSender(mailer, _config);
        final CompletionStage<Void> send = sender.send(
                TestBeanFactory.createRecipient(),
                ImmutableMap.of(new HtmlReportFormat.Builder().build(), TestBeanFactory.createRenderedReportBuilder().build()),
                Duration.ofMillis(500)
        );

        boolean cancelled = false;
        try {
            send.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (final CancellationException exception) {
            cancelled = true;
        }
        Assert.assertTrue("send().get() should have thrown a CancellationException", cancelled);
    }

    private static int getFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
