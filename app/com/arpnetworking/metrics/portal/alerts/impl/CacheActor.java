/*
 * Copyright 2021 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.alerts.impl;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.pattern.Patterns;
import com.arpnetworking.commons.akka.AkkaJsonSerializable;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;
import net.sf.oval.constraint.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * A actor that acts as a simple key-value store. You can use this as a cluster-wide cache
 * by starting as a cluster singleton, or as a building-block for a more complex cache
 * by using it as a member within a ClusterSharding pool.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class CacheActor extends AbstractActor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheActor.class);

    private final PeriodicMetrics _metrics;
    private final String _cacheName;
    private final Map<UUID, JobExecution.Success<AlertEvaluationResult>> _cache;
//    private final Cache<UUID, JobExecution.Success<AlertEvaluationResult>> _cache;

    private CacheActor(
            final String cacheName,
            final PeriodicMetrics metrics) {
        _cacheName = cacheName;
        _metrics = metrics;
        _cache = Maps.newHashMap();
//        _cache = CacheBuilder.newBuilder()
//                    .expireAfterAccess(2, TimeUnit.MINUTES)
//                    .maximumSize(2000)
//                    .build();
    }

    /**
     * Props to construct a new instance of {@code CacheActor}.
     *
     * @param cacheName The name of the cache.
     * @param metrics A metrics instance to record against.
     * @return props to instantiate the actor
     */
    public static Props props(final String cacheName, final PeriodicMetrics metrics) {
        return Props.create(CacheActor.class, () -> new CacheActor(cacheName, metrics));
    }

    /**
     * Retrieve a the value associated with this key.
     *
     * @param ref The CacheActor ref.
     * @param key The key to retrieve.
     * @param timeout The operation timeout.
     * @return A CompletionStage which contains the value, if any.
     */
    @SuppressWarnings("unchecked")
    public static CompletionStage<Optional<JobExecution.Success<AlertEvaluationResult>>> get(
            final ActorRef ref,
            final UUID key,
            final Duration timeout
    ) {
        return Patterns.askWithReplyTo(
            ref,
            replyTo -> new CacheGet.Builder().setKey(key).setReplyTo(Optional.of(replyTo)).build(),
            timeout
        ).thenApply(resp -> (Optional<JobExecution.Success<AlertEvaluationResult>>) resp);
    }

    /**
     * Retrieve a the values associated with a set of keys.
     *
     * @param ref The CacheActor ref.
     * @param keys The keys to retrieve.
     * @param timeout The operation timeout.
     * @return A CompletionStage which contains the values, if any.
     */
    @SuppressWarnings("unchecked")
    public static CompletionStage<ImmutableMap<UUID, JobExecution.Success<AlertEvaluationResult>>> multiget(
            final ActorRef ref,
            final Collection<UUID> keys,
            final Duration timeout
    ) {
        return Patterns.askWithReplyTo(
                ref,
                replyTo -> new CacheMultiGet(keys, replyTo),
                timeout
        ).thenApply(resp -> (ImmutableMap<UUID, JobExecution.Success<AlertEvaluationResult>>) resp);
    }

    /**
     * Put a Key-Value pair into the cache.
     *
     * @param ref The CacheActor ref.
     * @param key The key to update.
     * @param value The associated value.
     * @param timeout The operation timeout.
     * @return A completion stage to await for the write to complete.
     */
    public static CompletionStage<Void> put(
            final ActorRef ref,
            final UUID key,
            final JobExecution.Success<AlertEvaluationResult> value,
            final Duration timeout
    ) {
        return Patterns.askWithReplyTo(
            ref,
            replyTo -> new CachePut.Builder().setKey(key).setValue(value).build(),
            timeout
        ).thenApply(resp -> null);
    }

    /**
     * Put a Key-Value pair into the cache, without waiting for a reply.
     *
     * @param ref The CacheActor ref.
     * @param key The key to update.
     * @param value The associated value.
     */
    public static void putAsync(
            final ActorRef ref,
            final UUID key,
            final JobExecution.Success<AlertEvaluationResult> value
    ) {
        final CachePut msg = new CachePut.Builder().setKey(key).setValue(value).build();
        ref.tell(msg, ActorRef.noSender());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Receive createReceive() {
        return receiveBuilder()
            .match(CacheGet.class, msg -> {
                final Optional<JobExecution.Success<AlertEvaluationResult>> value = Optional.ofNullable(_cache.get(msg.getKey()));
                _metrics.recordCounter(String.format("cache/%s/get", _cacheName), value.isPresent() ? 1 : 0);
                msg.getReplyTo().tell(value, getSelf());
            })
            .match(CacheMultiGet.class, msg -> {
                final ImmutableMap.Builder<UUID, JobExecution.Success<AlertEvaluationResult>> results = new ImmutableMap.Builder<>();
                int hits = 0;
                for (final UUID key : msg.getKeys()) {
                    final Optional<JobExecution.Success<AlertEvaluationResult>> value = Optional.ofNullable(_cache.get(key));
                    if (value.isPresent()) {
                        hits += 1;
                        results.put(key, value.get());
                    }
                }
                _metrics.recordCounter(String.format("cache/%s/multiget", _cacheName), hits);
                msg.getReplyTo().tell(results, getSelf());
            })
            .match(CachePut.class, msg -> {
                _metrics.recordCounter(String.format("cache/%s/put", _cacheName), 1);
                final ActorRef self = getSelf();
                _cache.put(msg.getKey(), msg.getValue());
                msg.getReplyTo().ifPresent(ref -> ref.tell(new Status.Success(null), self));
            })
            .build();
    }

    private static final class CacheGet implements AkkaJsonSerializable {
        private final UUID _key;
        private final ActorRef _replyTo;

        /**
         * Constructor.
         */
        private CacheGet(final Builder builder) {
            _key = builder._key;
            _replyTo = builder._replyTo;
        }

        public UUID getKey() {
            return _key;
        }

        public ActorRef getReplyTo() {
            return _replyTo;
        }

        public static final class Builder extends OvalBuilder<CacheGet> {
            @NotNull
            private UUID _key;
            @NotNull
            private ActorRef _replyTo;

            Builder() {
                super(CacheGet::new);
            }

            public Builder setKey(final UUID key) {
                _key = key;
                return this;
            }

            public Builder setReplyTo(final Optional<ActorRef> actorRef) {
                _replyTo = actorRef.orElse(null);
                return this;
            }
        }
    }

    private static final class CacheMultiGet {
        private final Collection<UUID> _keys;
        private final ActorRef _replyTo;

        /**
         * Constructor.
         *
         * @param keys The keys to retrieve.
         * @param replyTo The address to reply to with the value, if any.
         */
        CacheMultiGet(final Collection<UUID> keys, final ActorRef replyTo) {
            _keys = keys;
            _replyTo = replyTo;
        }

        public Collection<UUID> getKeys() {
            return _keys;
        }

        public ActorRef getReplyTo() {
            return _replyTo;
        }
    }

    private static final class CachePut implements AkkaJsonSerializable {
        private final UUID _key;
        private final JobExecution.Success<AlertEvaluationResult> _value;
        private final Optional<ActorRef> _replyTo;

        private CachePut(final Builder builder) {
            _key = builder._key;
            _value = builder._value;
            _replyTo = builder._replyTo;
        }

        public UUID getKey() {
            return _key;
        }

        public JobExecution.Success<AlertEvaluationResult> getValue() {
            return _value;
        }

        public Optional<ActorRef> getReplyTo() {
            return _replyTo;
        }

        public static final class Builder extends OvalBuilder<CachePut> {
            @NotNull
            private UUID _key;
            @NotNull
            private JobExecution.Success<AlertEvaluationResult> _value;

            private Optional<ActorRef> _replyTo = Optional.empty();

            Builder() {
                super(CachePut::new);
            }

            public Builder setKey(final UUID key) {
                _key = key;
                return this;
            }

            public Builder setValue(final JobExecution.Success<AlertEvaluationResult> value) {
                _value = value;
                return this;
            }

            public Builder setReplyTo(final Optional<ActorRef> actorRef) {
                _replyTo = actorRef;
                return this;
            }
        }
    }
}
