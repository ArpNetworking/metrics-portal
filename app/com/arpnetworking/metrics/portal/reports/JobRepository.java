/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.reports;

import com.arpnetworking.metrics.portal.scheduling.Job;

import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Abstraction over a database/registry/etc. of {@link Job}s, each assigned a unique id.
 *
 * @author Spencer Pearson
 */
public interface JobRepository extends com.arpnetworking.metrics.portal.scheduling.JobRepository {
    /**
     * Retrieve a job from the repository.
     *
     * @param id The id that was assigned to the job when it was added to the repository.
     * @return The {@link Job} with the given id, or null if no job has that id.
     */
    @Nullable Job get(String id);

    /**
     * Lists every job in the repository.
     *
     * @return A stream of every job id in no particular order.
     */
    Stream<String> listSpecs();

    /**
     * Adds a new job to the repository.
     * @param job duh
     * @return A unique identifier assigned to that job.
     */
    String add(Job job);

    /**
     * Opens the repository. Before open(), no interactions are legal.
     */
    void open();

    /**
     * Closes the repository. Any interactions after close() (until the next open()) are illegal.
     */
    void close();
}
