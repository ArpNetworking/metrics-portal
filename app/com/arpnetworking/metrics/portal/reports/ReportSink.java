package com.arpnetworking.metrics.portal.reports;

public interface ReportSink {
    void send(Report r) throws Exception;
}
