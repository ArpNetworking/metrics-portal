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

import com.arpnetworking.logback.annotations.Loggable;
import models.internal.reports.Report;


/**
 * Default implementation of {@code Report.Result}.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 */
@Loggable
public final class DefaultReportResult implements Report.Result {
    /**
     * Default Constructor.
     */
    public DefaultReportResult() {}
}
