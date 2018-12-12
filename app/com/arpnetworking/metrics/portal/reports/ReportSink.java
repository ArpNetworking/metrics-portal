package com.arpnetworking.metrics.portal.reports;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface ReportSink {
    default CompletionStage<Void> send(Report r) {
        return send(CompletableFuture.completedFuture(r));
    };
    CompletionStage<Void> send(CompletionStage<Report> fr);
}
