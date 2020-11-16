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

/**
 * The alignment behavior of a particular {@link models.internal.MetricsQuery}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public enum QueryAlignment {
    /**
     * Period-alignment.
     *
     * Datapoints will be aligned with the start of each period (e.g. start
     * of the day or start of the hour).
     */
    PERIOD,
    /**
     * End-alignment.
     *
     * Datapoints will be aligned with the end of the query interval.
     */
    END
}
