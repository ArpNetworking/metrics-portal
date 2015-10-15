/**
 * Copyright 2015 Groupon.com
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
 * View model of <code>Expression</code>. Play view models are mutable.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
@Loggable
public final class Expression {

    public void setId(final String value) {
        _id = value;
    }

    public String getId() {
        return _id;
    }

    public void setCluster(final String value) {
        _cluster = value;
    }

    public String getCluster() {
        return _cluster;
    }

    public void setService(final String value) {
        _service = value;
    }

    public String getService() {
        return _service;
    }

    public void setMetric(final String value) {
        _metric = value;
    }

    public String getMetric() {
        return _metric;
    }

    public void setScript(final String value) {
        _script = value;
    }

    public String getScript() {
        return _script;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Id", _id)
                .add("Cluster", _cluster)
                .add("Service", _service)
                .add("Metric", _metric)
                .add("Script", _script)
                .toString();
    }

    private String _id;
    private String _cluster;
    private String _service;
    private String _metric;
    private String _script;
}
