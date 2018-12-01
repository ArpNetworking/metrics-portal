/*
 * Copyright 2018 Dropbox
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.metrics.portal.reports;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.protocol.commands.Page;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.types.ChromeTab;
import java.io.PrintWriter;


public class ChromeScreenshotReportGenerator implements ReportGenerator {

    public ChromeScreenshotReportGenerator(String url, Function<ChromeDevToolsService, Boolean> checkReady, boolean ignoreCertificateErrors, long timeoutMillis, double pdfWidthInches, double pdfHeightInches) {
        this.url = url;
        this.checkReady = checkReady;
        this.ignoreCertificateErrors = ignoreCertificateErrors;
        this.timeoutMillis = timeoutMillis;
        this.pdfWidthInches = pdfWidthInches;
        this.pdfHeightInches = pdfHeightInches;
    }

    private String url;
    private Function<ChromeDevToolsService, Boolean> checkReady;
    private boolean ignoreCertificateErrors = false;
    private long timeoutMillis = 10000;
    private double pdfWidthInches;
    private double pdfHeightInches;

    @Override
    public Report generateReport() {
        ChromeDevToolsService dts = createDevToolsService();
        return takeScreenshot(dts);
    }

    private ChromeDevToolsService createDevToolsService() {
        final ChromeLauncher launcher = new ChromeLauncher();
        final ChromeService chromeService = launcher.launch(true);
        final ChromeTab tab = chromeService.createTab();
        ChromeDevToolsService result = chromeService.createDevToolsService(tab);
        if (ignoreCertificateErrors) {
            result.getSecurity().setIgnoreCertificateErrors(true);
        }
        return result;
    }

    public Report takeScreenshot(
            ChromeDevToolsService devToolsService
    ) {
        // adapted from https://github.com/kklisura/chrome-devtools-java-client/blob/master/cdt-examples/src/main/java/com/github/kklisura/cdt/examples/TakeScreenshotExample.java

        final Page page = devToolsService.getPage();

        AtomicReference<Report> result = new AtomicReference<>(null);
        AtomicBoolean firstLoad = new AtomicBoolean(true);
        page.onLoadEventFired(
                event -> {
                    if (!firstLoad.getAndSet(false)) {return;}
                    try {
                        long t0 = System.currentTimeMillis();
                        long tf = t0 + timeoutMillis;
                        while (System.currentTimeMillis() < tf) {
                            try {
                                if (checkReady.apply(devToolsService)) {
                                    System.out.println("Taking screenshot...");
                                    result.set(new Report(
                                            "Screenshot",
                                            (String)devToolsService.getRuntime().evaluate("document.documentElement.outerHTML").getResult().getValue(),
                                            Base64.getDecoder().decode(devToolsService.getPage().printToPDF(
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
                                            ))));

                                    System.out.println("Done!");
                                    return;
                                } else {
                                    System.out.println("no target yet");
                                }
                            } catch (Throwable err) {
                                System.out.println("o no: " + err);
                                err.printStackTrace(new PrintWriter(System.err));
                            }
                            System.out.println("sleeping and retrying");
                            Thread.sleep(1000);
                        }
                    } catch (Throwable err) {
                        System.out.println("o no: " + err);
                        err.printStackTrace(new PrintWriter(System.err));
                    } finally {
                        devToolsService.close();
                    }
                }
        );

        // Enable page events.
        page.enable();

        // Navigate to github.com.
        page.navigate(url);

        devToolsService.waitUntilClosed();

        return result.get();
    }
}
