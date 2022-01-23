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

import com.arpnetworking.notcommons.serialization.DeserializationException;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import models.internal.Organization;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


/**
 * Unit tests for {@link DefaultJobRefSerializer}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DefaultJobRefSerializerTest {

    private static final UUID JOB_ID = UUID.fromString("72a0e49c-d0e3-445a-b7d2-bd1012125a30");
    private static final Organization ORGANIZATION =
            TestBeanFactory.organizationFrom(UUID.fromString("885d4ed6-093b-4570-b766-fd30040af67e"));
    private DefaultJobRefSerializer _refSerializer;

    @Before
    public void setUp() {
        // arbitrary choice of repositories for this test.
        _refSerializer = new DefaultJobRefSerializer();
    }

    @Test
    public void testRoundTripSerializeDeserialize() throws DeserializationException {
        final JobRef<?> ref =
                new JobRef.Builder<Integer>()
                        .setRepositoryType(MockJobRepository.class)
                        .setExecutionRepositoryType(MockJobExecutionRepository.class)
                        .setId(JOB_ID)
                        .setOrganization(ORGANIZATION)
                        .build();

        final String entityID = _refSerializer.serialize(ref);
        final JobRef<?> outref = _refSerializer.deserialize(entityID);
        assertThat(outref, is(ref));
    }

    @Test(expected = DeserializationException.class)
    public void testFailureWhenStructureIsInvalid() throws DeserializationException {
        final String entityID = "1&2&3&4&5&6";
        _refSerializer.deserialize(entityID);
    }

    @Test(expected = DeserializationException.class)
    public void testFailureWhenRepositoryIsInvalid() throws DeserializationException {
        final String entityID = "UnknownRepository&"
                + "com.arpnetworking.metrics.portal.scheduling.TwoWayJobRefSerializerTest$MockJobExecutionRepository&"
                + "885d4ed6-093b-4570-b766-fd30040af67e&72a0e49c-d0e3-445a-b7d2-bd1012125a30";
        _refSerializer.deserialize(entityID);
    }

    @Test(expected = DeserializationException.class)
    public void testFailureWhenExecutionRepositoryIsInvalid() throws DeserializationException {
        final String entityID = "com.arpnetworking.metrics.portal.scheduling.TwoWayJobRefSerializerTest$MockJobRepository&"
                + "UnknownRepository&885d4ed6-093b-4570-b766-fd30040af67e&72a0e49c-d0e3-445a-b7d2-bd1012125a30";
        _refSerializer.deserialize(entityID);
    }

    private interface MockJobRepository extends JobRepository<Integer> {}

    private interface MockJobExecutionRepository extends JobExecutionRepository<Integer> {}
}
