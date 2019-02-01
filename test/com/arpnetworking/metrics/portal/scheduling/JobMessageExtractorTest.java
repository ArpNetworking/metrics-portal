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
package com.arpnetworking.metrics.portal.scheduling;

import com.arpnetworking.metrics.portal.scheduling.impl.MapJobRepository;
import models.internal.Organization;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link JobMessageExtractor}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobMessageExtractorTest {

    private static class MockableIntJobRepository extends MapJobRepository<Integer> {}

    private static final Organization organization = Organization.DEFAULT;

    @Test
    public void testEntityId() {
        final JobMessageExtractor extractor = new JobMessageExtractor();
        final JobRef<Integer> ref = new JobRef.Builder<Integer>()
                .setId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .setOrganization(organization)
                .setRepositoryType(MockableIntJobRepository.class)
                .build();
        assertEquals(
                String.format(
                        "repoType-%s--orgId-%s--jobId-%s",
                        "com.arpnetworking.metrics.portal.scheduling.JobMessageExtractorTest.MockableIntJobRepository",
                        organization.getId(),
                        "11111111-1111-1111-1111-111111111111"),
                extractor.entityId(new JobExecutorActor.Reload.Builder<Integer>().setJobRef(ref).build()));
    }

}
