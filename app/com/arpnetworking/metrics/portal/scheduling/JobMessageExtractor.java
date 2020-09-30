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
package com.arpnetworking.metrics.portal.scheduling;

import akka.cluster.sharding.ShardRegion;
import com.google.inject.Inject;

import javax.annotation.Nullable;

/**
 * Extracts data from messages to setup job executors.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobMessageExtractor extends ShardRegion.HashCodeMessageExtractor {
    private final JobRefSerializer _refSerializer;

    /**
     * Public constructor.
     *
     * @param refSerializer the serializer to use to construct the entity ID from each job ref.
     */
    @Inject
    public JobMessageExtractor(final JobRefSerializer refSerializer) {
        super(NUM_SHARDS);
        _refSerializer = refSerializer;
    }

    @Override
    @Nullable
    public String entityId(final Object message) {
        if (message instanceof JobExecutorActor.Reload) {
            return _refSerializer.serialize(((JobExecutorActor.Reload) message).getJobRef());
        }
        return null;
    }

    @Override
    @Nullable
    public Object entityMessage(final Object message) {
        return message;
    }

    private static final int NUM_SHARDS = 3000;
}
