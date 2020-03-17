/*
 * Copyright 2020 Dropbox Inc.
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
package com.arpnetworking.rollups.config;

import com.arpnetworking.rollups.RollupPeriod;

/**
 * RollupConfig determines if rollups should be used or generated for the given metric.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface RollupConfig {

    /**
     * Determine if rollup generation is enabled for this particular metric + period combination.
     *
     * @param metricName - the name of the metric
     * @return true if the rollup is enabled
     */
    default boolean isGenerationEnabled(String metricName) {
        return false;
    }

    /**
     * Determine if rollup usage is enabled for this particular metric + period combination.
     *
     * @param metricName - the name of the metric
     * @param period - the rollup period
     * @return true if the rollup usage is enabled
     */
    default boolean isQueryEnabled(String metricName, RollupPeriod period) {
        // Allow queries for all metrics that have rollups enabled
        return isGenerationEnabled(metricName);
    }
}
