package com.arpnetworking.metrics.portal.reports;

public class Job {
    private final ReportSpec spec;
    private final ReportSink sink;
    private final Schedule schedule;

    public Job(ReportSpec spec, ReportSink sink, Schedule schedule) {
        this.spec = spec;
        this.sink = sink;
        this.schedule = schedule;
    }

    public ReportSpec getSpec() {
        return spec;
    }

    public ReportSink getSink() {
        return sink;
    }

    public Schedule getSchedule() {
        return schedule;
    }

}

