/*
 * Copyright 2015 Groupon.com
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
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import models.internal.MetricsQuery;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * View model for an alert.
 * <p>
 * Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class Alert {
    private UUID _id;
    private String _name;
    private String _description;
    private MetricsQuery _query;
    private boolean _enabled;
    private ImmutableMap<String, Object> _additionalMetadata;
    private @Nullable AlertFiringState _firingState;

    /**
     * Construct a view model from its internal representation.
     *
     * @param internal The internal model for this alert.
     * @param mostRecentEvaluation The most recent evaluation result for this alert.
     * @return
     */
    public static Alert fromInternal(
            final models.internal.alerts.Alert internal,
            final Optional<JobExecution.Success<AlertEvaluationResult>> mostRecentEvaluation
    ) {
        final Alert alert = new Alert();

        alert._id = internal.getId();
        alert._name = internal.getName();
        alert._description = internal.getDescription();
        alert._enabled = internal.isEnabled();
        alert._additionalMetadata = internal.getAdditionalMetadata();

        mostRecentEvaluation.ifPresent(evaluation -> {
            final Instant lastEvaluated = evaluation.getCompletedAt();
            final AlertEvaluationResult result = evaluation.getResult();

            final AlertFiringState firingState = new AlertFiringState();
            firingState.setLastEvaluated(lastEvaluated);
            firingState.setFiringTagSets(result.getFiringTags());
            alert._firingState = firingState;
        });

        return alert;
    }

    public UUID getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }

    public String getDescription() {
        return _description;
    }

    public MetricsQuery getQuery() {
        return _query;
    }

    public boolean isEnabled() {
        return _enabled;
    }

    public ImmutableMap<String, Object> getAdditionalMetadata() {
        return _additionalMetadata;
    }

    @Nullable
    public AlertFiringState getFiringState() {
        return _firingState;
    }
}
