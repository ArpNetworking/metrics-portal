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
import com.google.common.collect.Lists;
import models.internal.scheduling.Period;
import models.view.impl.ChromeScreenshotReportSource;
import models.view.impl.HtmlReportFormat;
import models.view.impl.OneOffSchedule;
import models.view.impl.PeriodicSchedule;
import models.view.reports.Recipient;
import models.view.reports.Report;
import models.view.reports.ReportSource;
import models.view.scheduling.Schedule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for [de]serializing Report-relevant types.

 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ReportSerializationTest {
    @Test
    public void testValidReport() throws IOException {
        final Report expected = new Report();
        expected.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        expected.setName("My Report Name");

        final ChromeScreenshotReportSource expectedSource = new ChromeScreenshotReportSource();
        expectedSource.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        expectedSource.setUri(URI.create("https://example.com"));
        expectedSource.setTitle("My Report Title");
        expectedSource.setIgnoreCertificateErrors(false);
        expectedSource.setTriggeringEventName("myTriggeringEventName");
        expected.setSource(expectedSource);

        final OneOffSchedule expectedSchedule = new OneOffSchedule();
        expectedSchedule.setRunAtAndAfter(Instant.parse("2019-01-01T00:00:00Z"));
        expected.setSchedule(expectedSchedule);

        final Recipient expectedRecipient = new Recipient();
        expectedRecipient.setType(RecipientType.EMAIL);
        expectedRecipient.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        expectedRecipient.setAddress("nobody@example.com");
        expectedRecipient.setFormat(new HtmlReportFormat());
        expected.setRecipients(Lists.newArrayList(expectedRecipient));

        final Report actual = loadResourceAs("testValidReport", Report.class);
        assertEquals(expected, actual);
    }

    @Test
    public void testValidPeriodicSchedule() throws IOException {
        final PeriodicSchedule expected = new PeriodicSchedule();
        expected.setRunAtAndAfter(Instant.parse("2019-01-01T00:00:00Z"));
        expected.setRunUntil(Instant.parse("2020-01-01T00:00:00Z"));
        expected.setPeriod(Period.DAILY);
        expected.setZone(ZoneId.of("America/Los_Angeles"));
        expected.setOffset(Duration.parse("PT4H"));

        final Schedule actual = loadResourceAs("testValidPeriodicSchedule", Schedule.class);
        assertEquals(expected, actual);
    }

    @Test
    public void testValidRecipient() throws IOException {
        final Recipient expected = new Recipient();
        expected.setType(RecipientType.EMAIL);
        expected.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        expected.setAddress("nobody@example.com");
        expected.setFormat(new HtmlReportFormat());

        final Recipient actual = loadResourceAs("testValidRecipient", Recipient.class);
        assertEquals(expected, actual);
    }

    @Test(expected = com.fasterxml.jackson.databind.exc.InvalidFormatException.class)
    public void testInvalidRecipientNoSuchType() throws IOException {
        loadResourceAs("testInvalidRecipientNoSuchType", Recipient.class);
    }

    private <T> T loadResourceAs(final String resourceSuffix, final Class<T> clazz) throws IOException {
        return ResourceHelper.loadResourceAs(getClass(), resourceSuffix, clazz);
    }
}
