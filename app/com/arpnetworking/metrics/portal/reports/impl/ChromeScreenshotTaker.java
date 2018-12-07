package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Report;
import com.arpnetworking.metrics.portal.reports.ReportRenderer;
import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.types.ChromeTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChromeScreenshotTaker {

    private final CompletableFuture<Report> result = new CompletableFuture<>();
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    public CompletionStage<Report> render(ChromeScreenshotReportSpec spec) {
        if (!isStarted.getAndSet(true)) {
            String domain;
            try {
                domain = new URI(spec.getUrl()).getAuthority();
            } catch (URISyntaxException e) {
                LOGGER.error("spec has malformed uri %s", spec.getUrl());
                result.completeExceptionally(e);
                return result;
            }
            ChromeDevToolsService dts = createDevToolsService(spec.isIgnoreCertificateErrors());
            dts.addEventListener(
                    domain,
                    spec.getTriggeringEventName(),
                    e -> {
                        try {
                            result.complete(reportFromPage(dts, spec.getTitle(), spec.getPdfWidthInches(), spec.getPdfHeightInches()));
                        } catch (Throwable err) {
                            result.completeExceptionally(err);
                        }
                    },
                    Object.class // TODO(spencerpearson): what even is this
            );
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportScheduler.class);
}
