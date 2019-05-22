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

import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 *
 * TODO(spencerpearson).
 *
 * @param <S> TODO(spencerpearson).
 * @param <F> TODO(spencerpearson).
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface Renderer<S extends ReportSource, F extends ReportFormat> {
    /**
     * TODO(spencerpearson).
     * @param source TODO(spencerpearson).
     * @param format TODO(spencerpearson).
     * @param scheduled TODO(spencerpearson).
     * @return TODO(spencerpearson).
     */
    CompletionStage<RenderedReport> render(S source, F format, Instant scheduled);
}
