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

import com.google.common.collect.ImmutableList;
import models.internal.Organization;
import models.internal.impl.DefaultOrganization;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


/**
 * Unit tests for {@link TwoWayJobRefSerializer}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class TwoWayJobRefSerializerTest {

    private final static UUID JOB_ID = UUID.fromString("72a0e49c-d0e3-445a-b7d2-bd1012125a30");
    private final static Organization ORGANIZATION = new DefaultOrganization.Builder().setId(UUID.fromString(
            "885d4ed6-093b-4570-b766-fd30040af67e")).build();
    private JobRefSerializer _refSerializer;

    @Before
    public void setUp() {
        // arbitrary choice of repositories for this test.
        _refSerializer = new TwoWayJobRefSerializer(
                ImmutableList.of(MockJobRepository.class),
                ImmutableList.of(MockJobExecutionRepository.class)
        );
    }

    @Test
    public void testRoundTripSerializeDeserialize() {
        final JobRef<?> ref =
                new JobRef.Builder<Integer>()
                        .setRepositoryType(MockJobRepository.class)
                        .setExecutionRepositoryType(MockJobExecutionRepository.class)
                        .setId(JOB_ID)
                        .setOrganization(ORGANIZATION)
                        .build();

        final String entityID = _refSerializer.jobRefToEntityID(ref);
        assertThat(entityID, is("MockJobRepository_MockJobExecutionRepository_" + ORGANIZATION.getId() + "_" + JOB_ID));

        final Optional<JobRef<?>> outRef = _refSerializer.entityIDtoJobRef(entityID);
        assertThat(outRef, is(Optional.of(ref)));
    }

    @Test
    public void testFailureWhenRepositoryIsNotWhitelisted() {
        final JobRef<?> ref =
                new JobRef.Builder<Integer>()
                        .setRepositoryType(UnknownJobRepository.class)
                        .setExecutionRepositoryType(MockJobExecutionRepository.class)
                        .setId(JOB_ID)
                        .setOrganization(ORGANIZATION)
                        .build();

        final String entityID = _refSerializer.jobRefToEntityID(ref);
        assertThat(entityID,
                is("UnknownJobRepository_MockJobExecutionRepository_" + ORGANIZATION.getId() + "_" + JOB_ID));

        final Optional<JobRef<?>> outRef = _refSerializer.entityIDtoJobRef(entityID);
        assertThat(outRef, is(Optional.empty()));
    }

    @Test
    public void testFailureWhenExecutionRepositoryIsNotWhitelisted() {
        final JobRef<?> ref =
                new JobRef.Builder<Integer>()
                        .setRepositoryType(MockJobRepository.class)
                        .setExecutionRepositoryType(UnknownJobExecutionRepository.class)
                        .setId(JOB_ID)
                        .setOrganization(ORGANIZATION)
                        .build();

        final String entityID = _refSerializer.jobRefToEntityID(ref);
        assertThat(entityID,
                is("MockJobRepository_UnknownJobExecutionRepository_" + ORGANIZATION.getId() + "_" + JOB_ID));

        final Optional<JobRef<?>> outRef = _refSerializer.entityIDtoJobRef(entityID);
        assertThat(outRef, is(Optional.empty()));
    }

    private interface MockJobRepository extends JobRepository<Integer> {}

    private interface MockJobExecutionRepository extends JobExecutionRepository<Integer> {}

    private interface UnknownJobRepository extends JobRepository<Integer> {}

    private interface UnknownJobExecutionRepository extends JobExecutionRepository<Integer> {}
}
