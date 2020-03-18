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
package com.arpnetworking.kairos.config;

import com.arpnetworking.kairos.client.models.SamplingUnit;

import java.util.Set;

/**
 * RollupConfig determines if rollups should be used for the given metric.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface MetricsQueryConfig {
    /**
     * Get a set of enabled rollup units for a particular metric.
     *
     * If a {@link SamplingUnit} is contained within this set, then it should be eligible for
     * promotion to a rollup query for the given metric.
     *
     * @param metricName the name of the metric
     * @return A set of sampling units for which rollups are enabled
     */
    Set<SamplingUnit> getQueryEnabledRollups(String metricName);
}
