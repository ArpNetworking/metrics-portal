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

package models.view.reports;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import models.view.impl.WebReportSource;

/**
 * View model of {@link models.internal.reports.ReportSource}. Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = WebReportSource.class, name = "WEB"),
})
public interface ReportSource {
    /**
     * Convert to an internal model {@link models.internal.reports.ReportSource}.
     *
     * @return The internal model.
     */
    models.internal.reports.ReportSource toInternal();

    /**
     * Convert from an internal model {@link models.internal.reports.ReportSource}.
     *
     * @param source The internal model.
     * @return The view model.
     * @throws IllegalArgumentException if the internal model cannot be represented in the view.
     */
    static ReportSource fromInternal(final models.internal.reports.ReportSource source) {
        if (source instanceof models.internal.impl.WebReportSource) {
            return WebReportSource.fromInternal((models.internal.impl.WebReportSource) source);
        } else {
            throw new IllegalArgumentException("Cannot convert class " + source.getClass() + " to a view model.");
        }
    }
}

