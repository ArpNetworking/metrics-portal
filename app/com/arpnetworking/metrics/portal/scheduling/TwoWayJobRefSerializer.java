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

import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import models.internal.impl.DefaultOrganization;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * A {@link JobRefSerializer} that allows construction to and from entity IDs.
 *
 * Round-trip is only guaranteed for refs whose repository types are whitelisted by this serializer
 * (e.g. allowed by the constructor arguments).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class TwoWayJobRefSerializer implements JobRefSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TwoWayJobRefSerializer.class);

    private final Map<String, Class<? extends JobRepository<?>>> _repositories;
    private final Map<String, Class<? extends JobExecutionRepository<?>>> _execRepositories;

    /**
     * Public constructor.
     *
     * @param allowedJobRepositories the list of allowed job repositories
     * @param allowedJobExecRepositories the list of allowed job execution repositories.
     */
    public TwoWayJobRefSerializer(
            final List<Class<? extends JobRepository<?>>> allowedJobRepositories,
            final List<Class<? extends JobExecutionRepository<?>>> allowedJobExecRepositories
    ) {
        _repositories = allowedJobRepositories.stream()
                .collect(ImmutableMap.toImmutableMap(
                        Class::getSimpleName,
                        Function.identity()
                ));
        _execRepositories = allowedJobExecRepositories.stream()
                .collect(ImmutableMap.toImmutableMap(
                        Class::getSimpleName,
                        Function.identity()
                ));
    }

    @Override
    public String jobRefToEntityID(final JobRef<?> ref) {
        final String repoName = ref.getRepositoryType().getSimpleName();
        if (!_repositories.containsKey(repoName)) {
            LOGGER.warn()
                .setMessage("The repository on this ref is not whitelisted for serialization.")
                .addData("repositoryType", ref.getRepositoryType());
        }
        final String execRepoName = ref.getExecutionRepositoryType().getSimpleName();
        if (!_execRepositories.containsKey(execRepoName)) {
            LOGGER.warn()
                    .setMessage("The execution repository on this ref is not whitelisted for serialization.")
                    .addData("repositoryType", ref.getExecutionRepositoryType());
        }
        return String.join(
                "_",
                repoName,
                execRepoName,
                ref.getOrganization().getId().toString(),
                ref.getJobId().toString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<JobRef<?>> entityIDtoJobRef(final String id) {
        final List<String> parts = Arrays.asList(id.split("_"));
        if (parts.size() != 4) {
            return Optional.empty();
        }
        final String repoClassName = parts.get(0);
        final String execRepoClassName = parts.get(1);
        final UUID orgId = UUID.fromString(parts.get(2));
        final UUID jobId = UUID.fromString(parts.get(3));

        final Optional<Class<? extends JobRepository<?>>> repo = resolveRepository(repoClassName);
        final Optional<Class<? extends JobExecutionRepository<?>>> execRepo = resolveExecutionRepository(execRepoClassName);

        if (repo.isPresent() && execRepo.isPresent()) {
            return Optional.of(new JobRef.Builder<>()
                    .setId(jobId)
                    .setOrganization(new DefaultOrganization.Builder().setId(orgId).build())
                    .setRepositoryType((Class<? extends JobRepository<Object>>) repo.get())
                    .setExecutionRepositoryType((Class<? extends JobExecutionRepository<Object>>) execRepo.get())
                    .build());
        }
        return Optional.empty();
    }

    private Optional<Class<? extends JobRepository<?>>> resolveRepository(final String className) {
        return Optional.ofNullable(_repositories.get(className));
    }

    private Optional<Class<? extends JobExecutionRepository<?>>> resolveExecutionRepository(final String className) {
        return Optional.ofNullable(_execRepositories.get(className));
    }
}
