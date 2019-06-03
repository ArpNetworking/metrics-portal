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
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.email.EmailPopulatingBuilder;
import org.simplejavamail.mailer.Mailer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
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

    private String sendSync(
            final Report report,
            final Recipient recipient,
            final ImmutableMap<ReportFormat, RenderedReport> formatsToSend,
            final Instant scheduled
    ) throws IOException {

        final String subject = getSubject(report, scheduled);
        EmailPopulatingBuilder builder = EmailBuilder.startingBlank()
                .from("no-reply+amp-reporting@dropbox.com")
                .to(recipient.getAddress())
                .withSubject(subject);

        for (Map.Entry<ReportFormat, RenderedReport> entry : formatsToSend.entrySet()) {
            builder = addFormat(builder, entry.getKey(), entry.getValue());
        }

        LOGGER.info()
                .setMessage("sending email")
                .addData("report", report)
                .addData("recipient", recipient)
                .log();

        _mailer.sendMail(builder.buildEmail());
        return "";
    }

    private String getSubject(final Report report, final Instant scheduled) {
        final String formattedTime = ZonedDateTime.ofInstant(scheduled, ZoneId.of("UTC")).toString();
        return "[Report] " + report.getName() + " for " + formattedTime;
    }

    private EmailPopulatingBuilder addFormat(
            final EmailPopulatingBuilder builder,
            final ReportFormat format,
            final RenderedReport rendered
    ) throws IOException {
        final String mimeType = format.getMimeType();
        final InputStream content = rendered.getBytes();
        if (mimeType.equals("text/html")) {
            return builder.withHTMLText(readString(content));
        }
        return builder.withAttachment("report", readBytes(content), mimeType);
    }

    /**
     * Public constructor.
     *
     * @param mailer The Mailer to send emails through.
     */
    @Inject
    public EmailSender(final Mailer mailer) {
        _mailer = mailer;
    }

    private static byte[] readBytes(final InputStream stream) throws IOException {
        return ByteStreams.toByteArray(stream);
    }

    private static String readString(final InputStream stream) throws IOException {
        return CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private final Mailer _mailer;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);
}
