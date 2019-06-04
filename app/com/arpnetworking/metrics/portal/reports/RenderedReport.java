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

package com.arpnetworking.metrics.portal.reports;

import com.google.common.io.ByteSource;
import models.internal.reports.ReportFormat;

import java.time.Instant;

/**
 * A document containing the results of a particular rendering of a particular {@link models.internal.reports.Report}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface RenderedReport {

    /**
     * The format of the report.
     *
     * @return The format.
     */
    ReportFormat getFormat();

    /**
     * The raw bytes of the document.
     *
     * @return The bytes.
     */
    ByteSource getBytes();

    /**
     * The instant that the report was scheduled for.
     *
     * @return The instant.
     */
    Instant getScheduledFor();

    /**
     * The instant that the report was actually generated. (Ideally, this should not affect the report content.)
     *
     * @return The instant.
     */
    Instant getGeneratedAt();
}
