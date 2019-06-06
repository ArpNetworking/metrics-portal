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
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;

import java.time.Instant;

/**
 * A document containing the results of a particular rendering of a particular {@link models.internal.reports.Report}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface RenderedReport {

    /**
     * The report that was rendered into this object.
     *
     * @return The format.
     */
    Report getReport();

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


    /**
     * Interface for Builders that construct {@link RenderedReport}s.
     *
     * @param <B> The concrete type of this builder.
     * @param <R> The type of RenderedReport to build.
     */
    interface Builder<B extends Builder<B, R>, R extends RenderedReport> extends com.arpnetworking.commons.builder.Builder<R> {

        /**
         * Set the report bytes. Required. Cannot be null.
         *
         * @param bytes The report bytes.
         * @return This instance of {@code Builder}.
         */
        B setBytes(byte[] bytes);

        /**
         * Set the report bytes. Required. Cannot be null.
         *
         * @param scheduledFor The report scheduledFor.
         * @return This instance of {@code Builder}.
         */
        B setScheduledFor(Instant scheduledFor);

        /**
         * Set the report generatedAt. Required. Cannot be null.
         *
         * @param generatedAt The report generatedAt.
         * @return This instance of {@code Builder}.
         */
        B setGeneratedAt(Instant generatedAt);

        /**
         * Set the report format. Required. Cannot be null.
         *
         * @param format The report format.
         * @return This instance of {@code Builder}.
         */
        B setFormat(ReportFormat format);
    }
}
