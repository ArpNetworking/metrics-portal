/*
 * Copyright 2019 Dropbox, Inc.
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

package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.base.MoreObjects;
import models.internal.reports.ReportFormat;

/**
 * An HTML report format.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class HtmlReportFormat implements ReportFormat {
    private static final HtmlReportFormat INSTANCE = new HtmlReportFormat(Builder.INSTANCE);

    private HtmlReportFormat(final Builder builder) {}

    @Override
    public <T> T accept(final Visitor<T> formatVisitor) {
        return formatVisitor.visit(this);
    }

    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .toString();
    }

    /**
     * Builder implementation that constructs {@code HtmlReportFormat}.
     */
    public static final class Builder extends OvalBuilder<HtmlReportFormat> {
        private static final Builder INSTANCE = new Builder();

        /**
         * Public Constructor.
         */
        public Builder() {
            super(HtmlReportFormat::new);
        }

        @Override
        public HtmlReportFormat build() {
            return HtmlReportFormat.INSTANCE;
        }
    }
}
