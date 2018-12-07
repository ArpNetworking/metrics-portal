package com.arpnetworking.metrics.portal.reports;

import javax.annotation.Nullable;
import java.util.stream.Stream;

public interface JobRepository {
    @Nullable Job get(String id);
    Stream<String> listSpecs();
    String add(Job spec);
    void open();
    void close();
}
