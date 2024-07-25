/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.rollups;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.Serializable;

/**
 * Message class used to cause the MetricsDiscovery actor to respond with a metric name that
 * is suitable to be rolled up.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
@SuppressFBWarnings(value = "SING_SINGLETON_IMPLEMENTS_SERIALIZABLE", justification = "Optimization. Pekko requires serializable messages.")
public final class MetricFetch implements Serializable {

    public static MetricFetch getInstance() {
        return THE_INSTANCE;
    }

    private MetricFetch() {
    }

    private static final MetricFetch THE_INSTANCE = new MetricFetch();
    private static final long serialVersionUID = 1001707331521967963L;
}
