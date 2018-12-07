package com.arpnetworking.metrics.portal.reports;

import java.time.temporal.TemporalAmount;

public class Job {
    private ReportSpec spec;
    private ReportSink sink;
    private TemporalAmount period;

    public Job(ReportSpec spec, ReportSink sink, TemporalAmount period) {
        this.spec = spec;
        this.sink = sink;
        this.period = period;
    }

    public ReportSpec getSpec() {
        return spec;
    }

    public void setSpec(ReportSpec spec) {
        this.spec = spec;
    }

    public ReportSink getSink() {
        return sink;
    }

    public void setSink(ReportSink sink) {
        this.sink = sink;
    }

    public TemporalAmount getPeriod() {
        return period;
    }

    public void setPeriod(TemporalAmount period) {
        this.period = period;
    }
}

