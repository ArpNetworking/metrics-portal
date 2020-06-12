package com.arpnetworking.rollups;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QueueConsistencyTaskCreatorTest {
    @Test
    public void periodStreamForInterval() {
        final List<Instant> actual = QueryConsistencyTaskCreator.periodStreamForInterval(
                Instant.parse("2020-06-11T22:23:21Z"),
                Instant.parse("2020-06-12T01:02:03Z"),
                RollupPeriod.HOURLY).collect(Collectors.toList());

        Assert.assertEquals(Arrays.asList(
                Instant.parse("2020-06-11T22:00:00Z"),
                Instant.parse("2020-06-11T23:00:00Z"),
                Instant.parse("2020-06-12T00:00:00Z"),
                Instant.parse("2020-06-12T01:00:00Z")), actual);
    }
}
