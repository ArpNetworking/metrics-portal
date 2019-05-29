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

package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.arpnetworking.metrics.portal.reports.Sender;
import com.google.common.collect.ImmutableMap;
import models.internal.reports.Recipient;
import models.internal.reports.ReportFormat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Sends reports over email.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class EmailSender implements Sender {
    @Override
    public CompletionStage<Void> send(
            final Recipient recipient,
            final ImmutableMap<ReportFormat, RenderedReport> formatsToSend
    ) {
        return CompletableFuture.completedFuture(null); // TODO(spencerpearson)
    }

    EmailSender() {}

}
