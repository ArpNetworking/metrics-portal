/*
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
package models.internal.impl;

import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.google.common.base.MoreObjects;
import models.internal.AlertQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;

import java.util.Optional;

/**
 * Default internal model implementation for an alert query.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Loggable
public final class DefaultAlertQuery implements AlertQuery {

    /**
     * Public constructor.
     *
     * @param repository the {@link AlertRepository} to query
     * @param organization the {@link Organization} to search in
     */
    public DefaultAlertQuery(final AlertRepository repository, final Organization organization) {
        _repository = repository;
        _organization = organization;
    }

    @Override
    public AlertQuery contains(final String contains) {
        _contains = Optional.of(contains);
        return this;
    }

    @Override
    public AlertQuery enabled(final boolean enabled) {
        _enabled = Optional.of(enabled);
        return this;
    }

    @Override
    public AlertQuery limit(final int limit) {
        _limit = limit;
        return this;
    }

    @Override
    public AlertQuery offset(final int offset) {
        _offset = Optional.of(offset);
        return this;
    }

    @Override
    public QueryResult<Alert> execute() {
        return _repository.queryAlerts(this);
    }

    @Override
    public Organization getOrganization() {
        return _organization;
    }

    @Override
    public Optional<String> getContains() {
        return _contains;
    }

    @Override
    public Optional<Boolean> getEnabled() {
        return _enabled;
    }

    @Override
    public int getLimit() {
        return _limit;
    }

    @Override
    public Optional<Integer> getOffset() {
        return _offset;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Repository", _repository)
                .add("Contains", _contains)
                .add("Limit", _limit)
                .add("Offset", _offset)
                .toString();
    }

    private final AlertRepository _repository;
    private final Organization _organization;
    private Optional<String> _contains = Optional.empty();
    private Optional<Boolean> _enabled = Optional.empty();
    private int _limit = DEFAULT_LIMIT;
    private Optional<Integer> _offset = Optional.empty();

    private static final int DEFAULT_LIMIT = 1000;
}
