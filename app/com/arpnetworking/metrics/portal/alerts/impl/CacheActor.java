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
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * A actor that acts as a simple key-value store. You can use this as a cluster-wide cache
 * by starting as a cluster singleton, or as a building-block for a more complex cache
 * by using it as a member within a ClusterSharding pool.
 *
 * @param <K> The type of key.
 * @param <V> The type of the associated value.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class CacheActor<K extends Serializable, V extends Serializable> extends AbstractActor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheActor.class);

    private final PeriodicMetrics _metrics;
    private final String _cacheName;
    private final HashMap<K, V> _cache;

    private CacheActor(final String cacheName, final PeriodicMetrics metrics) {
        _cacheName = cacheName;
        _metrics = metrics;
        _cache = Maps.newHashMap();
    }

    /**
     * Props to construct a new instance of {@code CacheActor}.
     *
     * @param cacheName The name of the cache.
     * @param metrics A metrics instance to record against.
     * @return props to instantiate the actor
     */
    public static Props props(final String cacheName, final PeriodicMetrics metrics) {
        return Props.create(CacheActor.class, () -> new CacheActor<>(cacheName, metrics));
    }

    /**
     * Retrieve a the value associated with this key.
     *
     * @param ref The CacheActor ref.
     * @param key The key to retrieve.
     * @param timeout The operation timeout.
     * @param <K> The type of key.
     * @param <V> The type of value.
     * @return A CompletionStage which contains the value, if any.
     */
    @SuppressWarnings("unchecked")
    public static <K extends Serializable, V extends Serializable> CompletionStage<Optional<V>> get(
            final ActorRef ref,
            final K key,
            final Duration timeout
    ) {
        return Patterns.askWithReplyTo(
            ref,
            replyTo -> new CacheGet<>(key, replyTo),
            timeout
        ).thenApply(resp -> (Optional<V>) resp);
    }

    /**
     * Retrieve a the values associated with a set of keys.
     *
     * @param ref The CacheActor ref.
     * @param keys The keys to retrieve.
     * @param timeout The operation timeout.
     * @param <K> The type of key.
     * @param <V> The type of value.
     * @return A CompletionStage which contains the values, if any.
     */
    @SuppressWarnings("unchecked")
    public static <K extends Serializable, V extends Serializable> CompletionStage<ImmutableMap<K, V>> multiget(
            final ActorRef ref,
            final Collection<? extends K> keys,
            final Duration timeout
    ) {
        return Patterns.askWithReplyTo(
                ref,
                replyTo -> new CacheMultiGet<>(keys, replyTo),
                timeout
        ).thenApply(resp -> (ImmutableMap<K, V>) resp);
    }

    /**
     * Put a Key-Value pair into the cache.
     *
     * @param ref The CacheActor ref.
     * @param key The key to update.
     * @param value The associated value.
     * @param timeout The operation timeout.
     * @param <K> The type of key.
     * @param <V> The type of value.
     * @return A completion stage to await for the write to complete.
     */
    public static <K extends Serializable, V extends Serializable> CompletionStage<Void> put(
            final ActorRef ref,
            final K key,
            final V value,
            final Duration timeout
    ) {
        return Patterns.askWithReplyTo(
            ref,
            replyTo -> new CachePut<>(key, value, Optional.of(replyTo)),
            timeout
        ).thenApply(resp -> null);
    }

    /**
     * Put a Key-Value pair into the cache, without waiting for a reply.
     *
     * @param ref The CacheActor ref.
     * @param key The key to update.
     * @param value The associated value.
     * @param <K> The type of key.
     * @param <V> The type of value.
     */
    public static <K extends Serializable, V extends Serializable> void putAsync(
            final ActorRef ref,
            final K key,
            final V value
    ) {
        final CachePut<K, V> msg = new CachePut<>(key, value, Optional.empty());
        ref.tell(msg, ActorRef.noSender());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Receive createReceive() {
        return receiveBuilder()
            .match(CacheGet.class, msg -> {
                final CacheGet<K> castMsg = (CacheGet<K>) msg;
                final Optional<V> value = Optional.ofNullable(_cache.get(castMsg.getKey()));
                _metrics.recordCounter(String.format("cache/%s/get", _cacheName), value.isPresent() ? 1 : 0);
                castMsg.getReplyTo().tell(value, getSelf());
            })
            .match(CacheMultiGet.class, msg -> {
                final CacheMultiGet<K> castMsg = (CacheMultiGet<K>) msg;
                final ImmutableMap.Builder<K, V> results = new ImmutableMap.Builder<>();
                int hits = 0;
                for (final K key : castMsg.getKeys()) {
                    final Optional<V> value = Optional.ofNullable(_cache.get(key));
                    if (value.isPresent()) {
                        hits += 1;
                        results.put(key, value.get());
                    }
                }
                _metrics.recordCounter(String.format("cache/%s/multiget", _cacheName), hits);
                castMsg.getReplyTo().tell(results, getSelf());
            })
            .match(CachePut.class, msg -> {
                _metrics.recordCounter(String.format("cache/%s/put", _cacheName), 1);
                final CachePut<K, V> putMsg = (CachePut<K, V>) msg;
                final ActorRef self = getSelf();
                _cache.put(putMsg.getKey(), putMsg.getValue());
                putMsg.getReplyTo().ifPresent(ref -> ref.tell(new Status.Success(null), self));
            })
            .build();
    }

    private static final class CacheGet<K extends Serializable> {
        private final K _key;
        private final ActorRef _replyTo;

        /**
         * Constructor.
         *
         * @param key The key to retrieve.
         * @param replyTo The address to reply to with the value, if any.
         */
        CacheGet(final K key, final ActorRef replyTo) {
            _key = key;
            _replyTo = replyTo;
        }

        public K getKey() {
            return _key;
        }

        public ActorRef getReplyTo() {
            return _replyTo;
        }
    }

    private static final class CacheMultiGet<K extends Serializable> {
        private final Collection<? extends K> _keys;
        private final ActorRef _replyTo;

        /**
         * Constructor.
         *
         * @param keys The keys to retrieve.
         * @param replyTo The address to reply to with the value, if any.
         */
        CacheMultiGet(final Collection<? extends K> keys, final ActorRef replyTo) {
            _keys = keys;
            _replyTo = replyTo;
        }

        public Collection<? extends K> getKeys() {
            return _keys;
        }

        public ActorRef getReplyTo() {
            return _replyTo;
        }
    }

    private static final class CachePut<K extends Serializable, V extends Serializable> {
        private final K _key;
        private final V _value;
        private final Optional<ActorRef> _replyTo;

        /**
         * Constructor.
         *
         * @param key The key to store.
         * @param value The value to associate with this key.
         * @param replyTo The address to reply to.
         */
        CachePut(final K key, final V value, final Optional<ActorRef> replyTo) {
            _key = key;
            _value = value;
            _replyTo = replyTo;
        }

        public K getKey() {
            return _key;
        }

        public V getValue() {
            return _value;
        }

        public Optional<ActorRef> getReplyTo() {
            return _replyTo;
        }
    }
}
