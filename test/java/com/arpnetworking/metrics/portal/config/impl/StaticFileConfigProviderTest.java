/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.config.impl;

import com.arpnetworking.utility.test.ResourceHelper;
import com.google.common.io.ByteStreams;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link StaticFileConfigProvider}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class StaticFileConfigProviderTest {
    private static final long TEST_TIMEOUT_MS = 1000;
    private Path _contentPath;
    private StaticFileConfigProvider _provider;

    @Before
    public void setUp() throws URISyntaxException {
        final URL url = ResourceHelper.resourceURL(getClass(), "exampleContents");
        _contentPath = Paths.get(url.toURI());
        _provider = new StaticFileConfigProvider(_contentPath);
    }

    @Test
    public void testStaticFileProvider() throws Exception {
        final byte[] expectedContents = Files.readAllBytes(_contentPath);
        final CompletableFuture<byte[]> future = new CompletableFuture<>();
        _provider.start(stream -> {
            try {
                future.complete(ByteStreams.toByteArray(stream));
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        });

        final byte[] buffer = future.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(buffer, is(equalTo(expectedContents)));
    }

    @Test
    public void testInvalidPath() {
        _provider = new StaticFileConfigProvider(Paths.get("thisDoesntExist"));
        try {
            _provider.start(stream -> { });
            fail("expected an exception");
            /* CHECKSTYLE.OFF: IllegalCatch - test */
        } catch (final Exception e) {
            /* CHECKSTYLE.ON: IllegalCatch */
            assertThat(e.getCause(), isA(NoSuchFileException.class));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testDoubleStart() {
        _provider.start(stream -> { });
        _provider.start(stream -> { });
    }

    @Test(expected = IllegalStateException.class)
    public void testStopBeforeStart() {
        _provider.stop();
    }
}
