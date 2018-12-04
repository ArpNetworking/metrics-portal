package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.ReportRepository;
import com.arpnetworking.metrics.portal.reports.ReportSpec;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class MapReportRepository implements ReportRepository {
    @Inject
    public MapReportRepository() {
        this.nonce = 0L;
        this.specs = new HashMap<>();
    }

    private Long nonce;
    private Map<String, ReportSpec> specs;
    @Override
    public @Nullable ReportSpec getSpec(String id) {
        return this.specs.get(id);
    }

    @Override
    public String add(ReportSpec spec) {
        String id = (this.nonce++).toString();
        this.specs.put(id, spec);
        return id;
    }

    @Override
    public void open() {

    }

    @Override
    public void close() {

    }
}
