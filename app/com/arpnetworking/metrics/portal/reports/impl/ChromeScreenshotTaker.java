package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Report;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.protocol.commands.Page;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.types.ChromeTab;
import play.libs.Json;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChromeScreenshotTaker {

    private final CompletableFuture<Report> result = new CompletableFuture<>();
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    public CompletionStage<Report> render(ChromeScreenshotReportSpec spec) {
        return render(spec, createDevToolsService(spec.isIgnoreCertificateErrors()));
    }

    private static final String TRIGGER_MESSAGE = "com.arpnetworking.metrics.portal.reports.impl.ChromeScreenshotTaker says, take the screenshot now please";
    protected CompletionStage<Report> render(ChromeScreenshotReportSpec spec, ChromeDevToolsService dts) {
        if (!isStarted.getAndSet(true)) {
            dts.getConsole().enable();
            dts.getConsole().onMessageAdded(e -> {
                if (e.getMessage().getText().equals(TRIGGER_MESSAGE)) {
                    LOGGER.info()
                            .setMessage("taking screenshot")
                            .addData("url", spec.getUrl())
                            .log();
                    try {
                        Report r = reportFromPage(dts, spec.getTitle(), spec.getPdfWidthInches(), spec.getPdfHeightInches());
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
                }
            });

            final Page page = dts.getPage();
            page.onLoadEventFired(_e -> {
                dts.getRuntime().evaluate("(() => {"+spec.getJsRunOnLoad()+"})()");
                dts.getRuntime().evaluate("window.addEventListener("+Json.toJson(spec.getTriggeringEventName())+", () => console.log("+Json.toJson(TRIGGER_MESSAGE)+"))");
            });
            page.enable();
            page.navigate(spec.getUrl());
        }
        return result;
    }

    private static ChromeDevToolsService createDevToolsService(boolean ignoreCertificateErrors) {
        final ChromeLauncher launcher = new ChromeLauncher();
        final ChromeService chromeService = launcher.launch(true);
        final ChromeTab tab = chromeService.createTab();
        ChromeDevToolsService result = chromeService.createDevToolsService(tab);
        if (ignoreCertificateErrors) {
            result.getSecurity().setIgnoreCertificateErrors(true);
        }
        return result;
    }

    private Report reportFromPage(ChromeDevToolsService devToolsService, String title, Double pdfWidthInches, Double pdfHeightInches) {
        String html = (String)devToolsService.getRuntime().evaluate("document.documentElement.outerHTML").getResult().getValue();

        byte[] pdf = Base64.getDecoder().decode(devToolsService.getPage().printToPDF(
                    false,
                    false,
                    false,
                    1.0,
                    pdfWidthInches,
                    pdfHeightInches,
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

        return new Report(title, html, pdf);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromeScreenshotTaker.class);
}
