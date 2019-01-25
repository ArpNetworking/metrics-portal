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

package models.ebean;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Ebean data model for an HTML report format.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Entity
@DiscriminatorValue("HTML")
public class HtmlReportFormat extends ReportFormat {
    @Override
    /* package */ models.internal.reports.ReportFormat toInternal() {
        return new models.internal.impl.HtmlReportFormat.Builder()
                .build();
    }
}
