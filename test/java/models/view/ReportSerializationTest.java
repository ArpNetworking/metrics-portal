package models.view;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import models.internal.scheduling.Period;
import models.view.impl.ChromeScreenshotReportSource;
import models.view.impl.OneOffSchedule;
import models.view.impl.PeriodicSchedule;
import models.view.reports.Report;
import models.view.scheduling.Schedule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.ZoneId;

import static org.junit.Assert.*;

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
        final PeriodicSchedule schedule = (PeriodicSchedule) OBJECT_MAPPER.readValue(loadResource("testValidPeriodicSchedule"), Schedule.class);
        assertEquals(Period.DAILY, schedule.getPeriod());
        assertEquals(Duration.parse("PT4H"), schedule.getOffset());
        assertEquals(ZoneId.of("America/Los_Angeles"), schedule.getZone());
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
