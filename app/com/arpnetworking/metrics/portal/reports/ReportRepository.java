package com.arpnetworking.metrics.portal.reports;

import javax.annotation.Nullable;

public interface ReportRepository {
    @Nullable ReportSpec getSpec(String id);
    String add(ReportSpec spec);
    void open();
    void close();
}
