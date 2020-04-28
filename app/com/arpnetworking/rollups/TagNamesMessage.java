/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.rollups;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Message containing a list of tag names for a metric.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class TagNamesMessage extends FailableMessage {

    public String getMetricName() {
        return _metricName;
    }

    public ImmutableMultimap<String, String> getTags() {
        return _tags;
    }

    private TagNamesMessage(final Builder builder) {
        super(builder);
        _metricName = builder._metricName;
        _tags = builder._tags;
    }

    private final String _metricName;
    private final ImmutableMultimap<String, String> _tags;
    private static final long serialVersionUID = 7474007527385332990L;

    /**
     * Builder class for TagNamesMessage.
     */
    public static final class Builder extends FailableMessage.Builder<Builder, TagNamesMessage> {

        /**
         * Constructs a TagNamesMessage builder.
         */
        public Builder() {
            super(TagNamesMessage::new);
        }

        /**
         * Sets the metric name for this message.
         *
         * @param value metric name
         * @return this builder
         */
        public Builder setMetricName(final String value) {
            _metricName = value;
            return this;
        }

        /**
         * Sets the tag names set for this message.
         *
         * @param value tag names set
         * @return this builder
         */
        public Builder setTags(final ImmutableMultimap<String, String> value) {
            _tags = value;
            return this;
        }

        @Override
        protected void reset() {
            super.reset();;
            _tags = ImmutableMultimap.of();
            _metricName = null;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @NotNull
        @NotEmpty
        private String _metricName;
        @NotNull
        private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();
    }
}
