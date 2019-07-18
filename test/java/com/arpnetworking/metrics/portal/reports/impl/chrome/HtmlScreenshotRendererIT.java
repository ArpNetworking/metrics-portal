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
import com.github.tomakehurst.wiremock.common.Strings;
import com.typesafe.config.Config;
import models.internal.TimeRange;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertTrue;

/**
 * Tests class {@link HtmlScreenshotRenderer}.
 *
 * This test is ignored on systems where it can't find Chrome -- see {@link BaseChromeIT} for instructions for manual execution.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class HtmlScreenshotRendererIT extends BaseChromeIT {

    @Test
    public void testRendering() throws Exception {
        final MockRenderedReportBuilder builder = Mockito.mock(MockRenderedReportBuilder.class);
        final Config config = CHROME_RENDERER_CONFIG;

        _wireMock.givenThat(
                get(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withBody("here are some bytes")
                        )
        );

        final HtmlReportFormat format = new HtmlReportFormat.Builder().build();
        final HtmlScreenshotRenderer renderer = new HtmlScreenshotRenderer(config, new ScheduledThreadPoolExecutor(1));
        final WebPageReportSource source = TestBeanFactory.createWebPageReportSourceBuilder()
                .setUri(URI.create("http://localhost:" + _wireMock.port()))
                .build();

        final CompletionStage<MockRenderedReportBuilder> stage = renderer.render(
                source,
                format,
                new TimeRange(Instant.EPOCH, Instant.EPOCH),
                builder,
                Duration.ofSeconds(15)
        );

        stage.toCompletableFuture().get(20, TimeUnit.SECONDS);

        final ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(builder).setBytes(bytes.capture());
        final String response = Strings.stringFromBytes(bytes.getValue(), StandardCharsets.UTF_8);
        assertTrue(response.contains("here are some bytes"));
    }

    @Test
    public void testTimeout() throws Exception {
        final MockRenderedReportBuilder builder = Mockito.mock(MockRenderedReportBuilder.class);
        final Config config = CHROME_RENDERER_CONFIG;

        _wireMock.givenThat(
                get(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withBody("here are some bytes")
                                .withFixedDelay(1000)
                        )
        );

        final HtmlReportFormat format = new HtmlReportFormat.Builder().build();
        final HtmlScreenshotRenderer renderer = new HtmlScreenshotRenderer(config, new ScheduledThreadPoolExecutor(1));
        final WebPageReportSource source = TestBeanFactory.createWebPageReportSourceBuilder()
                .setUri(URI.create("http://localhost:" + _wireMock.port()))
                .build();

        final CompletionStage<MockRenderedReportBuilder> stage = renderer.render(
                source,
                format,
                new TimeRange(Instant.EPOCH, Instant.EPOCH),
                builder,
                Duration.ofMillis(500)
        );

        boolean cancelled = false;
        try {
            stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (final CancellationException exception) {
            cancelled = true;
        }
        Assert.assertTrue("render().get() should have thrown a CancellationException", cancelled);
    }
}
