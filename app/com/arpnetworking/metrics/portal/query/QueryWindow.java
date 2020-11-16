/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.query;

import java.time.Duration;

/**
 * QueryWindow encapsulates the information required to determine the minimal
 * evaluation window for a query, given its start time.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface QueryWindow {
    /**
     * The length of the query window. This is the length required
     * to obtain at least one datapoint.
     *
     * @return The length of the query window
     */
    Duration getLookbackPeriod();

    /**
     * The query alignment. Depending on alignment, the query window may need to
     * be shifted.
     *
     * @return The query alignment.
     */
    QueryAlignment getAlignment();
}
