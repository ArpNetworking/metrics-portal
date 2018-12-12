package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Report;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import play.libs.Json;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChromeScreenshotTaker {

    @Inject
    private static ChromeDevToolsFactory devToolsFactory;

    private final CompletableFuture<Report> result = new CompletableFuture<>();
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    public CompletionStage<Report> render(ChromeScreenshotReportSpec spec) {
        return render(spec, devToolsFactory.create(spec.isIgnoreCertificateErrors()));
    }

    protected static final String TRIGGER_MESSAGE = "com.arpnetworking.metrics.portal.reports.impl.ChromeScreenshotTaker says, take the screenshot now please";
    protected CompletionStage<Report> render(ChromeScreenshotReportSpec spec, ChromeDevToolsService dts) {
        if (!isStarted.getAndSet(true)) {
            dts.onLog(msg -> {

                if (!msg.equals(TRIGGER_MESSAGE)) {
                    return;
                }

                System.out.println("Got trigger message");
                LOGGER.info()
                        .setMessage("taking screenshot")
                        .addData("url", spec.getUrl())
                        .log();
                try {
                    Report r = reportFromPage(dts, spec.getPdfWidthInches(), spec.getPdfHeightInches());
                    result.complete(r);
                    LOGGER.debug()
                            .setMessage("took screenshot successfully")
                            .addData("url", spec.getUrl())
                            .addData("html_length", (r.getHtml()==null) ? "<null>" : r.getHtml().length())
                            .log();
                } catch (Throwable err) {
                    result.completeExceptionally(err);
                    LOGGER.info()
                            .setMessage("failed to take screenshot")
                            .addData("url", spec.getUrl())
                            .setThrowable(err)
                            .log();
                } finally {
                    dts.close();
                }
            });

            dts.onLoad(() -> {
                dts.evaluate("(() => {"+spec.getJsRunOnLoad()+"})()");
                dts.evaluate("window.addEventListener("+Json.toJson(spec.getTriggeringEventName())+", () => console.log("+Json.toJson(TRIGGER_MESSAGE)+"))");
            });
            dts.navigate(spec.getUrl());
        }
        return result;
    }

    private Report reportFromPage(ChromeDevToolsService devToolsService, Double pdfWidthInches, Double pdfHeightInches) {
        String html = (String) devToolsService.evaluate("document.documentElement.outerHTML");
        byte[] pdf = devToolsService.printToPdf(pdfWidthInches, pdfHeightInches);

        return new Report(html, pdf);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromeScreenshotTaker.class);
}
