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

import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.arpnetworking.metrics.portal.reports.Sender;
import com.arpnetworking.metrics.portal.reports.impl.chrome.ChromeReportRenderingExecutorService;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import models.internal.TimeRange;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.email.EmailPopulatingBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends reports over email.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class EmailSender implements Sender {

    @Override
    public CompletionStage<Void> send(
            final Recipient recipient,
            final ImmutableMap<ReportFormat, RenderedReport> formatsToSend,
            final Duration timeout
    ) {
        final CompletableFuture<Void> result = CompletableFuture.supplyAsync(() -> {
            try {
                sendSync(recipient, formatsToSend);
            } catch (final IOException e) {
                throw new CompletionException(e);
            }
            return null;
        });

        _executor.schedule(() -> result.cancel(true), timeout.toNanos(), TimeUnit.NANOSECONDS);

        return result;
    }

    private void sendSync(
            final Recipient recipient,
            final ImmutableMap<ReportFormat, RenderedReport> formatsToSend
    ) throws IOException {
        if (formatsToSend.isEmpty()) {
            return;
        }
        final Report report = formatsToSend.values().iterator().next().getReport();
        final TimeRange timeRange = formatsToSend.values().iterator().next().getTimeRange();
        final String subject = getSubject(report, timeRange);
        EmailPopulatingBuilder builder = EmailBuilder.startingBlank()
                .from(_fromAddress)
                .to(recipient.getAddress())
                .withSubject(subject);

        for (final RenderedReport rendered : formatsToSend.values()) {
            builder = addFormat(builder, rendered);
        }

        LOGGER.info()
                .setMessage("sending email")
                .addData("report", report)
                .addData("recipient", recipient)
                .log();

        _mailer.sendMail(builder.buildEmail());
    }

    private String getSubject(final Report report, final TimeRange timeRange) {
        final String formattedTime = ZonedDateTime.ofInstant(timeRange.getStart(), ZoneOffset.UTC).toString();
        return "[Report] " + report.getName() + " for " + formattedTime; // TODO(spencerpearson): make format configurable
    }

    private EmailPopulatingBuilder addFormat(
            final EmailPopulatingBuilder builder,
            final RenderedReport rendered
    ) throws IOException {
        final MediaType mimeType = rendered.getFormat().getMimeType();
        final ByteSource content = rendered.getBytes();
        if (mimeType.equals(MediaType.HTML_UTF_8)) {
            return builder.withHTMLText(content.asCharSource(StandardCharsets.UTF_8).read());
        }
        return builder.withAttachment("report", content.read(), mimeType.toString());
    }

    /**
     * Public constructor.
     *
     * @param config The configuration for this sender.
     * @param executor Used to schedule timeouts on individual send operations.
     */
    @Inject
    public EmailSender(@Assisted final Config config, @ChromeReportRenderingExecutorService final ScheduledExecutorService executor) {
        this(buildMailer(config), config, executor);
    }

    /**
     * Constructor for tests, allowing dependency injection of the {@link Mailer}.
     */
    /* package private */ EmailSender(final Mailer mailer, final Config config, final ScheduledExecutorService executor) {
        _fromAddress = config.getString("fromAddress");
        _mailer = mailer;
        _executor = executor;
    }

    private static Mailer buildMailer(final Config config) {
        return MailerBuilder
                .withSMTPServer(
                        config.hasPath("smtp.host") ? config.getString("smtp.host") : "localhost",
                        config.hasPath("smtp.port") ? config.getInt("smtp.port") : 25
                )
                .buildMailer();
    }

    private final Mailer _mailer;
    private final String _fromAddress;
    private final ScheduledExecutorService _executor;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);
}
