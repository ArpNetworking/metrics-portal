package com.arpnetworking.metrics.portal.reports;

import java.util.concurrent.CompletionStage;

public interface ReportSpec {
    CompletionStage<Report> render();
}
