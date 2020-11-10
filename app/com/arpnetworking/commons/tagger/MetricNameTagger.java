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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A tagger that extracts values from the metric name.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class MetricNameTagger implements Tagger {
    private static final String DEFAULT_TAG_NAME = "metricName";

    private final String _delimeter;
    private final int _maxSegments;
    private final String _tagName;

    private MetricNameTagger(final Builder builder) {
        _delimeter = builder._delimeter;
        _maxSegments = builder._maxSegments;
        _tagName = builder._tagName;
    }

    /**
     * Return the tags to apply.
     * @return the tags.
     */
    @Override
    public ImmutableMap<String, String> getTags(final String metricName) {
        return ImmutableMap.of(
                _tagName, computeTagValue(metricName)
        );
    }

    private String computeTagValue(final String metricName) {
        final ImmutableList<String> segments = ImmutableList.copyOf(metricName.split(_delimeter));

        final int endIndex;
        if (_maxSegments == 0) {
            endIndex = segments.size();
        } else {
            endIndex = Math.min(_maxSegments, segments.size());
        }
        return String.join(_delimeter, segments.subList(0, endIndex));
    }

    /**
     * Builder for instances of {@link MetricNameTagger}.
     */
    public static final class Builder extends OvalBuilder<MetricNameTagger> {
        private String _tagName = DEFAULT_TAG_NAME;
        private String _delimeter = "/";
        private int _maxSegments = 0;

        /**
         * Construct a new, default Builder.
         */
        public Builder() {
            super(MetricNameTagger::new);
        }

        /**
         * Sets the tag name. Optional. Defaults to "metricName".
         *
         * @param tagName the tag name.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setTagName(final String tagName) {
            _tagName = tagName;
            return this;
        }

        /**
         * Sets the delimeter. Optional. Defaults to "/".
         *
         * @param delimeter the delimeter.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setDelimeter(final String delimeter) {
            _delimeter = delimeter;
            return this;
        }

        /**
         * Sets the maximum number of segments to extract. Optional.
         *
         * If unset or zero, the entire metric name will be used.
         *
         * @param maxSegments the segments.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setMaxSegments(final int maxSegments) {
            _maxSegments = maxSegments;
            return this;
        }
    }
}
