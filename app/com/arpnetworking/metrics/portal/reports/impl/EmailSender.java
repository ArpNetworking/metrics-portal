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
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import models.internal.Problem;
import models.internal.TimeRange;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import org.simplejavamail.api.email.EmailPopulatingBuilder;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Sends reports over email.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class EmailSender implements Sender {
    @Override
    public CompletionStage<Void> send(
            final Recipient recipient,
            final ImmutableMap<ReportFormat, RenderedReport> formatsToSend
    ) {
        if (_allowedRecipients.stream().noneMatch(pattern -> pattern.matcher(recipient.getAddress()).matches())) {
            throw new IllegalArgumentException("not allowed to send to recipient address " + recipient.getAddress());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                sendSync(recipient, formatsToSend);
            } catch (final IOException e) {
                throw new CompletionException(e);
            }
            return null;
        });
    }

    @Override
    public ImmutableList<Problem> validateSend(final Recipient recipient, final ImmutableCollection<ReportFormat> formatsToSend) {
        if (_allowedRecipients.stream().noneMatch(pattern -> pattern.matcher(recipient.getAddress()).matches())) {
            return ImmutableList.of(new Problem.Builder()
                    .setProblemCode("report_problem.DISALLOWED_EMAIL_ADDRESS")
                    .setArgs(ImmutableList.of(recipient.getAddress(), _allowedRecipients.toString()))
                    .build()
            );
        }
        return ImmutableList.of();
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
        return String.format("[Report] %s for %s", report.getName(), formattedTime); // TODO(spencerpearson): make format configurable
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
     * @param mapper The {@link ObjectMapper} to use to deserialize parts of the configuration.
     */
    @Inject
    public EmailSender(@Assisted final Config config, final ObjectMapper mapper) {
        this(buildMailer(config), config.getString("fromAddress"), buildAllowedRecipients(config, mapper));
    }

    /**
     * Constructor for tests, allowing dependency injection of the {@link Mailer}.
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Protected as mentioned at "
        + "https://wiki.sei.cmu.edu/confluence/display/java/OBJ11-J.+Be+wary+of+letting+constructors+throw+exceptions")
    /* package private */ EmailSender(final Mailer mailer, final Config config, final ObjectMapper mapper) {
        this(mailer, config.getString("fromAddress"), buildAllowedRecipients(config, mapper));
    }

    private EmailSender(final Mailer mailer, final String fromAddress, final ImmutableSet<Pattern> allowedRecipients) {
        _fromAddress = fromAddress;
        _allowedRecipients = allowedRecipients;
        _mailer = mailer;
    }

    private static ImmutableSet<Pattern> buildAllowedRecipients(final Config config, final ObjectMapper mapper) {
        try {
            return mapper.readValue(ConfigurationHelper.toJson(config, ALLOWED_RECIPIENTS_KEY), ALLOWED_RECIPIENTS_TYPE);
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
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
    private final ImmutableSet<Pattern> _allowedRecipients;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);
    private static final String ALLOWED_RECIPIENTS_KEY = "allowedRecipients";
    private static final TypeReference<ImmutableSet<Pattern>> ALLOWED_RECIPIENTS_TYPE = new TypeReference<ImmutableSet<Pattern>>() {};
}
