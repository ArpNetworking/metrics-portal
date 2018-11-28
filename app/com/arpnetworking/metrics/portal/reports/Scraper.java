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

/**
 * Takes a page screenshot.
 *
 * @author Kenan Klisura
 */
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

    public static class Snapshot { public String html; public byte[] pdf;

        public Snapshot(String html, byte[] pdf) {
            this.html = html;
            this.pdf = pdf;
        }
    }

    public static Optional<Snapshot> takeGrafanaReportScreenshot(ChromeDevToolsService devToolsService, String url, long timeoutMillis) {
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
                timeoutMillis
        );
    }

    public static Optional<Snapshot> takeScreenshot(ChromeDevToolsService devToolsService, String url, String jsPrepareCmd, long timeoutMillis) {
        return takeScreenshot(
                devToolsService,
                url,
                (ChromeDevToolsService dts) -> dts.getRuntime().evaluate(jsPrepareCmd).getResult().getValue().equals(true),
                timeoutMillis
        );
    }

    public static Optional<Snapshot> takeScreenshot(ChromeDevToolsService devToolsService, String url, Function<ChromeDevToolsService, Boolean> prepare, long timeoutMillis) {
        System.out.println("in takeScreenshot");

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
                                                    true, // landscape
                                                    false, // displayHeaderFooter
                                                    false, // printBackground
                                                    1.0, // scale
                                                    30.0, // height -- misdocumented as paperWidth
                                                    8.5, // width -- misdocumented as paperHeight
                                                    0.4, // marginTop
                                                    0.4, // marginBottom
                                                    0.4, // marginLeft
                                                    0.4, // marginRight
                                                    "", // pageRanges
                                                    true, // ignoreInvalidPageRanges
                                                    "", // headerTemplate
                                                    "", // footerTemplate
                                                    true// preferCSSPageSize
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
