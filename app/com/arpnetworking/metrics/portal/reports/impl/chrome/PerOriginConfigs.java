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
import com.google.common.collect.ImmutableMap;
import net.sf.oval.constraint.NotNull;

import java.net.URI;

/**
 * Describes special actions that should be done for each of several origins (i.e. scheme+host+port).
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PerOriginConfigs {

    private PerOriginConfigs(final Builder builder) {
        _byOrigin = builder._byOrigin;
    }

    private final ImmutableMap<String, OriginConfig> _byOrigin;

    /**
     * Tests whether a browser should be allowed to navigate to a URI.
     *
     * @param uri The URI to be navigated to.
     * @return Whether a browser should be allowed to navigate to that URI.
     */
    public boolean isNavigationAllowed(final URI uri) {
        final OriginConfig config = _byOrigin.get(getOrigin(uri));
        if (config == null) {
            return false;
        }
        return config.isNavigationAllowed(uri.getPath());
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
     * Tests whether a browser should be allowed to make a request to a URI.
     *
     * @param uri The URI to be requested.
     * @return Whether a browser should be allowed to make a request to that URI.
     */
    public boolean isRequestAllowed(final URI uri) {
        final OriginConfig config = _byOrigin.get(getOrigin(uri));
        if (config == null) {
            return false;
        }
        return config.isRequestAllowed(uri.getPath());
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

    private String getOrigin(final URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
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
        private ImmutableMap<String, OriginConfig> _byOrigin = null;
    }
}
