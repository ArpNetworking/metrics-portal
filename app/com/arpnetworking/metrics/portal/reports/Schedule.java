package com.arpnetworking.metrics.portal.reports;

import javax.annotation.Nullable;
import java.time.Instant;

public interface Schedule {
    @Nullable Instant nextRun(Instant lastRun);
}
