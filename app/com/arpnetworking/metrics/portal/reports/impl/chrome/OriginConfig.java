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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Describes special actions that should be done for some origin (i.e. scheme+host+port).
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class OriginConfig {

    private OriginConfig(final Builder builder) {
        _allowedNavigationPaths = ImmutableSet.copyOf(builder._allowedNavigationPaths);
        _allowedRequestPaths = ImmutableSet.copyOf(builder._allowedRequestPaths);
        _additionalHeaders = ImmutableMap.copyOf(builder._additionalHeaders);
    }

    private final ImmutableSet<String> _allowedNavigationPaths;
    private final ImmutableSet<String> _allowedRequestPaths;
    private final ImmutableMap<String, String> _additionalHeaders;

    /**
     * Tests whether a browser should be allowed to navigate to a path.
     *
     * @param path The path to be navigated to.
     * @return Whether a browser should be allowed to navigate to that path.
     */
    public boolean isNavigationAllowed(final String path) {
        // We _could_ precompile all the patterns so they don't need to be re-compiled each time,
        //   but this is in the context of making HTTP requests: compiling a regex is not our bottleneck here.
        return _allowedNavigationPaths.stream().anyMatch(pattern -> Pattern.compile(pattern).matcher(path).matches());
    }

    /**
     * Tests whether a browser should be allowed to make a request to a path.
     *
     * @param path The path to be requested.
     * @return Whether requests should be allowed to that path.
     */
    public boolean isRequestAllowed(final String path) {
        return isNavigationAllowed(path)
                || _allowedRequestPaths.stream().anyMatch(pattern -> Pattern.compile(pattern).matcher(path).matches());
    }

    public ImmutableSet<String> getAllowedNavigationPaths() {
        return _allowedNavigationPaths;
    }

    public ImmutableSet<String> getAllowedRequestPaths() {
        return _allowedRequestPaths;
    }

    public ImmutableMap<String, String> getAdditionalHeaders() {
        return _additionalHeaders;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final OriginConfig that = (OriginConfig) o;
        return _allowedNavigationPaths.equals(that._allowedNavigationPaths)
                && _allowedRequestPaths.equals(that._allowedRequestPaths)
                && _additionalHeaders.equals(that._additionalHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_allowedNavigationPaths, _allowedRequestPaths, _additionalHeaders);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("allowedNavigationPaths", _allowedNavigationPaths)
                .add("allowedRequestPaths", _allowedRequestPaths)
                .add("additionalHeaders", _additionalHeaders)
                .toString();
    }

    /**
     * Builder implementation that constructs {@code OriginConfig}.
     */
    public static final class Builder extends OvalBuilder<OriginConfig> {
        /**
         * Public Constructor.
         */
        public Builder() {
            super(OriginConfig::new);
        }

        /**
         * Set the patterns of paths to which navigation should be allowed. Optional. Defaults to empty set.
         *
         * @param allowedNavigationPaths The path patterns.
         * @return This instance of {@code Builder}.
         */
        public Builder setAllowedNavigationPaths(final ImmutableSet<String> allowedNavigationPaths) {
            _allowedNavigationPaths = allowedNavigationPaths;
            return this;
        }

        /**
         * Add a pattern to the set of paths that requests should be allowed to. Optional. Defaults to empty set.
         *
         * @param allowedRequestPaths The path pattern.
         * @return This instance of {@code Builder}.
         */
        public Builder setAllowedRequestPaths(final ImmutableSet<String> allowedRequestPaths) {
            _allowedRequestPaths = allowedRequestPaths;
            return this;
        }

        /**
         * Add a header that should be added to all requests to this origin. Optional. Defaults to empty map.
         *
         * @param additionalHeaders The header name.
         * @return This instance of {@code Builder}.
         */
        public Builder setAdditionalHeaders(final ImmutableMap<String, String> additionalHeaders) {
            _additionalHeaders = additionalHeaders;
            return this;
        }

        private Set<String> _allowedNavigationPaths = ImmutableSet.of();
        private Set<String> _allowedRequestPaths = ImmutableSet.of();
        private ImmutableMap<String, String> _additionalHeaders = ImmutableMap.of();
    }
}
