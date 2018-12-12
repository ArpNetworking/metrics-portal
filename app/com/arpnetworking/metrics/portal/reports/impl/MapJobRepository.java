package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Job;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class MapJobRepository implements JobRepository {

    @Inject
    public MapJobRepository() {}

    private final AtomicLong nonce = new AtomicLong(0);
    private final Map<String, Job> specs = new HashMap<>();
    @Override
    public @Nullable
    Job get(String id) {
        return this.specs.get(id);
    }

    @Override
    public Stream<String> listSpecs() {
        return specs.keySet().stream();
    }

    @Override
    public String add(Job spec) {
        String id = Long.toString(this.nonce.getAndIncrement());
        this.specs.put(id, spec);
        return id;
    }

    @Override
    public void open() {

    }

    @Override
    public void close() {

    }
}
