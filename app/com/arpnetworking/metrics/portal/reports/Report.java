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

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

public class Report {

    public Report(@Nullable String html, @Nullable byte[] pdf) {
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
                +"html.length="+(html == null ? "<null>" : html.length())
                +", pdf.length="+(pdf == null ? "<null>" : pdf.length)
                +"}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Report report = (Report) o;
        return Objects.equals(html, report.html) &&
                Arrays.equals(pdf, report.pdf);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(html);
        result = 31 * result + Arrays.hashCode(pdf);
        return result;
    }

    private final @Nullable String html;
    private final @Nullable byte[] pdf;
}
