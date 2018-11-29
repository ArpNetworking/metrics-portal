package com.arpnetworking.metrics.portal.reports;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
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


public class Scraper {
    public static void main(String[] args) throws Exception {
        final ChromeDevToolsService devToolsService = createDevToolsService();
        devToolsService.getSecurity().setIgnoreCertificateErrors(true);

        final Optional<Snapshot> screenshot = takeScreenshot(
                devToolsService,
                "https://localhost:9450/d/tdJITcBmz/playground?orgId=1&theme=light",
                "(() => {\n" +
                "    var e = document.getElementsByClassName('rendered-markdown-container')[0];\n" +
                "    if (!e) return false;\n" +
                "    var s = e.srcdoc;\n" +
                "    document.open(); document.write(s); document.close();\n" +
                "    return true;\n" +
                "})()",
                new Dimensions(8.5, 30.0),
                10000
        );
        if (screenshot.isPresent()) Files.write(Paths.get("screenshot.pdf"), screenshot.get().pdf);
        if (screenshot.isPresent()) Files.write(Paths.get("screenshot.html"), screenshot.get().html.getBytes());
        else {System.out.println("timed out");}
    }

    public static ChromeDevToolsService createDevToolsService() {
        return createDevToolsService(false);
    }
    public static ChromeDevToolsService createDevToolsService(boolean ignoreCertificateErrors) {
        final ChromeLauncher launcher = new ChromeLauncher();
        final ChromeService chromeService = launcher.launch(true);
        final ChromeTab tab = chromeService.createTab();
        ChromeDevToolsService result = chromeService.createDevToolsService(tab);
        if (ignoreCertificateErrors) {
            result.getSecurity().setIgnoreCertificateErrors(true);
        }
        return result;
    }

    public static class Dimensions {
        public double width, height;
        public Dimensions(double width, double height) {
            this.width = width;
            this.height = height;
        }
    }

    public static class Snapshot {
        public String html;
        public byte[] pdf;
        public Snapshot(String html, byte[] pdf) {
            this.html = html;
            this.pdf = pdf;
        }
    }

    public static Optional<Snapshot> takeGrafanaReportScreenshot(
            ChromeDevToolsService devToolsService,
            String url,
            Dimensions pdfSizeInches,
            long timeoutMillis
    ) {
        return takeScreenshot(
                devToolsService,
                url,
                "(() => {\n" +
                        "    var e = document.getElementsByClassName('rendered-markdown-container')[0];\n" +
                        "    if (!e) return false;\n" +
                        "    var s = e.srcdoc;\n" +
                        "    document.open(); document.write(s); document.close();\n" +
                        "    return true;\n" +
                        "})()",
                pdfSizeInches,
                timeoutMillis
        );
    }

    public static Optional<Snapshot> takeScreenshot(
            ChromeDevToolsService devToolsService,
            String url,
            String jsPrepareCmd,
            Dimensions pdfSizeInches,
            long timeoutMillis
    ) {
        return takeScreenshot(
                devToolsService,
                url,
                (ChromeDevToolsService dts) -> dts.getRuntime().evaluate(jsPrepareCmd).getResult().getValue().equals(true),
                pdfSizeInches,
                timeoutMillis
        );
    }

    public static Optional<Snapshot> takeScreenshot(
            ChromeDevToolsService devToolsService,
            String url,
            Function<ChromeDevToolsService, Boolean> prepare,
            Dimensions pdfSizeInches,
            long timeoutMillis
    ) {
        // adapted from https://github.com/kklisura/chrome-devtools-java-client/blob/master/cdt-examples/src/main/java/com/github/kklisura/cdt/examples/TakeScreenshotExample.java

        final Page page = devToolsService.getPage();

        AtomicReference<Optional<Snapshot>> result = new AtomicReference<>(Optional.empty());
        AtomicBoolean firstLoad = new AtomicBoolean(true);
        page.onLoadEventFired(
                event -> {
                    if (!firstLoad.getAndSet(false)) {return;}
                    try {
                        long t0 = System.currentTimeMillis();
                        long tf = t0 + timeoutMillis;
                        while (System.currentTimeMillis() < tf) {
                            try {
                                if (prepare.apply(devToolsService)) {
                                    System.out.println("Taking screenshot...");
                                    result.set(Optional.of(new Snapshot(
                                            (String)devToolsService.getRuntime().evaluate("document.documentElement.outerHTML").getResult().getValue(),
                                            Base64.getDecoder().decode(devToolsService.getPage().printToPDF(
                                                    false,
                                                    false,
                                                    false,
                                                    1.0,
                                                    pdfSizeInches.width,
                                                    pdfSizeInches.height,
                                                    0.4,
                                                    0.4,
                                                    0.4,
                                                    0.4,
                                                    "",
                                                    true,
                                                    "",
                                                    "",
                                                    true
                                            )))));

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
//                        try {Thread.sleep(10000);} catch (InterruptedException e) {}
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
