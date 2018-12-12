package com.arpnetworking.metrics.portal.reports.impl;

import java.util.function.Consumer;

public interface ChromeDevToolsService {
    Object evaluate(String js);
    byte[] printToPdf(double pageWidth, double pageHeight);
    void navigate(String url);
    void onLoad(Runnable callback);
    void onLog(Consumer<String> callback);
    void close();
}
