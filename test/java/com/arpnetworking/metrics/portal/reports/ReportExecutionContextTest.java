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

import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
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

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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


    private Injector _injector;
    @Mock
    private Sender _sender;
    private ReportExecutionContext _context;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Mockito.doReturn(CompletableFuture.completedFuture("done")).when(_sender).send(Mockito.any(), Mockito.any());
        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Sender.class).annotatedWith(Names.named("EMAIL")).toInstance(_sender);
                bind(Renderer.class).annotatedWith(Names.named("web text/html")).to(MockHtmlRenderer.class).asEagerSingleton();
                bind(Renderer.class).annotatedWith(Names.named("web application/pdf")).to(MockPdfRenderer.class).asEagerSingleton();
            }
        });

        _context = new ReportExecutionContext(oh, no);
    }

    @Test
    public void testExecute() throws Exception {
        _context.execute(EXAMPLE_REPORT, T0).toCompletableFuture().get();

        Mockito.verify(_sender).send(ALICE, ImmutableMap.of(HTML, mockRendered(HTML, T0)));
        Mockito.verify(_sender).send(BOB, ImmutableMap.of(HTML, mockRendered(HTML, T0), PDF, mockRendered(PDF, T0)));
    }

    @Test(expected = ConfigurationException.class)
    public void testExecuteThrowsIfNoRendererFound() {
        _context.execute(EXAMPLE_REPORT, T0);
    }

    @Test(expected = ConfigurationException.class)
    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
    public void testExecuteThrowsIfNoSenderFound() {
        final Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Renderer.class).annotatedWith(Names.named("web text/html")).to(MockHtmlRenderer.class).asEagerSingleton();
                bind(Renderer.class).annotatedWith(Names.named("web application/pdf")).to(MockPdfRenderer.class).asEagerSingleton();
            }
        });
        _context.execute(EXAMPLE_REPORT, T0);
    }

    private static DefaultRenderedReport mockRendered(final ReportFormat format, final Instant scheduled) {
        return new DefaultRenderedReport.Builder()
                .setBytes(new byte[0])
                .setFormat(format)
                .setScheduledFor(scheduled)
                .setGeneratedAt(scheduled)
                .build();
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
    }
}
