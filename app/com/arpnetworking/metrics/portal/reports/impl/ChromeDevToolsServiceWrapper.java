package com.arpnetworking.metrics.portal.reports.impl;

import com.github.kklisura.cdt.services.ChromeDevToolsService;

import java.util.Base64;
import java.util.function.Consumer;

public class ChromeDevToolsServiceWrapper implements com.arpnetworking.metrics.portal.reports.impl.ChromeDevToolsService {
    private final ChromeDevToolsService dts;
    public ChromeDevToolsServiceWrapper(ChromeDevToolsService dts) {
        this.dts = dts;
    }
    public Object evaluate(String js) {
        return dts.getRuntime().evaluate(js).getResult().getValue();
    }
    public byte[] printToPdf(double pageWidthInches, double pageHeightInches) {
        return Base64.getDecoder().decode(dts.getPage().printToPDF(
                false,
                false,
                false,
                1.0,
                pageWidthInches,
                pageHeightInches,
                0.4,
                0.4,
                0.4,
                0.4,
                "",
                true,
                "",
                "",
                true
        ));
    }
    public void navigate(String url) {
        dts.getPage().navigate(url);
    }
    public void onLoad(Runnable callback) {
        dts.getPage().enable(); dts.getPage().onLoadEventFired(e -> callback.run());
    }
    public void onLog(Consumer<String> callback) {
        dts.getConsole().enable();
        dts.getConsole().onMessageAdded(e -> callback.accept(e.getMessage().getText()));
    }
    public void close() {
        dts.close();
    }
}
