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

    private static final String SECRET_TOKEN = "SECRET TOKEN";
    private static final PerOriginConfigs ORIGIN_CONFIGS = new PerOriginConfigs.Builder()
            .setByOrigin(ImmutableMap.of("http://allowed.com", new OriginConfig.Builder()
                    .setAllowedNavigationPaths(ImmutableSet.of("/?"))
                    .setAdditionalHeaders(ImmutableMap.of("X-Auth-Token", SECRET_TOKEN))
                    .build()))
            .build();
    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        _dts = Mockito.mock(DevToolsService.class);
        Mockito.doAnswer(args -> CompletableFuture.completedFuture(null)).when(_dts).navigate(Mockito.anyString());

        _factory = Mockito.mock(DevToolsFactory.class);
        Mockito.doReturn(_dts).when(_factory).create(Mockito.anyBoolean());
        Mockito.doReturn(ORIGIN_CONFIGS).when(_factory).getOriginConfigs();
    }

    @After
    public void tearDown() {
        if (mocks != null) {
            try {
                mocks.close();
            } catch (final Exception ignored) { }
        }
    }

    private MockRenderer createMockRenderer() {
        return createMockRenderer(new CompletableFuture<>());
    }
    private MockRenderer createMockRenderer(final CompletableFuture<?> complete) {
        return new MockRenderer(_factory, complete);
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

    @Test(timeout = 2000)
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

    @Test(timeout = 2000)
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
            return _complete.thenApply(anything -> builder.setBytes(new byte[0]));
        }

        CompletableFuture<?> getComplete() {
            return _complete;
        }

        MockRenderer(
                final DevToolsFactory factory,
                final CompletableFuture<?> complete
        ) {
            super(factory);
            _complete = complete;
        }

        private final CompletableFuture<?> _complete;
    }
}
