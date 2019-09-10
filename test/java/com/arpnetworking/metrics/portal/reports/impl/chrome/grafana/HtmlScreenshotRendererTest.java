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
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;

/**
 * Tests class {@link HtmlGrafanaScreenshotRenderer}.
 *
 * This test is ignored on systems where it can't find Chrome -- see {@link BaseChromeTestSuite} for instructions for manual execution.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class HtmlScreenshotRendererTest extends BaseChromeTestSuite {

    private void runTestWithRenderDelay(final Duration renderDelay) throws Exception {
        final MockRenderedReportBuilder builder = Mockito.mock(MockRenderedReportBuilder.class);

        _wireMock.givenThat(
                get(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "text/html")
                                .withBody(Utils.mockGrafanaReportPanelPage(renderDelay))
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

        final ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(builder).setBytes(bytes.capture());
        final String response = Strings.stringFromBytes(bytes.getValue(), StandardCharsets.UTF_8);
        assertEquals(response, "content we care about");
    }

    @Test(timeout = 20000)
    public void testImmediateRendering() throws Exception {
        runTestWithRenderDelay(Duration.ZERO);
    }

    @Test(timeout = 20000)
    public void testDelayedRendering() throws Exception {
        runTestWithRenderDelay(Duration.ofSeconds(2));
    }
}
