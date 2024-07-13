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
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;
import org.junit.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests class {@link HtmlScreenshotRenderer}.
 *
 * This test is ignored on systems where it can't find Chrome -- see {@link BaseChromeTestSuite} for instructions for manual execution.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class HtmlScreenshotRendererTest extends BaseChromeTestSuite {

    @Test(timeout = 60000)
    public void testRendering() throws Exception {
        final MockRenderedReportBuilder builder = new MockRenderedReportBuilder();

        _wireMock.givenThat(
                get(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withBody("here are some bytes")
                        )
        );

        final HtmlReportFormat format = new HtmlReportFormat.Builder().build();
        final HtmlScreenshotRenderer renderer = new HtmlScreenshotRenderer(getDevToolsFactory());
        final WebPageReportSource source = TestBeanFactory.createWebPageReportSourceBuilder()
                .setUri(URI.create("http://localhost:" + _wireMock.port()))
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
        assertTrue(response.contains("here are some bytes"));
    }

    @Test(timeout = 60000)
    public void testObeysOriginConfig() throws Exception {
        final MockRenderedReportBuilder builder = new MockRenderedReportBuilder();

        _wireMock.givenThat(
                get(urlEqualTo("/allowed-page"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "text/html")
                                .withBody("<img src=\"/disallowed-img\" /> <img src=\"/allowed-img\" />")
                        )
        );
        _wireMock.givenThat(
                get(urlEqualTo("/allowed-img"))
                        .willReturn(aResponse()
                                .withBody("")
                        )
        );

        final HtmlReportFormat format = new HtmlReportFormat.Builder().build();
        final HtmlScreenshotRenderer renderer = new HtmlScreenshotRenderer(getDevToolsFactory("/allowed-.*"));
        final WebPageReportSource source = TestBeanFactory.createWebPageReportSourceBuilder()
                .setUri(URI.create("http://localhost:" + _wireMock.port() + "/allowed-page"))
                .build();

        final CompletableFuture<MockRenderedReportBuilder> stage = renderer.render(
                source,
                format,
                DEFAULT_TIME_RANGE,
                builder,
                DEFAULT_TIMEOUT
        );

        stage.get();

        // ensure configured additionalHeaders are added:
        assertEquals(
                "extra header value",
                _wireMock.findAll(new RequestPatternBuilder().withUrl("/allowed-img")).get(0).getHeader("X-Extra-Header")
        );

        // ensure disallowed resource is blocked
        assertEquals(0, _wireMock.findAll(new RequestPatternBuilder().withUrl("/disallowed-img")).size());
        // (control group: ensure allowed resource wasn't blocked)
        assertEquals(1, _wireMock.findAll(new RequestPatternBuilder().withUrl("/allowed-img")).size());

        // ensure navigation to non-whitelisted pages is blocked
        try {
            final WebPageReportSource sourceWithDisallowedUri = TestBeanFactory.createWebPageReportSourceBuilder()
                    .setUri(URI.create("http://disallowed"))
                    .build();
            renderer.render(
                    sourceWithDisallowedUri,
                    format,
                    DEFAULT_TIME_RANGE,
                    builder,
                    DEFAULT_TIMEOUT
            ).get();
            fail("should have rejected navigation to " + sourceWithDisallowedUri);
        } catch (final IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("navigation is not allowed"));
        }
    }
}
