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
package com.arpnetworking.metrics.portal.reports;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Describes how to send a report to some destination.
 *
 * @author Spencer Pearson
 */
public interface ReportSink {

    /**
     * Sends a report.
     *
     * @param fr The (future) report to send.
     * @return A CompletionStage that completes when the report is sent.
     */
    CompletionStage<Void> send(CompletionStage<Report> fr);

    /**
     * Sends a report (by wrapping it in a CompletableFuture and delegating to <code>send(CompletionStage<Report>)</code>.
     * @param r The report to send.
     * @return A CompletionStage that completes when the report is sent.
     */
    default CompletionStage<Void> send(final Report r) {
        return send(CompletableFuture.completedFuture(r));
    };

}
