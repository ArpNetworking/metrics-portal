/*
 * Copyright 2014 Groupon.com
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
package models.view;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;

/**
 * View model of <code>Host</code>. Play view models are mutable.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Loggable
public final class Host {

    public void setHostname(final String value) {
        _hostname = value;
    }

    public String getHostname() {
        return _hostname;
    }

    public void setMetricsSoftwareState(final String value) {
        _metricsSoftwareState = value;
    }

    public String getMetricsSoftwareState() {
        return _metricsSoftwareState;
    }

    public void setCluster(final String value) {
        _cluster = value;
    }

    public String getCluster() {
        return _cluster;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Hostname", _hostname)
                .add("MetricsSoftwareState", _metricsSoftwareState)
                .add("Cluster", _cluster)
                .toString();
    }

    private String _hostname;
    private String _metricsSoftwareState;
    private String _cluster;
}
