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

package com.arpnetworking.metrics.portal.reports.impl.testing;

import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.google.common.collect.ImmutableSetMultimap;
import models.internal.TimeRange;
import models.internal.impl.DefaultRenderedReport;
import models.internal.impl.DefaultReport;
import models.internal.impl.WebPageReportSource;
import models.internal.reports.Recipient;
import models.internal.reports.ReportFormat;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Non-generic implementation of {@link RenderedReport.Builder}, for ease of mocking.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class MockRenderedReportBuilder
        implements RenderedReport.Builder<MockRenderedReportBuilder, RenderedReport> {

    private TimeRange _timeRange;
    private byte[] _bytes;
    private Instant _generatedAt;
    private ReportFormat _format;

    @Override
    public MockRenderedReportBuilder setBytes(final byte[] bytes) {
        _bytes = bytes;
        return this;
    }

    @Override
    public MockRenderedReportBuilder setTimeRange(final TimeRange timeRange) {
        _timeRange = timeRange;
        return this;
    }

    @Override
    public MockRenderedReportBuilder setGeneratedAt(final Instant generatedAt) {
        _generatedAt = generatedAt;
        return this;
    }

    @Override
    public MockRenderedReportBuilder setFormat(final ReportFormat format) {
        _format = format;
        return this;
    }

    @Override
    public RenderedReport build() {
        return new DefaultRenderedReport.Builder()
                .setTimeRange(_timeRange)
                .setGeneratedAt(_generatedAt)
                .setFormat(_format)
                .setBytes(_bytes)
                .setReport(new DefaultReport.Builder()
                        .setReportSource(
                                new WebPageReportSource.Builder()
                                        .setId(UUID.randomUUID())
                                        .setUri(URI.create("http://example.com/"))
                                        .setTitle("Example Report Source")
                                .build())
                        .setId(UUID.randomUUID())
                        .setName("Example Report")
                        .setRecipients(new ImmutableSetMultimap.Builder<ReportFormat, Recipient>().build())
                        .build())
                .build();
    }
}
