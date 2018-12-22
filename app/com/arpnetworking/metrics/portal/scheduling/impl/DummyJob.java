package com.arpnetworking.metrics.portal.scheduling.impl;

import com.arpnetworking.metrics.portal.scheduling.Job;
import com.arpnetworking.metrics.portal.scheduling.Schedule;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DummyJob implements Job {
    private final Throwable _err;
    public DummyJob(final @Nullable Throwable err) {
        _err = err;
    }
    @Override
    public Schedule getSchedule() {
        return OneOffSchedule.INSTANCE;
    }

    @Override
    public CompletionStage<Void> start() {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        if (_err == null) {
            result.complete(null);
        } else {
            result.completeExceptionally(_err);
        }
        return result;
    }
}
