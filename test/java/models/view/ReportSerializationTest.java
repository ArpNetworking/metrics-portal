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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.reports.RecipientType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
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
import java.net.URL;
import java.time.Duration;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for [de]serializing Report-relevant types.

 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class ReportSerializationTest {
    @Test
    public void testValidReport() throws IOException, URISyntaxException {
        final Report report = OBJECT_MAPPER.readValue(loadResource("testValidReport"), Report.class);
        assertEquals(new URI("https://example.com"), ((ChromeScreenshotReportSource) report.getSource()).getUri());
        assertTrue(report.getSchedule() instanceof OneOffSchedule);
        assertEquals(1, report.getRecipients().size());
        assertEquals("nobody@example.com", report.getRecipients().get(0).getAddress());
    }

    @Test
    public void testValidPeriodicSchedule() throws IOException {
        final PeriodicSchedule schedule = (PeriodicSchedule) OBJECT_MAPPER.readValue(
                loadResource("testValidPeriodicSchedule"),
                Schedule.class
        );
        assertEquals(Period.DAILY, schedule.getPeriod());
        assertEquals(Duration.parse("PT4H"), schedule.getOffset());
        assertEquals(ZoneId.of("America/Los_Angeles"), schedule.getZone());
    }

    @Test
    public void testValidRecipient() throws IOException {
        final Recipient recipient = OBJECT_MAPPER.readValue(loadResource("testValidRecipient"), Recipient.class);
        assertEquals("nobody@example.com", recipient.getAddress());
        assertEquals(RecipientType.EMAIL, recipient.getType());
        assertEquals(new HtmlReportFormat(), recipient.getFormat());
    }

    @Test(expected = com.fasterxml.jackson.databind.exc.InvalidFormatException.class)
    public void testInvalidRecipientNoSuchType() throws IOException {
        OBJECT_MAPPER.readValue(loadResource("testInvalidRecipientNoSuchType"), Recipient.class);
    }

    private String loadResource(final String suffix) {
        final String resourcePath = "models/view/"
                + CLASS_NAME
                + "."
                + suffix
                + ".json";
        final URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IllegalArgumentException(String.format("Resource not found: %s", resourcePath));
        }
        try {
            return Resources.toString(resourceUrl, Charsets.UTF_8);
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final String CLASS_NAME = ReportSerializationTest.class.getSimpleName();
}
