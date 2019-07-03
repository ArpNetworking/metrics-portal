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
import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.arpnetworking.metrics.portal.reports.impl.chrome.grafana.testing.Utils;
import com.arpnetworking.metrics.portal.reports.impl.testing.MockRenderedReportBuilder;
import com.github.tomakehurst.wiremock.common.Strings;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.internal.TimeRange;
import models.internal.impl.GrafanaReportPanelReportSource;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests class {@link HtmlScreenshotRenderer}.
 *
 * This test-class is only meant to be run manually: it depends on Chrome, which not all environments are guaranteed to have, so this class
 * is marked {@code @Ignore}. If you want to run it manually, set
 * {@link com.arpnetworking.metrics.portal.reports.impl.testing.Utils#CHROME_PATH} as appropriate for your system first.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Ignore
public class HtmlScreenshotRendererIT {
    @Rule
    public WireMockRule _wireMock = new WireMockRule(wireMockConfig().dynamicPort());

    private void runTestWithRenderDelay(final Duration renderDelay) throws Exception {
        final Config config = com.arpnetworking.metrics.portal.reports.impl.testing.Utils.createChromeRendererConfig();
        final MockRenderedReportBuilder builder = Mockito.mock(MockRenderedReportBuilder.class);

        _wireMock.givenThat(
                get(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "text/html")
                                .withBody(Utils.mockGrafanaReportPanelPage(renderDelay))
                        )
        );

        final HtmlReportFormat format = new HtmlReportFormat.Builder().build();
        final HtmlScreenshotRenderer renderer = new HtmlScreenshotRenderer(config);
        final GrafanaReportPanelReportSource source = new GrafanaReportPanelReportSource.Builder()
                .setWebPageReportSource(
                        TestBeanFactory.createWebPageReportSourceBuilder()
                                .setUri(URI.create("http://localhost:" + _wireMock.port()))
                                .build())
                .build();

        final CompletionStage<MockRenderedReportBuilder> stage = renderer.render(
                source,
                format,
                new TimeRange(Instant.EPOCH, Instant.EPOCH),
                builder);

        stage.toCompletableFuture().get(20, TimeUnit.SECONDS);

        final ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(builder).setBytes(bytes.capture());
        final String response = Strings.stringFromBytes(bytes.getValue(), StandardCharsets.UTF_8);
        assertEquals(response, "content we care about");
    }

    @Test
    public void testImmediateRendering() throws Exception {
        runTestWithRenderDelay(Duration.ZERO);
    }

    @Test
    public void testDelayedRendering() throws Exception {
        runTestWithRenderDelay(Duration.ofSeconds(2));
    }
}
