package com.arpnetworking.metrics.portal.alerts;

import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;

import java.util.concurrent.CompletionStage;

public interface AlertNotifier {
    CompletionStage<Void> notify(final Alert alert, final AlertEvaluationResult result, final String message);
}
