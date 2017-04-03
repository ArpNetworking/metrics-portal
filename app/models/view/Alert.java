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
import com.google.common.collect.ImmutableMap;

/**
 * View model of <code>Alert</code>. Play view models are mutable.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class Alert {

    public void setId(final String value) {
        _id = value;
    }

    public String getId() {
        return _id;
    }

    public void setContext(final String value) {
        _context = value;
    }

    public String getContext() {
        return _context;
    }

    public void setName(final String value) {
        _name = value;
    }

    public String getName() {
        return _name;
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

    public void setStatistic(final String value) {
        _statistic = value;
    }

    public String getStatistic() {
        return _statistic;
    }

    public void setPeriod(final String value) {
        _period = value;
    }

    public String getPeriod() {
        return _period;
    }

    public void setOperator(final String value) {
        _operator = value;
    }

    public String getOperator() {
        return _operator;
    }

    public void setValue(final Quantity value) {
        _value = value;
    }

    public Quantity getValue() {
        return _value;
    }

    public void setExtensions(final ImmutableMap<String, Object> extensions) {
        _extensions = extensions;
    }

    public ImmutableMap<String, Object> getExtensions() {
        return _extensions;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Id", _id)
                .add("Context", _context)
                .add("Name", _name)
                .add("Cluster", _cluster)
                .add("Service", _service)
                .add("Metric", _metric)
                .add("Statistic", _statistic)
                .add("Period", _period)
                .add("Operator", _operator)
                .add("Value", _value)
                .add("Extensions", _extensions)
                .toString();
    }

    private String _id;
    private String _context;
    private String _name;
    private String _cluster;
    private String _service;
    private String _metric;
    private String _statistic;
    private String _period;
    private String _operator;
    private Quantity _value;
    private ImmutableMap<String, Object> _extensions;
}
