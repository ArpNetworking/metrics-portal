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
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.email.EmailPopulatingBuilder;
import org.simplejavamail.mailer.Mailer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Sends reports over email.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class EmailSender implements Sender {
    @Override
    public CompletionStage<Void> send(
            final Report report,
            final Recipient recipient,
            final ImmutableMap<ReportFormat, RenderedReport> formatsToSend,
            final Instant scheduled
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                sendSync(report, recipient, formatsToSend, scheduled);
            } catch (final IOException e) {
                throw new CompletionException(e);
            }
            return null;
        });
    }

    private void sendSync(
            final Report report,
            final Recipient recipient,
            final ImmutableMap<ReportFormat, RenderedReport> formatsToSend,
            final Instant scheduled
    ) throws IOException {

        final String subject = getSubject(report, scheduled);
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

    private String getSubject(final Report report, final Instant scheduled) {
        final String formattedTime = ZonedDateTime.ofInstant(scheduled, ZoneOffset.UTC).toString();
        return "[Report] " + report.getName() + " for " + formattedTime;
    }

    private EmailPopulatingBuilder addFormat(
            final EmailPopulatingBuilder builder,
            final RenderedReport rendered
    ) throws IOException {
        final String mimeType = rendered.getFormat().getMimeType();
        final ByteSource content = rendered.getBytes();
        if (mimeType.equals(MediaType.HTML_UTF_8.toString())) {
            return builder.withHTMLText(content.asCharSource(StandardCharsets.UTF_8).read());
        }
        return builder.withAttachment("report", content.read(), mimeType);
    }

    /**
     * Public constructor.
     *
     * @param mailer The {@link Mailer} to send emails through.
     * @param config The configuration for this sender.
     */
    @Inject
    public EmailSender(final Mailer mailer, @Assisted final Config config) {
        _mailer = mailer;
        _fromAddress = config.getString("fromAddress");
    }

    private final Mailer _mailer;
    private final String _fromAddress;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);
}
