package com.arpnetworking.metrics.portal.reports;

import java.util.concurrent.CompletionStage;

public interface ReportRenderer<S extends ReportSpec> {
    CompletionStage<Report> render(S spec);
}
