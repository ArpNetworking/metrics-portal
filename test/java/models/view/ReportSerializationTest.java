/*
 * Copyright 2019 Dropbox Inc.
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

import com.arpnetworking.metrics.portal.reports.RecipientType;
import com.arpnetworking.utility.ResourceHelper;
import models.internal.scheduling.Period;
import models.view.impl.ChromeScreenshotReportSource;
import models.view.impl.HtmlReportFormat;
import models.view.impl.OneOffSchedule;
import models.view.impl.PeriodicSchedule;
import models.view.reports.Recipient;
import models.view.reports.Report;
import models.view.scheduling.Schedule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for [de]serializing Report-relevant types.

 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ReportSerializationTest {
    @Test
    public void testValidReport() throws IOException, URISyntaxException {
        final Report report = loadResourceAs("testValidReport", Report.class);
        assertEquals(new URI("https://example.com"), ((ChromeScreenshotReportSource) report.getSource()).getUri());
        assertTrue(report.getSchedule() instanceof OneOffSchedule);
        assertEquals(1, report.getRecipients().size());
        assertEquals("nobody@example.com", report.getRecipients().get(0).getAddress());
    }

    @Test
    public void testValidPeriodicSchedule() throws IOException {
        final PeriodicSchedule schedule = (PeriodicSchedule) loadResourceAs("testValidPeriodicSchedule", Schedule.class);
        assertEquals(Period.DAILY, schedule.getPeriod());
        assertEquals(Duration.parse("PT4H"), schedule.getOffset());
        assertEquals(ZoneId.of("America/Los_Angeles"), schedule.getZone());
    }

    @Test
    public void testValidRecipient() throws IOException {
        final Recipient recipient = loadResourceAs("testValidRecipient", Recipient.class);
        assertEquals("nobody@example.com", recipient.getAddress());
        assertEquals(RecipientType.EMAIL, recipient.getType());
        assertEquals(new HtmlReportFormat(), recipient.getFormat());
    }

    @Test(expected = com.fasterxml.jackson.databind.exc.InvalidFormatException.class)
    public void testInvalidRecipientNoSuchType() throws IOException {
        loadResourceAs("testInvalidRecipientNoSuchType", Recipient.class);
    }

    private <T> T loadResourceAs(final String resourceSuffix, final Class<T> clazz) throws IOException {
        return ResourceHelper.loadResourceAs(getClass(), resourceSuffix, clazz);
    }
}
