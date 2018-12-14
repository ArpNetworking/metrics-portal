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

public class Job {
    private final ReportSpec spec;
    private final ReportSink sink;
    private final Schedule schedule;

    public Job(ReportSpec spec, ReportSink sink, Schedule schedule) {
        this.spec = spec;
        this.sink = sink;
        this.schedule = schedule;
    }

    public ReportSpec getSpec() {
        return spec;
    }

    public ReportSink getSink() {
        return sink;
    }

    public Schedule getSchedule() {
        return schedule;
    }

}

