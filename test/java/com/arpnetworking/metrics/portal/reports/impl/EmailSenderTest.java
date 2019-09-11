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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.dumbster.smtp.SimpleSmtpServer;
import com.dumbster.smtp.SmtpMessage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import models.internal.TimeRange;
import models.internal.impl.HtmlReportFormat;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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
import java.util.concurrent.ExecutionException;

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

    @Before
    public void setUp() throws IOException {
        _server = SimpleSmtpServer.start(getFreePort());
        _mailer = MailerBuilder.withSMTPServer("localhost", _server.getPort()).buildMailer();
        MockitoAnnotations.initMocks(this);
        _sender = new EmailSender(
                _mailer,
                ConfigFactory.parseMap(ImmutableMap.of(
                        "type", "com.arpnetworking.metrics.portal.reports.impl.EmailSender",
                        "fromAddress", "me@invalid.net",
                        "allowedRecipients", ImmutableList.of(".+@example\\.com")
                )),
                ObjectMapperFactory.createInstance()
        );
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
                TestBeanFactory.createRecipientBuilder().build(),
                ImmutableMap.of(report.getFormat(), report)
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
                TestBeanFactory.createRecipientBuilder().build(),
                ImmutableMap.of()
        ).toCompletableFuture().get();

        Assert.assertEquals(0, _server.getReceivedEmails().size());
    }

    @Test(expected = MailException.class)
    public void testSendFailsIfExceptionThrown() throws MailException, ExecutionException, InterruptedException {
        _server.stop(); // so we should get an exception when trying to connect to it
        final RenderedReport report = TestBeanFactory.createRenderedReportBuilder().build();
        try {
            _sender.send(
                    TestBeanFactory.createRecipientBuilder().build(),
                    ImmutableMap.of(new HtmlReportFormat.Builder().build(), report)
            ).toCompletableFuture().get();
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof MailException) {
                throw (MailException) e.getCause();
            }
            throw e;    
        }
    }

    @Test
    public void testSendRejectsIllegalRecipients() {
        final ImmutableList<String> legalAddresses = ImmutableList.of("alice@example.com", "bob@example.com");
        for (final String address : legalAddresses) {
            _sender.send(TestBeanFactory.createRecipientBuilder().setAddress(address).build(), ImmutableMap.of());
        }

        final ImmutableList<String> illegalAddresses = ImmutableList.of("", "@example.com", "charlie@example.com.ru");
        for (final String address : illegalAddresses) {
            try {
                _sender.send(TestBeanFactory.createRecipientBuilder().setAddress(address).build(), ImmutableMap.of());
                Assert.fail("should have refused to send to '" + address + "'");
            } catch (final IllegalArgumentException e) {
            }
        }
    }

    private static int getFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
