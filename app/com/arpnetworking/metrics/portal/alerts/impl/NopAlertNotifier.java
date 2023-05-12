package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.alerts.AlertNotifier;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class NopAlertNotifier implements AlertNotifier {
    @Override
    public CompletionStage<Void> notify(Alert alert, AlertEvaluationResult result, String message) {
        return CompletableFuture.completedFuture(null);
    }
}
