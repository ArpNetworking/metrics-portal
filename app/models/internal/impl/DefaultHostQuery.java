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
package models.internal.impl;

import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.google.common.base.MoreObjects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.HostQuery;
import models.internal.MetricsSoftwareState;
import models.internal.Organization;

import java.io.Serial;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Default internal model implementation for a host query.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Loggable
public final class DefaultHostQuery implements HostQuery {

    /**
     * Public constructor.
     *
     * @param repository the {@link HostRepository} to query
     * @param organization the {@link Organization} to search in
     */
    public DefaultHostQuery(final HostRepository repository, final Organization organization) {
        _repository = repository;
        _organization = organization;
    }

    @Override
    public HostQuery partialHostname(final Optional<String> partialHostname) {
        _partialHostname = partialHostname.orElse(null);
        return this;
    }

    @Override
    public HostQuery metricsSoftwareState(final Optional<MetricsSoftwareState> metricsSoftwareState) {
        _metricsSoftwareState = metricsSoftwareState.orElse(null);
        return this;
    }

    @Override
    public HostQuery cluster(final Optional<String> cluster) {
        _cluster = cluster.orElse(null);
        return this;
    }

    @Override
    public HostQuery limit(final int limit) {
        _limit = limit;
        return this;
    }

    @Override
    public HostQuery offset(final Optional<Integer> offset) {
        _offset = offset.orElse(null);
        return this;
    }

    @Override
    public HostQuery sortBy(final Optional<Field> sortBy) {
        _sortBy = sortBy.orElse(null);
        return this;
    }

    @Override
    public Optional<String> getPartialHostname() {
        return Optional.ofNullable(_partialHostname);
    }

    @Override
    public Organization getOrganization() {
        return _organization;
    }

    @Override
    public Optional<MetricsSoftwareState> getMetricsSoftwareState() {
        return Optional.ofNullable(_metricsSoftwareState);
    }

    @Override
    public Optional<String> getCluster() {
        return Optional.ofNullable(_cluster);
    }

    @Override
    public int getLimit() {
        return _limit;
    }

    @Override
    public Optional<Integer> getOffset() {
        return Optional.ofNullable(_offset);
    }

    @Override
    public Optional<Field> getSortBy() {
        return Optional.ofNullable(_sortBy);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Repository", _repository)
                .add("PartialHostname", _partialHostname)
                .add("MetricsSoftwareState", _metricsSoftwareState)
                .add("Limit", _limit)
                .add("Offset", _offset)
                .add("SortBy", _sortBy)
                .toString();
    }


    @SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
    private final transient HostRepository _repository;
    private final Organization _organization;
    @Nullable
    private String _partialHostname = null;
    @Nullable
    private MetricsSoftwareState _metricsSoftwareState = null;
    @Nullable
    private String _cluster = null;
    private int _limit = DEFAULT_LIMIT;
    @Nullable
    private Integer _offset = null;
    @Nullable
    private Field _sortBy = null;

    private static final int DEFAULT_LIMIT = 1000;
    @Serial
    private static final long serialVersionUID = 1L;
}
