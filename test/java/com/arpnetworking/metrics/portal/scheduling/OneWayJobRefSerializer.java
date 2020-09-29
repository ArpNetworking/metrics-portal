/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.scheduling;

import java.util.Optional;

/**
 * A {@link JobRefSerializer} that just extracts the Job ID from the ref, making no attempt
 * to preserve enough information for later reconstruction (hence "one-way").
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class OneWayJobRefSerializer implements JobRefSerializer {
    @Override
    public String jobRefToEntityID(final JobRef<?> ref) {
        return ref.getJobId().toString();
    }

    @Override
    public Optional<JobRef<?>> entityIDtoJobRef(final String id) {
        return Optional.empty();
    }
}
