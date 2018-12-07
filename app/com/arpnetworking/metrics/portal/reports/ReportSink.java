package com.arpnetworking.metrics.portal.reports;

import java.util.concurrent.CompletableFuture;

public interface ReportSink {
    CompletableFuture<Void> send(Report r);
}
