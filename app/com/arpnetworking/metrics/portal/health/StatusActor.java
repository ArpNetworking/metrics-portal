/*
 * Copyright 2014 Groupon.com
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

package com.arpnetworking.metrics.portal.health;

import models.view.StatusResponse;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.remote.artery.ThisActorSystemQuarantinedEvent;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Periodically polls the cluster status and caches the result.
 *
 * Accepts the following messages:
 *     {@link StatusRequest}: Replies with a StatusResponse message containing the service status data
 *     {@link HealthRequest}: Replies with a boolean value, true indicating healthy, false indicating unhealthy
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class StatusActor extends AbstractActor {
    /**
     * Public constructor.
     *
     * @param cluster The instance of the Clustering extension.
     * @param clusterStatusCache The actor holding the cached cluster status.
     */
    public StatusActor(
            final Cluster cluster,
            final ActorRef clusterStatusCache) {

        _cluster = cluster;
        _clusterStatusCache = clusterStatusCache;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        getContext().system().eventStream().subscribe(getSelf(), ThisActorSystemQuarantinedEvent.class);
    }

    /**
     * Creates a {@link Props} for use in Pekko.
     *
     * @param cluster The instance of the Clustering extension.
     * @param clusterStatusCache The actor holding the cached cluster status.
     * @return A new {@link Props}.
     */
    public static Props props(
            final Cluster cluster,
            final ActorRef clusterStatusCache) {

        return Props.create(StatusActor.class, cluster, clusterStatusCache);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(StatusRequest.class, message -> processStatusRequest())
                .match(ThisActorSystemQuarantinedEvent.class, event -> {
                    _quarantined = true;
                })
                .match(HealthRequest.class, message -> {
                    final boolean healthy = _cluster.readView().self().status() == MemberStatus.up() && !_quarantined;
                    sender().tell(healthy, getSelf());
                })
                .build();
    }

    private void processStatusRequest() {
        // Call the bookkeeper
        final CompletableFuture<ClusterStatusCacheActor.StatusResponse> clusterStateFuture =
                Patterns.ask(
                        _clusterStatusCache,
                        new ClusterStatusCacheActor.GetRequest(),
                        Duration.ofSeconds(3))
                .thenApply(ClusterStatusCacheActor.StatusResponse.class::cast)
                .exceptionally(new AsNullRecovery<>())
                .toCompletableFuture();

        Patterns.pipe(
                clusterStateFuture.toCompletableFuture()
                        .thenApply(statusResponse -> new StatusResponse.Builder()
                                .setClusterState(statusResponse)
                                .setLocalAddress(_cluster.selfAddress())
                                .build()),
                context().dispatcher())
                .to(sender(), self());
    }

    private final Cluster _cluster;
    private final ActorRef _clusterStatusCache;
    private boolean _quarantined;

    private static final class AsNullRecovery<T> implements Function<Throwable, T> {
        @Override
        @Nullable
        public T apply(final Throwable failure) {
            return null;
        }
    }

    /**
     * Represents a health check request.
     */
    public static final class HealthRequest {}

    /**
     * Represents a status request.
     */
    public static final class StatusRequest {}
}
