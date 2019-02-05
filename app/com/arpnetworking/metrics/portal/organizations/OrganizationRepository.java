/*
 * Copyright 2018 Smartsheet.com
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
package com.arpnetworking.metrics.portal.organizations;

import models.internal.Organization;
import models.internal.OrganizationQuery;
import models.internal.QueryResult;
import play.mvc.Http;

import java.util.UUID;

/**
 * Provides organizations to use for multi-tenancy.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 * @author Ville Koskela (vkoskela at dropbox dot com)
 */
public interface OrganizationRepository {

    /**
     * Open this {@link OrganizationRepository}.
     */
    void open();

    /**
     * Close this {@link OrganizationRepository}.
     */
    void close();

    /**
     * Get the organization in the current request.
     *
     * @param request request that serves as the context
     * @return the organization to use for the request
     */
    Organization get(Http.Request request);

    /**
     * Get the organization by its identifier.
     *
     * @param id the organization identifier
     * @return the organization to use for the request
     */
    Organization get(UUID id);

    /**
     * Create a query against the organizations repository.
     *
     * @return Instance of {@link OrganizationQuery}.
     */
    OrganizationQuery createQuery();

    /**
     * Query the organizations repository.
     *
     * @param query Instance of {@link OrganizationQuery}.
     * @return Instance of {@link QueryResult} for {@link Organization}.
     */
    QueryResult<Organization> query(OrganizationQuery query);

}
