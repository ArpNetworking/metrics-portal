/**
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
package com.arpnetworking.metrics.portal.scheduling;

import akka.cluster.sharding.ShardRegion;

import javax.annotation.Nullable;

/**
 * Extracts data from messages to setup job executors.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class JobMessageExtractor extends ShardRegion.HashCodeMessageExtractor {
    /**
     * Public constructor.
     */
    public JobMessageExtractor() {
        super(NUM_SHARDS);
    }

    @Override
    @Nullable
    public String entityId(final Object message) {
        if (message instanceof JobExecutorActor.Reload) {
            return jobRefToUId(((JobExecutorActor.Reload) message).getJobRef());
        }
        return null;
    }

    @Override
    @Nullable
    public Object entityMessage(final Object message) {
        return message;
    }

    private static String jobRefToUId(final JobRef<?> ref) {
        return String.format(
                "repoType-%s--orgId-%s--jobId-%s",
                ref.getRepositoryType(),
                ref.getOrganization().getId(),
                ref.getJobId());
    }

    private static final int NUM_SHARDS = 100;
}
