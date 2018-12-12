package com.arpnetworking.metrics.portal.reports;

import java.time.Instant;

public interface Schedule {
    Instant nextRun(Instant lastRun);
}
