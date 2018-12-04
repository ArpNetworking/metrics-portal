package com.arpnetworking.metrics.portal.reports;

public class ReportSpec {
    private String name;
    private ReportGenerator generator;
    private ReportSink sink;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ReportGenerator getGenerator() {
        return generator;
    }

    public void setGenerator(ReportGenerator generator) {
        this.generator = generator;
    }

    public ReportSink getSink() {
        return sink;
    }

    public void setSink(ReportSink sink) {
        this.sink = sink;
    }

    public ReportSpec(String name, ReportGenerator generator, ReportSink sink) {
        this.name = name;
        this.generator = generator;
        this.sink = sink;
    }

    public void run() throws Exception {
        Report r;
        try {
            r = generator.generateReport();
        } catch (Exception e) {
            r = new Report("Report '"+name+"' failed", "Reason: <pre>"+e+"</pre>", null);
        }
        sink.send(r);
    }
}
