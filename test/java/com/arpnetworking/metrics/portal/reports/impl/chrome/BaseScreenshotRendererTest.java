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
package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.arpnetworking.metrics.portal.reports.impl.testing.MockRenderedReportBuilder;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import models.internal.Problem;
import models.internal.TimeRange;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests for {@link BaseScreenshotRenderer}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class BaseScreenshotRendererTest extends BaseChromeTestSuite {

    @Mock
    private DevToolsFactory _factory;
    @Mock
    private DevToolsService _dts;

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseScreenshotRendererTest.class);
    private static final String SECRET_TOKEN = "SECRET TOKEN";
    private static final PerOriginConfigs ORIGIN_CONFIGS = new PerOriginConfigs.Builder()
            .setByOrigin(ImmutableMap.of("http://allowed.com", new OriginConfig.Builder()
                    .setAllowedNavigationPaths(ImmutableSet.of("/?"))
                    .setAdditionalHeaders(ImmutableMap.of("X-Auth-Token", SECRET_TOKEN))
                    .build()))
            .build();
    private AutoCloseable _mocks;

    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        _dts = Mockito.mock(DevToolsService.class);
        Mockito.doAnswer(args -> CompletableFuture.completedFuture(null)).when(_dts).navigate(Mockito.anyString());

        _factory = Mockito.mock(DevToolsFactory.class);
        Mockito.doReturn(_dts).when(_factory).create(Mockito.anyBoolean());
        Mockito.doReturn(ORIGIN_CONFIGS).when(_factory).getOriginConfigs();
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

    private MockRenderer createMockRenderer() {
        return createMockRenderer(new CompletableFuture<>());
    }
    @SuppressWarnings("unchecked")
    private MockRenderer createMockRenderer(final CompletableFuture<?> complete) {
        return new MockRenderer(_factory, (CompletableFuture<MockRenderedReportBuilder>) complete);
    }

    @Test
    public void testClosesDevToolsWhenComplete() {
        final MockRenderer renderer = createMockRenderer();
        renderer.getComplete().complete(new MockRenderedReportBuilder()); // make the render complete immediately
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
        final MockRenderer renderer = createMockRenderer(new CompletableFuture<>());
        renderer.render(
                TestBeanFactory.createWebPageReportSourceBuilder().build(),
                new HtmlReportFormat.Builder().build(),
                DEFAULT_TIME_RANGE,
                Mockito.mock(MockRenderedReportBuilder.class),
                Duration.ofMillis(500)
        );
        Mockito.verify(_dts, Mockito.timeout(1000).atLeastOnce()).close();
    }

    @Test
    public void testClosesDevToolsOnCancelWhileRendering() {
        final MockRenderer renderer = createMockRenderer(new CompletableFuture<>());
        final CompletableFuture<?> future = renderer.render(
                TestBeanFactory.createWebPageReportSourceBuilder().build(),
                new HtmlReportFormat.Builder().build(),
                DEFAULT_TIME_RANGE,
                Mockito.mock(MockRenderedReportBuilder.class),
                Duration.ofDays(1)
        );
        try {
            future.cancel(true);
            renderer.getComplete().get(500, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | ExecutionException | InterruptedException ignored) { }
        Mockito.verify(_dts, Mockito.timeout(1000).atLeastOnce()).close();
    }

    @Test(timeout = 5000)
    public void testClosesDevToolsOnTimeoutWhileNavigating() throws Exception {
        final CompletableFuture<?> navigationStarted = new CompletableFuture<>();
        Mockito.doAnswer(args -> {
            navigationStarted.complete(null);
            return new CompletableFuture<Void>();
        }).when(_dts).navigate(Mockito.anyString());

        createMockRenderer().render(
                TestBeanFactory.createWebPageReportSourceBuilder().build(),
                new HtmlReportFormat.Builder().build(),
                DEFAULT_TIME_RANGE,
                Mockito.mock(MockRenderedReportBuilder.class),
                Duration.ofMillis(500)
        );
        navigationStarted.get();
        Mockito.verify(_dts, Mockito.timeout(1000).atLeastOnce()).close();
    }

    @Test(timeout = 5000)
    public void testClosesDevToolsOnCancelWhileNavigating() throws Exception {
        final CompletableFuture<?> navigationStarted = new CompletableFuture<>();
        Mockito.doAnswer(args -> {
            navigationStarted.complete(null);
            return new CompletableFuture<Void>();
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

    @Test
    public void testDoesNotIncludeHeadersInProblemMessage() {
        final URI uri = URI.create("http://disallowed.com");
        Mockito.doReturn(false).when(_dts).isNavigationAllowed(Mockito.anyString());
        final ImmutableList<Problem> problems = createMockRenderer().validateRender(
                TestBeanFactory.createWebPageReportSourceBuilder().setUri(uri).build(),
                new HtmlReportFormat.Builder().build()
        );
        Assert.assertTrue(problems.toString().contains(uri.toString()));
        Assert.assertFalse(problems.toString().contains(SECRET_TOKEN));
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
            LOGGER.debug()
                    .setMessage("Rendering in mock renderer")
                    .addData("source", source)
                    .addData("format", format)
                    .addData("timeRange", timeRange)
                    .addData("builder", builder)
                    .addData("complete", _complete)
                    .log();
            final CompletableFuture<B> result = _complete.thenApply(anything -> builder.setBytes(new byte[0]));
            LOGGER.debug()
                    .setMessage("Rendering in mock renderer callback complete")
                    .addData("result", result)
                    .log();

            return result;
        }

        CompletableFuture<MockRenderedReportBuilder> getComplete() {
            return _complete;
        }

        MockRenderer(
                final DevToolsFactory factory,
                final CompletableFuture<MockRenderedReportBuilder> complete
        ) {
            super(factory);
            _complete = complete;
        }

        private final CompletableFuture<MockRenderedReportBuilder> _complete;
    }
}
