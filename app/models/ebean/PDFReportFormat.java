/*
 * Copyright 2018 Dropbox, Inc.
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

package models.ebean;

import models.internal.impl.PdfReportFormat;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("PDF")
public class PDFReportFormat extends ReportFormat {
    @Column(name = "height_inches")
    private Float heightInches;

    @Column(name = "width_inches")
    private Float widthInches;

    public Float getHeightInches() {
        return heightInches;
    }

    public void setHeightInches(final float value) {
        heightInches = value;
    }

    public Float getWidthInches() {
        return widthInches;
    }

    public void setWidthInches(final float value) {
        widthInches = value;
    }

    @Override
    /* package */ models.internal.reports.ReportFormat toInternal() {
        return new PdfReportFormat.Builder()
                .setWidthInches(widthInches)
                .setHeightInches(heightInches)
                .build();
    }
}
