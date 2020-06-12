package com.arpnetworking.rollups;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QueueConsistencyTaskCreatorTest {
    @Test
    public void periodStreamForInterval() {
        final List<Instant> actual = QueryConsistencyTaskCreator.periodStreamForInterval(
                Instant.parse("2020-06-11 22:23:21"),
                Instant.parse("2020-06-12 01:02:03"),
                RollupPeriod.HOURLY).collect(Collectors.toList());

        Assert.assertEquals(new ArrayList<Instant>(), actual);
    }
}
