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

package models.internal.reports;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import java.util.concurrent.CompletionStage;

/**
 * Mechanism for sending reports.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface Sender {
    /**
     * TODO(spencerpearson).
     * @param recipients TODO(spencerpearson).
     * @param formatToRendered TODO(spencerpearson).
     * @return TODO(spencerpearson).
     */
    CompletionStage<Void> send(
            ImmutableMultimap<Recipient, ReportFormat> recipients,
            ImmutableMap<ReportFormat, RenderedReport> formatToRendered
    );
}
