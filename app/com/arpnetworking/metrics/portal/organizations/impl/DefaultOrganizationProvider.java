/**
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
package com.arpnetworking.metrics.portal.organizations.impl;

import com.arpnetworking.metrics.portal.organizations.OrganizationProvider;
import models.internal.Organization;
import play.mvc.Http;

/**
 * Always returns the default organization.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class DefaultOrganizationProvider implements OrganizationProvider {
    @Override
    public Organization getOrganization(final Http.Request request) {
        return Organization.DEFAULT;
    }
}
