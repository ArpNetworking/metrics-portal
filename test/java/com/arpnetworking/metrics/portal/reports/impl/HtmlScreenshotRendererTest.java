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
package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.arpnetworking.metrics.portal.reports.impl.chrome.HtmlScreenshotRenderer;
import com.github.tomakehurst.wiremock.common.Strings;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.internal.TimeRange;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests class {@link HtmlScreenshotRenderer}.
 *
 * This test-class is only meant to be run manually: it depends on Chrome, which not all environments are guaranteed to have, so this class
 * is marked {@code @Ignore}. If you want to run it manually, set {@link #CHROME_BINARY_PATH} as appropriate for your system first.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Ignore
public class HtmlScreenshotRendererTest {
    private static final String CHROME_BINARY_PATH = "";

    @Rule
    public WireMockRule _wireMock = new WireMockRule(wireMockConfig().dynamicPort());

    private URI _baseURI;

    @Mock
    private MockRendereredReportBuilder _renderedReportBuilder;

    @Before
    public void setUp() throws Exception {
        if (CHROME_BINARY_PATH.isEmpty()) {
            fail("set " + getClass().getSimpleName() + ".CHROME_BINARY_PATH");
        }
        MockitoAnnotations.initMocks(this);
        _baseURI = new URI("http://localhost:" + _wireMock.port());
    }

    @Test
    public void testRendering() throws Exception {
        final Config config = ConfigFactory.parseMap(ImmutableMap.of(
                "chromePath", CHROME_BINARY_PATH,
                "chromeArgs", ImmutableMap.of(
                        "no-sandbox", true
                )
        ));
        final HtmlScreenshotRenderer renderer = new HtmlScreenshotRenderer(config);
        final WebPageReportSource source = new WebPageReportSource.Builder()
                .setId(UUID.randomUUID())
                .setUri(URI.create("http://localhost:" + _wireMock.port() + "/potato"))
                .setTitle("my title")
                .setTriggeringEventName("load")
                .setIgnoreCertificateErrors(false)
                .build();

        _wireMock.givenThat(
                get(urlEqualTo("/potato"))
                        .willReturn(aResponse()
                                .withBody("here are some bytes")
                        )
        );

        final CompletionStage<MockRendereredReportBuilder> stage = renderer.render(
                source,
                new HtmlReportFormat.Builder().build(),
                new TimeRange(Instant.EPOCH, Instant.EPOCH),
                _renderedReportBuilder);

        stage.toCompletableFuture().get();

        final ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(_renderedReportBuilder).setBytes(bytes.capture());
        final String response = Strings.stringFromBytes(bytes.getValue(), StandardCharsets.UTF_8);
        assertTrue(response.contains("here are some bytes"));
    }

    private abstract static class MockRendereredReportBuilder
            implements RenderedReport.Builder<MockRendereredReportBuilder, RenderedReport> {}
}
