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
import com.arpnetworking.metrics.portal.reports.ReportSink;
import com.google.inject.Inject;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.email.EmailPopulatingBuilder;
import org.simplejavamail.mailer.Mailer;

import java.util.concurrent.CompletableFuture;

public class EmailReportSink implements ReportSink {
    private String recipient;
    private Mailer mailer;

    public EmailReportSink(String recipient, Mailer mailer) {
        this.recipient = recipient;
        this.mailer = mailer;
    }

    @Override
    public CompletableFuture<Void> send(Report r) {
        EmailPopulatingBuilder builder = EmailBuilder.startingBlank()
                .from("no-reply+amp-reporting@dropbox.com")
                .to(recipient)
                .withSubject("[Report] "+r.getTitle());
        if (r.getHtml() != null) builder = builder.withHTMLText(r.getHtml());
        if (r.getPdf() != null) builder = builder.withAttachment("report", r.getPdf(), "application/pdf");
        mailer.sendMail(builder.buildEmail());
        return new CompletableFuture<>();
    }
}
