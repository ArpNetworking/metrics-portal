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
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.reports.RecipientType;
import com.arpnetworking.utility.test.ResourceHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import models.internal.impl.DefaultRecipient;
import models.internal.impl.DefaultReport;
import models.internal.reports.ReportFormat;
import models.view.impl.HtmlReportFormat;
import models.view.reports.Recipient;
import models.view.reports.Report;
import models.view.scheduling.Schedule;
import net.sf.oval.exception.ConstraintsViolatedException;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Tests for [de]serializing Report-relevant types.

 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ReportSerializationTest {

    @Test
    public void testDeserializeOfSerialize() throws IOException {
        final models.internal.reports.Report originalInternal = TestBeanFactory.createReportBuilder().build();
        final Report originalView = Report.fromInternal(originalInternal);
        final String json = MAPPER.writeValueAsString(originalView);
        final Report deserializedView = MAPPER.readValue(json, Report.class);
        final models.internal.reports.Report deserializedInternal = deserializedView.toInternal();

        assertEquals(originalInternal, deserializedInternal);
    }

    @Test
    public void testValidReport() throws IOException {
        final models.internal.reports.Report expected = new DefaultReport.Builder()
                .setId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .setName("My Report Name")
                .setTimeout(Duration.parse("PT1M"))
                .setReportSource(new models.internal.impl.WebPageReportSource.Builder()
                        .setId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                        .setUri(URI.create("https://example.com"))
                        .setTitle("My Report Title")
                        .setIgnoreCertificateErrors(false)
                        .build())
                .setSchedule(new com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule.Builder()
                        .setRunAtAndAfter(Instant.parse("2019-01-01T00:00:00Z"))
                        .build())
                .setRecipients(ImmutableSetMultimap.<ReportFormat, models.internal.reports.Recipient>builder()
                        .put(
                                new models.internal.impl.HtmlReportFormat.Builder().build(),
                                new models.internal.impl.DefaultRecipient.Builder()
                                        .setType(RecipientType.EMAIL)
                                        .setId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                                        .setAddress("nobody@example.com")
                                .build()
                        )
                        .build())
                .build();
        final Report actual = loadResourceAs("testValidReport", Report.class);
        assertEquals(expected, actual.toInternal());
    }

    @Test
    public void testValidPeriodicSchedule() throws IOException {
        final com.arpnetworking.metrics.portal.scheduling.Schedule expected =
                new com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule.Builder()
                        .setRunAtAndAfter(Instant.parse("2019-01-01T00:00:00Z"))
                        .setRunUntil(Instant.parse("2020-01-01T00:00:00Z"))
                        .setPeriod(ChronoUnit.DAYS)
                        .setZone(ZoneId.of("America/Los_Angeles"))
                        .setOffset(Duration.parse("PT4H"))
                        .build();

        final Schedule actual = loadResourceAs("testValidPeriodicSchedule", Schedule.class);
        assertEquals(expected, actual.toInternal());
    }

    @Test
    public void testValidRecipient() throws IOException {
        final models.internal.reports.Recipient expected = new DefaultRecipient.Builder()
                .setType(RecipientType.EMAIL)
                .setId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .setAddress("nobody@example.com")
                .build();

        final Recipient actual = loadResourceAs("testValidRecipient", Recipient.class);
        assertEquals(expected, actual.toInternal());
        assertEquals(new HtmlReportFormat(), actual.getFormat());
    }

    @Test(expected = com.fasterxml.jackson.databind.exc.InvalidFormatException.class)
    public void testInvalidRecipientNoSuchType() throws IOException {
        loadResourceAs("testInvalidRecipientNoSuchType", Recipient.class);
    }

    @Test(expected = ConstraintsViolatedException.class)
    public void testInvalidNoRecipients() throws IOException {
        final Report report = loadResourceAs("testValidReport", Report.class);
        report.setRecipients(ImmutableList.of());
        report.toInternal();
    }

    private <T> T loadResourceAs(final String resourceSuffix, final Class<T> clazz) throws IOException {
        return ResourceHelper.loadResourceAs(getClass(), resourceSuffix, clazz);
    }

    private static final ObjectMapper MAPPER = ObjectMapperFactory.getInstance();
}
