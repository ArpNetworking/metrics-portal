/*
 * Copyright 2023 Inscope Metrics Inc
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
package com.arpnetworking.metrics.portal.alerts;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;

import java.util.concurrent.CompletionStage;

/**
 * An interface that allows for the notification of alert triggers to external locations.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        property = "type"
)
public interface AlertNotifier {
    /**
     * Notify the external location of the alert trigger.
     *
     * @param alert source alert
     * @param result details of the alert execution
     * @return Void completion stage
     */
    CompletionStage<Void> notify(Alert alert, AlertEvaluationResult result);
}
