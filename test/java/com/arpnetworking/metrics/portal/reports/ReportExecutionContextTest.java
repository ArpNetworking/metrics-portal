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

import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import models.internal.Problem;
import models.internal.TimeRange;
import models.internal.impl.DefaultRecipient;
import models.internal.impl.DefaultRenderedReport;
import models.internal.impl.DefaultReport;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;
import models.internal.impl.WebPageReportSource;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import play.Environment;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
    private static final Instant T1 = Instant.now().truncatedTo(ChronoUnit.DAYS);
    private static final Instant T0 = ChronoUnit.DAYS.addTo(T1, -1);
    private static final TimeRange TIME_RANGE = new TimeRange(T0, T1);
    private static final ManualClock CLOCK = new ManualClock(T1, Duration.ofSeconds(1), ZoneId.of("UTC"));
    private static final Report EXAMPLE_REPORT = new DefaultReport.Builder()
            .setId(UUID.randomUUID())
            .setName("My Name")
            .setReportSource(new WebPageReportSource.Builder()
                    .setId(UUID.randomUUID())
                    .setTitle("My Report Title")
                    .setUri(URI.create("https://example.com"))
                    .setIgnoreCertificateErrors(true)
                    .build()
            )
            .setSchedule(new OneOffSchedule.Builder().setRunAtAndAfter(T0).build())
            .setTimeout(Duration.ofSeconds(30))
            .setRecipients(ImmutableSetMultimap.<ReportFormat, Recipient>builder()
                    .put(HTML, ALICE)
                    .put(HTML, BOB)
                    .put(PDF, BOB)
                    .build()
            )
            .build();


    private Injector _injector;
    private Environment _environment;
    @Mock
    private MockEmailSender _emailSender;
    private Config _config;
    private AutoCloseable _mocks;

    @Before
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED")
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        Mockito.doReturn(CompletableFuture.completedFuture("done")).when(_emailSender).send(
                Mockito.any(),
                Mockito.any()
        );

        Mockito.doReturn(ImmutableList.of()).when(_emailSender).validateSend(Mockito.any(), Mockito.any());
        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {}

            @Provides
            @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by Guice")
            private MockEmailSender provideMockEmailSender() {
                return _emailSender;
            }
        });

        _environment = Environment.simple();

        _config = ConfigFactory.parseMap(ImmutableMap.of(
                "reporting.renderers.WEB_PAGE.\"text/html; charset=utf-8\".type", getClass().getName() + "$MockHtmlRenderer",
                "reporting.renderers.WEB_PAGE.\"application/pdf\".type", getClass().getName() + "$MockPdfRenderer",
                "reporting.renderers.WEB_PAGE.\"application/pdf\".pdfCompatibilityVersion", "2.0",
                "reporting.senders.EMAIL.type", getClass().getName() + "$MockEmailSender"
        ));
    }

    @After
    public void tearDown() {
        if (_mocks != null) {
            try {
                _mocks.close();
                // CHECKSTYLE.OFF: IllegalCatch - Ignore all errors when closing the mock
            } catch (final Exception ignored) { }
                // CHECKSTYLE.ON: IllegalCatch
        }
    }

    @Test
    public void testInstantiatesWithConfig() {
        final ReportExecutionContext context = new ReportExecutionContext(CLOCK, _injector, _environment, _config);
        final MockPdfRenderer renderer = (MockPdfRenderer) context.getRenderer((WebPageReportSource) EXAMPLE_REPORT.getSource(), PDF);
        Assert.assertEquals("2.0", renderer.getPdfCompatibilityVersion());
    }

    @Test
    public void testExecute() throws Exception {
        final ReportExecutionContext context = new ReportExecutionContext(CLOCK, _injector, _environment, _config);
        context.execute(EXAMPLE_REPORT, T1).toCompletableFuture().get();
        Mockito.verify(_emailSender).send(
                ALICE,
                ImmutableMap.of(HTML, mockRendered(EXAMPLE_REPORT, HTML, TIME_RANGE))
        );
        Mockito.verify(_emailSender).send(
                BOB,
                ImmutableMap.of(HTML, mockRendered(EXAMPLE_REPORT, HTML, TIME_RANGE), PDF, mockRendered(EXAMPLE_REPORT, PDF, TIME_RANGE))
        );
    }

    @Test(expected = ReportExecutionException.class)
    public void testExecuteThrowsIfNoRendererFound() throws InterruptedException, ReportExecutionException {
        final ReportExecutionContext context = new ReportExecutionContext(
                CLOCK,
                _injector,
                _environment,
                _config.withoutPath("reporting.renderers.WEB_PAGE.\"text/html; charset=utf-8\"")
        );
        unwrapAsyncThrow(context.execute(EXAMPLE_REPORT, T1), ReportExecutionException.class);
    }

    @Test(expected = ReportExecutionException.class)
    public void testExecuteThrowsIfNoSenderFound() throws InterruptedException, ReportExecutionException {
        final ReportExecutionContext context = new ReportExecutionContext(
                CLOCK,
                _injector,
                _environment,
                _config.withoutPath("reporting.senders.EMAIL")
        );
        unwrapAsyncThrow(context.execute(EXAMPLE_REPORT, T1), ReportExecutionException.class);
    }

    @Test(expected = Exception.class)
    public void testBadConfigWithNoType() {
        new ReportExecutionContext(CLOCK, _injector, _environment, ConfigFactory.parseMap(ImmutableMap.of(
                "reporting.renderers.WEB_PAGE.\"text/html\".something", "something"
        )));
    }

    @Test(expected = Exception.class)
    public void testBadConfigWithUnloadableType() {
        new ReportExecutionContext(CLOCK, _injector, _environment, ConfigFactory.parseMap(ImmutableMap.of(
                "reporting.renderers.WEB_PAGE.\"text/html\".type", "no.such.package.MyClass"
        )));
    }

    @Test(expected = Exception.class)
    public void testBadConfigWithUninjectableType() {
        new ReportExecutionContext(CLOCK, _injector, _environment, ConfigFactory.parseMap(ImmutableMap.of(
                "reporting.renderers.WEB_PAGE.\"text/html\".type", getClass().getName() + "$ClassNotRegisteredWithInjector"
        )));
    }

    @Test(expected = Exception.class)
    public void testBadConfigWithNonobjectRenderers() {
        new ReportExecutionContext(CLOCK, _injector, _environment, ConfigFactory.parseMap(ImmutableMap.of(
                "reporting.renderers", "not a ConfigObject"
        )));
    }

    @Test(expected = Exception.class)
    public void testBadConfigWithNonobjectRenderersByFormat() {
        new ReportExecutionContext(CLOCK, _injector, _environment, ConfigFactory.parseMap(ImmutableMap.of(
                "reporting.renderers.WEB_PAGE", "not a ConfigObject"
        )));
    }

    @Test(expected = Exception.class)
    public void testBadConfigWithNonobjectRendererSpec() {
        new ReportExecutionContext(CLOCK, _injector, _environment, ConfigFactory.parseMap(ImmutableMap.of(
                "reporting.renderers.WEB_PAGE.text", "not a ConfigObject"
        )));
    }

    @Test
    public void testOkIfNoReportsSectionPresent() {
        new ReportExecutionContext(CLOCK, _injector, _environment, ConfigFactory.parseMap(ImmutableMap.of()));
    }

    @Test
    public void testVerifyCanProbablyExecute() {
        final ReportExecutionContext context = new ReportExecutionContext(CLOCK, _injector, _environment, _config);
        context.validateExecute(EXAMPLE_REPORT);

        final ReportExecutionContext contextWithoutEmail = new ReportExecutionContext(
                CLOCK,
                _injector,
                _environment,
                _config.withoutPath("reporting.senders.EMAIL")
        );
        Assert.assertTrue(
                contextWithoutEmail.validateExecute(EXAMPLE_REPORT).stream()
                        .anyMatch(problem -> problem.getProblemCode().equals("report_problem.NO_SUCH_SENDER"))
        );

        final ReportExecutionContext contextWithoutWeb = new ReportExecutionContext(
                CLOCK,
                _injector,
                _environment,
                _config.withoutPath("reporting.renderers.WEB_PAGE")
        );
        Assert.assertTrue(
                contextWithoutWeb.validateExecute(EXAMPLE_REPORT).stream()
                        .anyMatch(problem -> problem.getProblemCode().equals("report_problem.NO_SUCH_RENDERER"))
        );
    }

    private static DefaultRenderedReport mockRendered(final Report report, final ReportFormat format, final TimeRange timeRange) {
        return new DefaultRenderedReport.Builder()
                .setReport(report)
                .setBytes(new byte[0])
                .setFormat(format)
                .setTimeRange(timeRange)
                .setGeneratedAt(timeRange.getEnd())
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
        public ImmutableList<Problem> validateSend(final Recipient recipient, final ImmutableCollection<ReportFormat> formatsToSend) {
            return ImmutableList.of();
        }

        @Override
        public CompletionStage<Void> send(
                final Recipient recipient,
                final ImmutableMap<ReportFormat, RenderedReport> formatsToSend
        ) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class MockHtmlRenderer implements Renderer<WebPageReportSource, HtmlReportFormat> {
        @Override
        public ImmutableList<Problem> validateRender(final WebPageReportSource source, final HtmlReportFormat format) {
            return ImmutableList.of();
        }

        @Override
        public <B extends RenderedReport.Builder<B, ?>> CompletableFuture<B> render(
                final WebPageReportSource source,
                final HtmlReportFormat format,
                final TimeRange timeRange,
                final B builder,
                final Duration timeout
        ) {
            return CompletableFuture.completedFuture(builder.setBytes(new byte[0]));
        }
    }

    private static final class MockPdfRenderer implements Renderer<WebPageReportSource, PdfReportFormat> {
        @Override
        public ImmutableList<Problem> validateRender(final WebPageReportSource source, final PdfReportFormat format) {
            return ImmutableList.of();
        }

        @Override
        public <B extends RenderedReport.Builder<B, ?>> CompletableFuture<B> render(
                final WebPageReportSource source,
                final PdfReportFormat format,
                final TimeRange timeRange,
                final B builder,
                final Duration timeout
        ) {
            return CompletableFuture.completedFuture(builder.setBytes(new byte[0]));
        }

        public String getPdfCompatibilityVersion() {
            return _pdfCompatibilityVersion;
        }

        @Inject
        MockPdfRenderer(@Assisted final Config config) {
            _pdfCompatibilityVersion = config.getString("pdfCompatibilityVersion");
        }

        private final String _pdfCompatibilityVersion;
    }

    private static final class ClassNotRegisteredWithInjector {}
}
