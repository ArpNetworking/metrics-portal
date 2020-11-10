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

/**
 * A tagger that does not provide any tags.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class NoTagsTagger implements Tagger {

    private static final NoTagsTagger INSTANCE = new NoTagsTagger();

    private NoTagsTagger() { }

    @Override
    public ImmutableMap<String, String> getTags(final String metricName) {
        return ImmutableMap.of();
    }

    /**
     * A builder for instances of {@NoTagsTagger}.
     */
    public static final class Builder {
        /**
         * Construct a new default Builder.
         */
        public Builder() {

        }

        /**
         * Build an instance of {@link NoTagsTagger}.
         * @return an instance of NoTagsTagger.
         */
        public NoTagsTagger build() {
            return INSTANCE;
        }
    }
}
