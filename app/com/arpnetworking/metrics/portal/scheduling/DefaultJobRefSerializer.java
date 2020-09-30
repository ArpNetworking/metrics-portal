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

import com.arpnetworking.commons.serialization.DeserializationException;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import models.internal.impl.DefaultOrganization;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A {@link JobRefSerializer} that allows construction to and from entity IDs.
 * <p>
 * Round-trip is only guaranteed for refs whose repository types are whitelisted by this serializer (e.g. allowed by the
 * constructor arguments).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DefaultJobRefSerializer implements JobRefSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJobRefSerializer.class);
    private static final String DELIMETER = "&";

    /**
     * Public constructor.
     */
    public DefaultJobRefSerializer() {
    }

    @Override
    public String serialize(final JobRef<?> ref) {
        final String repoName = ref.getRepositoryType().getName();
        final String execRepoName = ref.getExecutionRepositoryType().getName();
        return String.join(
                DELIMETER,
                repoName,
                execRepoName,
                ref.getOrganization().getId().toString(),
                ref.getJobId().toString());
    }

    @Override
    public JobRef<?> deserialize(final String id) throws DeserializationException {
        final List<String> parts = Arrays.asList(id.split(DELIMETER));
        if (parts.size() != 4) {
            throw new DeserializationException("expected exactly 4 parts");
        }
        final String repoClassName = parts.get(0);
        final String execRepoClassName = parts.get(1);
        final UUID orgId = UUID.fromString(parts.get(2));
        final UUID jobId = UUID.fromString(parts.get(3));

        final Class<? extends JobRepository<Object>> repo = resolveRepository(repoClassName);
        final Class<? extends JobExecutionRepository<Object>> execRepo = resolveExecutionRepository(execRepoClassName);

        return new JobRef.Builder<>()
                .setId(jobId)
                .setOrganization(new DefaultOrganization.Builder().setId(orgId).build())
                .setRepositoryType(repo)
                .setExecutionRepositoryType(execRepo)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends JobRepository<Object>> resolveRepository(final String className) throws DeserializationException {
        final Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (final ClassNotFoundException e) {
            throw new DeserializationException(e);
        }
        try {
            return (Class<? extends JobRepository<Object>>) clazz;
        } catch (final ClassCastException e) {
            throw new DeserializationException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends JobExecutionRepository<Object>> resolveExecutionRepository(
            final String className
    ) throws DeserializationException {
        final Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (final ClassNotFoundException e) {
            throw new DeserializationException(e);
        }
        try {
            return (Class<? extends JobExecutionRepository<Object>>) clazz;
        } catch (final ClassCastException e) {
            throw new DeserializationException(e);
        }
    }
}
