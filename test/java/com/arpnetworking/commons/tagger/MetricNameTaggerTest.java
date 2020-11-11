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

package com.arpnetworking.commons.tagger;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Unit tests for {@link MetricNameTagger}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class MetricNameTaggerTest {
    @Test
    public void testOffsetAndMaxSegments() {
        final String metricName = "foo/bar/baz/qux";

        final Tagger unlimitedTagger = new MetricNameTagger.Builder()
                .setDelimeter("/")
                .setMaxSegments(0)
                .build();

        assertThat(unlimitedTagger.getTags(metricName), is(ImmutableMap.of("metricName", metricName)));

        final Tagger limitedTagger = new MetricNameTagger.Builder()
                .setDelimeter("/")
                .setMaxSegments(2)
                .build();

        assertThat(limitedTagger.getTags(metricName), is(ImmutableMap.of("metricName", "foo/bar")));
    }

    @Test
    public void testDelimeter() {
        final String metricName = "foo/bar__baz/qux__a/b";

        final Tagger customDelimeter = new MetricNameTagger.Builder()
                .setDelimeter("__")
                .setMaxSegments(2)
                .build();

        assertThat(customDelimeter.getTags(metricName), is(ImmutableMap.of("metricName", "foo/bar__baz/qux")));
    }

    @Test
    public void testCustomTagName() {
        final String metricName = "foo/bar/baz/qux";

        final Tagger customDelimeter = new MetricNameTagger.Builder()
                .setTagName("namespace")
                .setDelimeter("/")
                .setMaxSegments(1)
                .build();

        assertThat(customDelimeter.getTags(metricName), is(ImmutableMap.of("namespace", "foo")));
    }
}
