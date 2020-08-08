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

package models.view.alerts;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Nested view model for an alert's firing state.
 * <p>
 * Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class AlertFiringState {
    @Nullable
    private Instant _lastEvaluatedAt;
    private ImmutableList<String> _groupBys;
    private ImmutableList<ImmutableMap<String, String>> _firingTags = ImmutableList.of();
    private Instant _queryStartTime;
    private Instant _queryEndTime;

    /**
     * Get the last evaluated time, if any.
     *
     * @return lastEvaluated the instant this alert was last evaluated.
     */
    public Optional<Instant> getLastEvaluatedAt() {
        return Optional.ofNullable(_lastEvaluatedAt);
    }

    /**
     * The start of the query time range at the time of last evaluation.
     *
     * @return the inclusive start of the query range.
     */
    public Instant getQueryStartTime() {
        return _queryStartTime;
    }

    /**
     * The end of the query time range at the time of last evaluation.
     *
     * @return the inclusive end of the query range.
     */
    public Instant getQueryEndTime() {
        return _queryEndTime;
    }

    /**
     * Get the list of firing tag sets for this alert.
     *
     * @return The list of firing tag sets.
     */
    public ImmutableList<String> getGroupBys() {
        return _groupBys;
    }


    /**
     * Get the list of firing tag sets for this alert.
     *
     * @return The list of firing tag sets.
     */
    public ImmutableList<ImmutableMap<String, String>> getFiringTags() {
        return _firingTags;
    }

    /**
     * Sets the query start time.
     *
     * @param queryStartTime the inclusive start of the query range.
     */
    public void setQueryStartTime(final Instant queryStartTime) {
        _queryStartTime = queryStartTime;
    }

    /**
     * Sets the query start time.
     *
     * @param queryEndTime the exclusive end of the query range.
     */
    public void setQueryEndTime(final Instant queryEndTime) {
        _queryEndTime = queryEndTime;
    }

    /**
     * Sets the last evaluated time.
     *
     * @param lastEvaluated the instant this alert was last evaluated.
     */
    public void setLastEvaluatedAt(@Nullable final Instant lastEvaluated) {
        _lastEvaluatedAt = lastEvaluated;
    }

    /**
     * Sets the groupBys.
     *
     * @param groupBys the group bys.
     */
    public void setGroupBys(final ImmutableList<String> groupBys) {
        _groupBys = groupBys;
    }

    /**
     * Sets the firing tag sets.
     *
     * @param firingFor the firing tag sets.
     */
    public void setFiringTags(final ImmutableList<ImmutableMap<String, String>> firingFor) {
        _firingTags = firingFor;
    }
}
