/*
 * Copyright 2019 Dropbox
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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.impl.DefaultRecipient;
import models.internal.impl.DefaultRenderedReport;
import models.internal.impl.DefaultReport;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;
import models.internal.impl.WebPageReportSource;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * Tests for {@link ReportExecutionContext}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class ReportExecutionContextTest {

    private static final Recipient ALICE = new DefaultRecipient.Builder()
            .setId(UUID.randomUUID())
            .setAddress("alice@invalid.com")
            .setType(RecipientType.EMAIL)
            .build();
    private static final Recipient BOB = new DefaultRecipient.Builder()
            .setId(UUID.randomUUID())
            .setAddress("bob@invalid.com")
            .setType(RecipientType.EMAIL)
            .build();

    private static final HtmlReportFormat HTML = new HtmlReportFormat.Builder().build();
    private static final PdfReportFormat PDF = new PdfReportFormat.Builder().setWidthInches(8.5f).setHeightInches(11f).build();
    private static final Instant T0 = Instant.now();
    private static final Report EXAMPLE_REPORT = new DefaultReport.Builder()
            .setId(UUID.randomUUID())
            .setName("My Name")
            .setReportSource(new WebPageReportSource.Builder()
                    .setId(UUID.randomUUID())
                    .setTitle("My Report Title")
                    .setTriggeringEventName("myTriggeringEventName")
                    .setUri(URI.create("https://example.com"))
                    .setIgnoreCertificateErrors(true)
                    .build()
            )
            .setSchedule(new OneOffSchedule.Builder().setRunAtAndAfter(T0).build())
            .setRecipients(ImmutableSetMultimap.<ReportFormat, Recipient>builder()
                    .put(HTML, ALICE)
                    .put(HTML, BOB)
                    .put(PDF, BOB)
                    .build()
            )
            .build();


    @Mock
    private MockEmailSender _emailSender;
    private ObjectMapper _objectMapper;
    private Config _config;

    @Before
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED")
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Mockito.doReturn(CompletableFuture.completedFuture("done")).when(_emailSender).send(Mockito.any(), Mockito.any());

        _objectMapper = ObjectMapperFactory.createInstance();
        _objectMapper.registerModule(new SimpleModule() {
            @Override
            public void setupModule(final SetupContext context) {
                addDeserializer(MockEmailSender.class, new JsonDeserializer<MockEmailSender>() {
                    @Override
                    public MockEmailSender deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) {
                        return _emailSender;
                    }
                });
                super.setupModule(context);
            }
        });
        _config = ConfigFactory.parseMap(ImmutableMap.of(
                // CHECKSTYLE.OFF: LineLength
                "reports.renderers.web_page.\"text/html\".type", "com.arpnetworking.metrics.portal.reports.ReportExecutionContextTest$MockHtmlRenderer",
                "reports.renderers.web_page.\"application/pdf\".type", "com.arpnetworking.metrics.portal.reports.ReportExecutionContextTest$MockPdfRenderer",
                "reports.senders.EMAIL.type", "com.arpnetworking.metrics.portal.reports.ReportExecutionContextTest$MockEmailSender"
                // CHECKSTYLE.ON: LineLength
        ));
    }

    @Test
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED")
    public void testExecute() throws Exception {
        final ReportExecutionContext context = new ReportExecutionContext(_objectMapper, _config);
        context.execute(EXAMPLE_REPORT, T0).toCompletableFuture().get();
        Mockito.verify(_emailSender).send(ALICE, ImmutableMap.of(HTML, mockRendered(HTML, T0)));
        Mockito.verify(_emailSender).send(BOB, ImmutableMap.of(HTML, mockRendered(HTML, T0), PDF, mockRendered(PDF, T0)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteThrowsIfNoRendererFound() throws InterruptedException, IOException {
        final ReportExecutionContext context = new ReportExecutionContext(
                _objectMapper,
                _config.withoutPath("reports.renderers.web_page.\"text/html\"")
        );
        unwrapAsyncThrow(context.execute(EXAMPLE_REPORT, T0), IllegalArgumentException.class);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
    public void testExecuteThrowsIfNoSenderFound() throws  IllegalArgumentException, InterruptedException, IOException {
        final ReportExecutionContext context = new ReportExecutionContext(
                _objectMapper,
                _config.withoutPath("reports.senders.EMAIL")
        );
        unwrapAsyncThrow(context.execute(EXAMPLE_REPORT, T0), ConfigurationException.class);
    }

    private static DefaultRenderedReport mockRendered(final ReportFormat format, final Instant scheduled) {
        return new DefaultRenderedReport.Builder()
                .setBytes(new byte[0])
                .setFormat(format)
                .setScheduledFor(scheduled)
                .setGeneratedAt(scheduled)
                .build();
    }

    private <T, E extends Throwable> T unwrapAsyncThrow(
            final CompletionStage<T> stage,
            final Class<E> clazz
    ) throws E, InterruptedException {
        try {
            return stage.toCompletableFuture().get();
        } catch (final ExecutionException exc) {
            throw clazz.cast(exc.getCause());
        }
    }

    private static class MockEmailSender implements Sender {
        @Override
        @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
        public CompletionStage<Void> send(
                final Recipient recipient,
                final ImmutableMap<ReportFormat, RenderedReport> formatsToSend
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Inject
        MockEmailSender() {}

    }

    private static final class MockHtmlRenderer implements Renderer<WebPageReportSource, HtmlReportFormat> {
        @Override
        public CompletionStage<RenderedReport> render(
                final WebPageReportSource source,
                final HtmlReportFormat format,
                final Instant scheduled
        ) {
            return CompletableFuture.completedFuture(mockRendered(format, scheduled));
        }

        @Inject
        MockHtmlRenderer() {}
    }

    private static final class MockPdfRenderer implements Renderer<WebPageReportSource, PdfReportFormat> {
        @Override
        public CompletionStage<RenderedReport> render(
                final WebPageReportSource source,
                final PdfReportFormat format,
                final Instant scheduled
        ) {
            return CompletableFuture.completedFuture(mockRendered(format, scheduled));
        }

        @Inject
        MockPdfRenderer() {}
    }
}
