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
package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Report;
import com.arpnetworking.metrics.portal.reports.ReportSink;

import java.util.concurrent.CompletionStage;

/**
 * A {@link ReportSink} that does absolutely nothing with the report passed into it.
 *
 * @author Spencer Pearson
 */
public class DummyReportSink implements ReportSink {

    /**
     * The only instance of DummyReportSink. (They all behave the same: there's no point having more than one.)
     */
    public static final DummyReportSink INSTANCE = new DummyReportSink();

    @Override
    public CompletionStage<Void> send(final CompletionStage<Report> fr) {
        return fr.thenApply(r -> null);
    }
}
