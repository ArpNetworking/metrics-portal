/*
 * Copyright 2019 Dropbox Inc
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
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.google.common.base.MoreObjects;
import models.internal.OrganizationQuery;

import java.util.Optional;

/**
 * Default internal model implementation for an organization query.
 *
 * @author Ville Koskela (vkoskela at dropbox dot com)
 */
@Loggable
public final class DefaultOrganizationQuery implements OrganizationQuery {

    /**
     * Public constructor.
     *
     * @param repository The {@link OrganizationRepository}
     */
    public DefaultOrganizationQuery(final OrganizationRepository repository) {
        _repository = repository;
    }

    @Override
    public OrganizationQuery limit(final int limit) {
        _limit = limit;
        return this;
    }

    @Override
    public OrganizationQuery offset(final Optional<Integer> offset) {
        _offset = offset;
        return this;
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
                .add("Limit", _limit)
                .add("Offset", _offset)
                .toString();
    }


    private final OrganizationRepository _repository;
    private int _limit = DEFAULT_LIMIT;
    private Optional<Integer> _offset = Optional.empty();

    private static final int DEFAULT_LIMIT = 1000;
}
