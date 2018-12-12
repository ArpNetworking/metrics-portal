package com.arpnetworking.metrics.portal.reports;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

public class Report {

    public String getTitle() {
        return title;
    }

    public Report(String title, @Nullable String html, @Nullable byte[] pdf) {
        this.title = title;
        this.html = html;
        this.pdf = pdf;
    }

    public @Nullable String getHtml() {
        return html;
    }

    public @Nullable byte[] getPdf() {
        return pdf;
    }

    @Override
    public String toString() {
        return "Report{"
                +"title='"+title+"'"
                +", html.length="+(html == null ? "<null>" : html.length())
                +", pdf.length="+(pdf == null ? "<null>" : pdf.length)
                +"}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Report report = (Report) o;
        return Objects.equals(title, report.title) &&
                Objects.equals(html, report.html) &&
                Arrays.equals(pdf, report.pdf);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(title, html);
        result = 31 * result + Arrays.hashCode(pdf);
        return result;
    }

    private final String title;
    private final @Nullable String html;
    private final @Nullable byte[] pdf;
}
