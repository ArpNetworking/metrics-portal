package com.arpnetworking.metrics.portal.reports;

public class Job {
    private ReportSpec spec;
    private ReportSink sink;
    private Schedule schedule;

    public Job(ReportSpec spec, ReportSink sink, Schedule schedule) {
        this.spec = spec;
        this.sink = sink;
        this.schedule = schedule;
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

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }
}

