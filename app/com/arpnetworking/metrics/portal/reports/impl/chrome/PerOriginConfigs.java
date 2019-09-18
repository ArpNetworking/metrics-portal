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
import net.sf.oval.constraint.NotNull;

import java.net.URI;
import java.util.Objects;
import java.util.function.Function;

/**
 * Describes special actions that should be done for each of several origins (i.e. scheme+host+port).
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PerOriginConfigs {

    private PerOriginConfigs(final Builder builder) {
        _byOrigin = builder._byOrigin;
        _allowEverything = builder._allowEverything;
    }

    private final ImmutableMap<String, OriginConfig> _byOrigin;
    private final boolean _allowEverything;

    /**
     * Tests whether a browser should be allowed to navigate to a URI.
     *
     * @param uri The URI to be navigated to.
     * @return Whether a browser should be allowed to navigate to that URI.
     */
    public boolean isNavigationAllowed(final URI uri) {
        // TODO(spencerpearson): this should really return an ImmutableList<Problem> to describe _why_
        //   the URI is rejected (non-whitelisted domain, vs non-whitelisted path within domain, vs maybe others someday)
        return _allowEverything || getOrDefault(uri, oconf -> oconf.isNavigationAllowed(uri.getPath()), false);
    }

    /**
     * Tests whether a browser should be allowed to make a request to a URI.
     *
     * @param uri The URI to be requested.
     * @return Whether a browser should be allowed to make a request to that URI.
     */
    public boolean isRequestAllowed(final URI uri) {
        return _allowEverything || getOrDefault(uri, oconf -> oconf.isRequestAllowed(uri.getPath()), false);
    }

    /**
     * Gets any additional headers that should be added to requests to a particular URI.
     *
     * @param uri The URI to be requested.
     * @return Any additional headers to add to that request.
     */
    public ImmutableMap<String, String> getAdditionalHeaders(final URI uri) {
        return getOrDefault(uri, OriginConfig::getAdditionalHeaders, ImmutableMap.of());
    }

    /**
     * Overload of {@link #isNavigationAllowed(URI)}.
     *
     * @param uri The URI to be navigated to.
     * @return Whether a browser should be allowed to navigate to that URI.
     */
    public boolean isNavigationAllowed(final String uri) {
        return isNavigationAllowed(URI.create(uri));
    }

    /**
     * Overload of {@link #isRequestAllowed(URI)}.
     *
     * @param uri The URI to be requested.
     * @return Whether a browser should be allowed to make a request to that URI.
     */
    public boolean isRequestAllowed(final String uri) {
        return isRequestAllowed(URI.create(uri));
    }

    /**
     * Overload of {@link #getAdditionalHeaders(URI)}.
     *
     * @param uri The URI to be requested.
     * @return Any additional headers to add to that request.
     */
    public ImmutableMap<String, String> getAdditionalHeaders(final String uri) {
        return getAdditionalHeaders(URI.create(uri));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_byOrigin", _byOrigin)
                .add("_allowEverything", _allowEverything)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PerOriginConfigs that = (PerOriginConfigs) o;
        return _allowEverything == that._allowEverything
                && _byOrigin.equals(that._byOrigin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_byOrigin, _allowEverything);
    }

    private String getOrigin(final URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private <T> T getOrDefault(final URI uri, final Function<OriginConfig, T> f, final T defaultValue) {
        final OriginConfig config = _byOrigin.get(getOrigin(uri));
        if (config == null) {
            return defaultValue;
        }
        return f.apply(config);
    }


    /**
     * Builder implementation that constructs {@code OriginConfig}.
     */
    public static final class Builder extends OvalBuilder<PerOriginConfigs> {
        /**
         * Public Constructor.
         */
        public Builder() {
            super(PerOriginConfigs::new);
        }

        /**
         * Set whether all requests/navigation will be globally allowed. Optional. Defaults to false.
         *
         * @param allowEverything Whether to allow everything by default.
         * @return This instance of {@code Builder}.
         */
        public Builder setAllowEverything(final boolean allowEverything) {
            _allowEverything = allowEverything;
            return this;
        }

        /**
         * Set the configurations for all origins. Required. Cannot be null.
         *
         * @param byOrigin The configs for all origins.
         * @return This instance of {@code Builder}.
         */
        public Builder setByOrigin(final ImmutableMap<String, OriginConfig> byOrigin) {
            _byOrigin = byOrigin;
            return this;
        }

        @NotNull
        private ImmutableMap<String, OriginConfig> _byOrigin;
        private boolean _allowEverything = false;
    }
}
