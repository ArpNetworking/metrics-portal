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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import models.internal.Alert;

/**
 * Abstract repository that overrides the write functions for the <code>AlertRepository</code>.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public abstract class ReadOnlyAbstractAlertRepository implements AlertRepository {

    /**
     * {@inheritDoc}
     */
    @Override
    public void addOrUpdateAlert(final Alert alert) {
        throw new UnsupportedOperationException("This is a read only repository.");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyAbstractAlertRepository.class);
}
