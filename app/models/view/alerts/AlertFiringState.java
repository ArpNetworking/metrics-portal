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

/**
 * Nested view model for an alert's firing state.
 *
 * Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class AlertFiringState {
    private Instant _lastEvaluated;
    private ImmutableList<ImmutableMap<String, String>> _firingTagSets;

    public Instant getLastEvaluated() {
        return _lastEvaluated;
    }

    public ImmutableList<ImmutableMap<String, String>> getFiringTagSets() {
        return _firingTagSets;
    }

    /**
     * Sets the last evaluated time.
     *
     * @param lastEvaluated the instant this alert was last evaluated.
     */
    public void setLastEvaluated(final Instant lastEvaluated) {
        _lastEvaluated = lastEvaluated;
    }

    /**
     * Sets the firing tag sets.
     *
     * @param firingTagSets the firing tag sets.
     */
    public void setFiringTagSets(final ImmutableList<ImmutableMap<String, String>> firingTagSets) {
        _firingTagSets = firingTagSets;
    }
}
