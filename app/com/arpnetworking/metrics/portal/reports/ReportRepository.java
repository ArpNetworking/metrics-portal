package com.arpnetworking.metrics.portal.reports;

import javax.annotation.Nullable;
import java.util.stream.Stream;

public interface ReportRepository {
    @Nullable ReportSpec getSpec(String id);
    Stream<String> listSpecs();
    String add(ReportSpec spec);
    void open();
    void close();
}
