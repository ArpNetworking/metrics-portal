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
import com.arpnetworking.metrics.portal.reports.impl.testing.MockRenderedReportBuilder;
import com.arpnetworking.metrics.portal.reports.impl.testing.Utils;
import com.github.tomakehurst.wiremock.common.Strings;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.typesafe.config.Config;
import models.internal.TimeRange;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertTrue;

/**
 * Tests class {@link HtmlScreenshotRenderer}.
 *
 * This test-class is only meant to be run manually: it depends on Chrome, which not all environments are guaranteed to have, so this class
 * is marked {@code @Ignore}. If you want to run it manually, set {@link Utils#CHROME_PATH} as appropriate for your system first.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Ignore
public class HtmlScreenshotRendererIT {
    @Rule
    public WireMockRule _wireMock = new WireMockRule(wireMockConfig().dynamicPort());

    @Test
    public void testRendering() throws Exception {
        final MockRenderedReportBuilder builder = Mockito.mock(MockRenderedReportBuilder.class);
        final Config config = Utils.createChromeRendererConfig();

        _wireMock.givenThat(
                get(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withBody("here are some bytes")
                        )
        );

        final HtmlReportFormat format = new HtmlReportFormat.Builder().build();
        final HtmlScreenshotRenderer renderer = new HtmlScreenshotRenderer(config);
        final WebPageReportSource source = TestBeanFactory.createWebPageReportSourceBuilder()
                .setUri(URI.create("http://localhost:" + _wireMock.port()))
                .build();

        final CompletionStage<MockRenderedReportBuilder> stage = renderer.render(
                source,
                format,
                new TimeRange(Instant.EPOCH, Instant.EPOCH),
                builder);

        stage.toCompletableFuture().get();

        final ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(builder).setBytes(bytes.capture());
        final String response = Strings.stringFromBytes(bytes.getValue(), StandardCharsets.UTF_8);
        assertTrue(response.contains("here are some bytes"));
    }
}
