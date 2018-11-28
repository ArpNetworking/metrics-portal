package com.arpnetworking.metrics.portal.reports;

import com.github.kklisura.cdt.services.ChromeDevToolsService;

import javax.mail.Transport;
import java.util.Optional;

public class ScreenshotAndEmail {
    public static void main() throws Exception {
        String[] args = {};
        main(args);
    }
    public static void main(String[] args) throws Exception {
        final String url = "https://localhost:9450/d/tdJITcBmz/playground?orgId=1&theme=light";
        final ChromeDevToolsService devToolsService = TakeScreenshot.createDevToolsService();
        devToolsService.getSecurity().setIgnoreCertificateErrors(true);
//        devToolsService.getEmulation().setVisibleSize(2000, 10000);
        final Optional<TakeScreenshot.Snapshot> screenshot = TakeScreenshot.takeScreenshot(
                devToolsService,
                url,
                "(() => {\n" +
                        "    var e = document.getElementsByClassName('rendered-markdown-container')[0];\n" +
                        "    if (!e) return false;\n" +
                        "    var s = e.srcdoc;\n" +
                        "    document.open(); document.write(s); document.close();\n" +
                        "    return true;\n" +
                        "})()",
                5000
        );
        if (screenshot.isPresent()) {
            System.out.println("html = "+screenshot.get().html);
            Transport.send(SendEmailExample.buildImageEmail(
                    "spencerpearson@dropbox.com",
                    "Example webperf report",
                    screenshot.get().html,
                    screenshot.get().pdf
            ));
        }
        else {
            System.out.println("timed out");
        }

    }
}
