/*
 * Copyright 2019 Dropbox, Inc.
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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.scheduling.JobCoordinator;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/**
 * Tests for {@link JobCoordinator}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class ReportSerializationTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();

    @Test
    public void testRunsAntiEntropy() {
    }

    private static class MockableIntJobRepository extends MapJobRepository<Integer> {}
}
