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

    private final CompletableFuture<Report> result = new CompletableFuture<>();
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    public CompletionStage<Report> render(ChromeScreenshotReportSpec spec) {
        // Yes, the ChromeDevToolsService should be Guiced.
        // But then _this_ class would have to be Guiced,
        //   and then either ReportSpecs would always have to be Guiced
        //     (unacceptable; they're practically structs)
        //   or ReportSpec.render() would have to be split out into a separate ReportRenderer class
        //   which would also have to be Guiced
        //   and then the JobExecutor would have to be Guiced
        //   and then the JobScheduler would have to be Guiced
        //   --well, it already is. But this would still be a huge refactor.
        // And it doesn't _actually_ buy us anything.
        //   Testability? No, we can just test the two-argument render().
        //   Flexability? I... don't really see how.
        // Thank you for reading my essay.
        return render(spec, ChromeDevToolsFactory.create(spec.isIgnoreCertificateErrors()));
    }

    protected static final String TRIGGER_MESSAGE = "com.arpnetworking.metrics.portal.reports.impl.ChromeScreenshotTaker says, take the screenshot now please";
    protected CompletionStage<Report> render(ChromeScreenshotReportSpec spec, ChromeDevToolsService dts) {
        if (!isStarted.getAndSet(true)) {
            dts.onLog(msg -> {

                if (!msg.equals(TRIGGER_MESSAGE)) {
                    return;
                }

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
