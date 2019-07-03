package com.arpnetworking.metrics.portal.reports.impl.chrome.grafana.testing;

import java.time.Duration;

public class Utils {
    public static String mockGrafanaReportPanelPage(final Duration renderDelay) {
        return ""
                + "<html>\n"
                + "<head><script>\n"
                + "  function mockRenderCompletion() {\n"
                + "    document.body.innerHTML = `\n"
                + "      <iframe class=\"rendered-markdown-container\" srcdoc=\"content we care about\"></iframe>\n"
                + "    `;\n"
                + "    window.dispatchEvent(new Event('reportrendered'));\n"
                + "  }\n"
                + "  window.addEventListener('load', () => setTimeout(mockRenderCompletion, " + renderDelay.toMillis() + "));\n"
                + "</script></head>\n"
                + "<body>content we do not care about</body>\n"
                + "</html>\n";
    }
}
