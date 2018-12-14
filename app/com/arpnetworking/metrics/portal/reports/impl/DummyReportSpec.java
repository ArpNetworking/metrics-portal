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
import com.arpnetworking.metrics.portal.reports.ReportSpec;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DummyReportSpec implements ReportSpec {

    public static final DummyReportSpec INSTANCE = new DummyReportSpec();

    @Override
    public CompletionStage<Report> render() {
        return CompletableFuture.completedFuture(new Report(null, null));
    }
}
