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

package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import models.internal.alerts.AlertEvaluationResult;
import net.sf.oval.constraint.NotBlank;
import net.sf.oval.constraint.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Default implementation for an {@code AlertEvaluationResult}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DefaultAlertEvaluationResult implements AlertEvaluationResult {
    private final String _seriesName;
    private final ImmutableList<ImmutableMap<String, String>> _firingTags;
    private final ImmutableList<String> _groupBys;

    private DefaultAlertEvaluationResult(final Builder builder) {
        _seriesName = builder._seriesName;
        _firingTags = builder._firingTags;
        _groupBys = builder._groupBys;
    }

    @Override
    public String getSeriesName() {
        return _seriesName;
    }

    @Override
    public ImmutableList<ImmutableMap<String, String>> getFiringTags() {
        return _firingTags;
    }

    @Override
    public ImmutableList<String> getGroupBys() {
        return _groupBys;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAlertEvaluationResult that = (DefaultAlertEvaluationResult) o;
        return Objects.equal(_firingTags, that._firingTags);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_firingTags);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("seriesName", _seriesName)
                .add("firingTags", _firingTags)
                .toString();
    }

    /**
     * Builder class for instances of {@code DefaultAlertEvaluationResult}.
     */
    public static class Builder extends OvalBuilder<DefaultAlertEvaluationResult> {
        private ImmutableList<ImmutableMap<String, String>> _firingTags = ImmutableList.of();

        @NotNull
        @NotBlank
        private @Nullable String _seriesName;
        private ImmutableList<String> _groupBys = ImmutableList.of();

        /**
         * Default constructor for an empty builder.
         */
        public Builder() {
            super(DefaultAlertEvaluationResult::new);
        }

        /**
         * Set the seriesName. Required. Cannot be null or empty.
         *
         * @param seriesName the series seriesName
         * @return This instance of {@code Builder}.
         */
        public Builder setSeriesName(@Nullable final String seriesName) {
            _seriesName = seriesName;
            return this;
        }

        /**
         * Set the tag group-bys. Defaults to empty.
         *
         * @param groupBys The list of tag group-bys.
         * @return This instance of {@code Builder}.
         */
        public Builder setGroupBys(final List<String> groupBys) {
            _groupBys = ImmutableList.copyOf(groupBys);
            return this;
        }

        /**
         * Set the firing tags. Defaults to empty.
         *
         * @param firingTags The set of firing tags.
         * @return This instance of {@code Builder}.
         */
        public Builder setFiringTags(final List<Map<String, String>> firingTags) {
            _firingTags = firingTags.stream().map(ImmutableMap::copyOf).collect(ImmutableList.toImmutableList());
            return this;
        }
    }
}
