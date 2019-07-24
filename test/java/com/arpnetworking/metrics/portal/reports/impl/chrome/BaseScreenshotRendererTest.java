package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.arpnetworking.metrics.portal.reports.impl.testing.MockRenderedReportBuilder;
import com.typesafe.config.Config;
import models.internal.TimeRange;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class BaseScreenshotRendererTest extends BaseChromeTest {

    @Mock
    private DevToolsFactory _factory;
    @Mock
    private DevToolsService _dts;

    @Before
    public void setUp() {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        _factory = Mockito.mock(DevToolsFactory.class);
        _dts = Mockito.mock(DevToolsService.class);
        Mockito.doReturn(_dts).when(_factory).create(Mockito.anyBoolean(), Mockito.anyMap());
    }

    private MockRenderer createMockRenderer() {
        return createMockRenderer(new CompletableFuture<>());
    }
    private MockRenderer createMockRenderer(final CompletableFuture<?> complete) {
        return new MockRenderer(CHROME_RENDERER_CONFIG, _renderService, _timeoutService, _factory, complete);
    }

    @Test
    public void testClosesDevToolsWhenComplete() {
        final MockRenderer renderer = createMockRenderer();
        renderer.getComplete().complete(null); // make the render complete immediately
        renderer.render(
                TestBeanFactory.createWebPageReportSourceBuilder().build(),
                new HtmlReportFormat.Builder().build(),
                DEFAULT_TIME_RANGE,
                Mockito.mock(MockRenderedReportBuilder.class),
                Duration.ofDays(1)
        );
        Mockito.verify(_dts, Mockito.timeout(1000).atLeastOnce()).close();
    }

    @Test
    public void testClosesDevToolsOnTimeoutWhileRendering() {
        final MockRenderer renderer = createMockRenderer(Mockito.mock(CompletableFuture.class));
        renderer.render(
                TestBeanFactory.createWebPageReportSourceBuilder().build(),
                new HtmlReportFormat.Builder().build(),
                DEFAULT_TIME_RANGE,
                Mockito.mock(MockRenderedReportBuilder.class),
                Duration.ofMillis(500)
        );
        Mockito.verify(renderer.getComplete(), Mockito.timeout(500)).thenApply(Mockito.any());
        Mockito.verify(_dts, Mockito.timeout(1000).atLeastOnce()).close();
    }

    @Test
    public void testClosesDevToolsOnCancelWhileRendering() {
        final MockRenderer renderer = createMockRenderer(Mockito.mock(CompletableFuture.class));
        final CompletableFuture<?> future = renderer.render(
                TestBeanFactory.createWebPageReportSourceBuilder().build(),
                new HtmlReportFormat.Builder().build(),
                DEFAULT_TIME_RANGE,
                Mockito.mock(MockRenderedReportBuilder.class),
                Duration.ofDays(1)
        );
        Mockito.verify(renderer.getComplete(), Mockito.timeout(500)).thenApply(Mockito.any());
        future.cancel(true);
        Mockito.verify(_dts, Mockito.timeout(1000).atLeastOnce()).close();
    }

    @Test
    public void testClosesDevToolsOnTimeoutWhileNavigating() throws Exception {
        Mockito.doAnswer(args -> {
            Thread.sleep(9999999);
            return null;
        }).when(_dts).navigate(Mockito.anyString());

        createMockRenderer().render(
                TestBeanFactory.createWebPageReportSourceBuilder().build(),
                new HtmlReportFormat.Builder().build(),
                DEFAULT_TIME_RANGE,
                Mockito.mock(MockRenderedReportBuilder.class),
                Duration.ofMillis(500)
        );
        Mockito.verify(_dts, Mockito.timeout(1000).atLeastOnce()).close();
    }

    @Test(timeout = 2000)
    public void testClosesDevToolsOnCancelWhileNavigating() throws Exception {
        final CompletableFuture<?> navigationStarted = new CompletableFuture<>();
        Mockito.doAnswer(args -> {
            navigationStarted.complete(null);
            Thread.sleep(9999999);
            return null;
        }).when(_dts).navigate(Mockito.anyString());

        final CompletableFuture<?> future = createMockRenderer().render(
                TestBeanFactory.createWebPageReportSourceBuilder().build(),
                new HtmlReportFormat.Builder().build(),
                DEFAULT_TIME_RANGE,
                Mockito.mock(MockRenderedReportBuilder.class),
                Duration.ofDays(1)
        );
        navigationStarted.get();
        future.cancel(true);
        Mockito.verify(_dts, Mockito.timeout(1000).atLeastOnce()).close();
    }

    private static final class MockRenderer extends BaseScreenshotRenderer<WebPageReportSource, HtmlReportFormat> {
        @Override
        protected boolean getIgnoreCertificateErrors(final WebPageReportSource source) {
            return source.ignoresCertificateErrors();
        }

        @Override
        protected URI getUri(final WebPageReportSource source) {
            return source.getUri();
        }

        @Override
        protected <B extends RenderedReport.Builder<B, ?>> CompletableFuture<B> whenLoaded(
                final DevToolsService devToolsService,
                final WebPageReportSource source,
                final HtmlReportFormat format,
                final TimeRange timeRange,
                final B builder
        ) {
            return _complete.thenApply(anything -> builder.setBytes(new byte[0]));
        }

        CompletableFuture<?> getComplete() {
            return _complete;
        }

        MockRenderer(
                final Config config,
                final ExecutorService renderExecutor,
                final ScheduledExecutorService timeoutExecutor,
                final DevToolsFactory factory,
                final CompletableFuture<?> complete
        ) {
            super(config, renderExecutor, timeoutExecutor, factory);
            _complete = complete;
        }

        private final CompletableFuture<?> _complete;
    }
}
