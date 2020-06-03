/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.alerts;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.scheduling.AlertExecutionContext;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import models.internal.MetricsQueryFormat;
import models.internal.Organization;
import models.internal.alerts.Alert;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultMetricsQuery;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Unit tests for {@link AlertExecutionContext}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class AlertExecutionContextTest {
    private AlertExecutionContext _context;
    private Alert _alert;
    private Schedule _schedule;

    @Before
    public void setup() {
        final Organization organization = TestBeanFactory.createOrganization();
        final UUID id = UUID.randomUUID();
        _schedule = NeverSchedule.getInstance();
        _alert = new DefaultAlert.Builder()
                .setId(id)
                .setOrganization(organization)
                .setEnabled(true)
                .setName("TestAlert")
                .setDescription("Used in a test.")
                .setQuery(
                        new DefaultMetricsQuery.Builder()
                                .setQuery("Query TBD")
                                .setFormat(MetricsQueryFormat.KAIROS_DB)
                                .build()
                )
                .build();
        _context = new AlertExecutionContext(_schedule);
    }

    @Test
    public void testReturnsTheConfiguredSchedule() {
        assertThat(_context.getSchedule(_alert), equalTo(_schedule));
    }
}
