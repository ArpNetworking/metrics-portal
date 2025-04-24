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
package com.arpnetworking.metrics.portal.reports.impl.chrome.grafana;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.reports.impl.chrome.BaseChromeTestSuite;
import com.arpnetworking.metrics.portal.reports.impl.chrome.grafana.testing.Utils;
import com.arpnetworking.metrics.portal.reports.impl.testing.MockRenderedReportBuilder;
import com.github.tomakehurst.wiremock.common.Strings;
import models.internal.impl.GrafanaReportPanelReportSource;
import models.internal.impl.HtmlReportFormat;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests class {@link HtmlGrafanaScreenshotRenderer}.
 *
 * This test is ignored on systems where it can't find Chrome -- see {@link BaseChromeTestSuite} for instructions for manual execution.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class HtmlScreenshotRendererTest extends BaseChromeTestSuite {

    @Ignore("This is flakey due to event handling with CDP")
    private void runTestWithRenderDelay(final Duration renderDelay) throws Exception {
        final MockRenderedReportBuilder builder = new MockRenderedReportBuilder();

        _wireMock.givenThat(
                get(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "text/html")
                                .withBody(Utils.mockGrafanaReportPanelPage(renderDelay, true))
                        )
        );

        final HtmlReportFormat format = new HtmlReportFormat.Builder().build();
        final HtmlGrafanaScreenshotRenderer renderer = new HtmlGrafanaScreenshotRenderer(getDevToolsFactory());
        final GrafanaReportPanelReportSource source = new GrafanaReportPanelReportSource.Builder()
                .setWebPageReportSource(
                        TestBeanFactory.createWebPageReportSourceBuilder()
                                .setUri(URI.create("http://localhost:" + _wireMock.port()))
                                .build())
                .build();

        final CompletableFuture<MockRenderedReportBuilder> stage = renderer.render(
                source,
                format,
                DEFAULT_TIME_RANGE,
                builder,
                DEFAULT_TIMEOUT
        );

        stage.get();

        final String response = Strings.stringFromBytes(builder.getBytes(), StandardCharsets.UTF_8);
        assertEquals(response, "content we care about");
    }

    @Test(timeout = 60000)
    @Ignore("This is flakey due to event handling with CDP")
    public void testImmediateRendering() throws Exception {
        runTestWithRenderDelay(Duration.ZERO);
    }

    @Test(timeout = 60000)
    public void testDelayedRendering() throws Exception {
        runTestWithRenderDelay(Duration.ofSeconds(2));
    }

    @Test(timeout = 60000)
    public void testDelayedRenderingFailure() throws Exception {
        final MockRenderedReportBuilder builder = new MockRenderedReportBuilder();

        _wireMock.givenThat(
                get(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "text/html")
                                .withBody(Utils.mockGrafanaReportPanelPage(Duration.ofSeconds(2), false))
                        )
        );

        final HtmlReportFormat format = new HtmlReportFormat.Builder().build();
        final HtmlGrafanaScreenshotRenderer renderer = new HtmlGrafanaScreenshotRenderer(getDevToolsFactory());
        final GrafanaReportPanelReportSource source = new GrafanaReportPanelReportSource.Builder()
                .setWebPageReportSource(
                        TestBeanFactory.createWebPageReportSourceBuilder()
                                .setUri(URI.create("http://localhost:" + _wireMock.port()))
                                .build())
                .build();

        final CompletableFuture<MockRenderedReportBuilder> stage = renderer.render(
                source,
                format,
                DEFAULT_TIME_RANGE,
                builder,
                DEFAULT_TIMEOUT
        );

        try {
            stage.get();
            Assert.fail("rendering should not have completed successfully");
        } catch (final ExecutionException exc) {
            assertThat(exc.getCause(), Matchers.instanceOf(BaseGrafanaScreenshotRenderer.BrowserReportedFailure.class));
        }
    }
}
