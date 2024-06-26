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

import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.notcommons.pekko.ParallelLeastShardAllocationStrategy;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import models.internal.ShardAllocation;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Scheduler;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.cluster.ClusterEvent;
import scala.jdk.javaapi.OptionConverters;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Caches the cluster state.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class ClusterStatusCacheActor extends AbstractActor {

    /**
     * Creates a {@link org.apache.pekko.actor.Props} for use in Pekko.
     *
     * @param cluster The cluster to reference.
     * @param metricsFactory A {@link MetricsFactory} to use for metrics creation.
     * @return A new {@link org.apache.pekko.actor.Props}
     */
    public static Props props(final Cluster cluster, final MetricsFactory metricsFactory) {
        return Props.create(ClusterStatusCacheActor.class, cluster, metricsFactory);
    }

    /**
     * Public constructor.
     *
     * @param cluster {@link org.apache.pekko.cluster.Cluster} whose state is cached
     * @param metricsFactory A {@link MetricsFactory} to use for metrics creation.
     */
    public ClusterStatusCacheActor(final Cluster cluster, final MetricsFactory metricsFactory) {
        _cluster = cluster;
        _metricsFactory = metricsFactory;
    }

    @Override
    public void preStart() {
        final Scheduler scheduler = getContext()
                .system()
                .scheduler();
        _pollTimer = scheduler.scheduleAtFixedRate(
                Duration.ofSeconds(0),
                Duration.ofSeconds(10),
                getSelf(),
                POLL,
                getContext().system().dispatcher(),
                getSelf());
    }

    @Override
    public void postStop() throws Exception {
        if (_pollTimer != null) {
            _pollTimer.cancel();
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ClusterEvent.CurrentClusterState.class, clusterState -> {
                    _clusterState = Optional.of(clusterState);
                    try (Metrics metrics = _metricsFactory.create()) {
                        metrics.setGauge("pekko/members_count", Streams.stream(clusterState.getMembers()).count());
                        if (_cluster.selfAddress().equals(clusterState.getLeader())) {
                            metrics.setGauge("pekko/is_leader", 1);
                        } else {
                            metrics.setGauge("pekko/is_leader", 0);
                        }
                    }
                })
                .match(GetRequest.class, message -> sendResponse(getSender()))
                .match(ParallelLeastShardAllocationStrategy.RebalanceNotification.class, rebalanceNotification -> {
                    _rebalanceState = Optional.of(rebalanceNotification);
                })
                .matchEquals(POLL, message -> {
                    if (self().equals(sender())) {
                        _cluster.sendCurrentClusterState(getSelf());
                    } else {
                        unhandled(message);
                    }
                })
                .build();
    }

    private void sendResponse(final ActorRef sender) {
        final StatusResponse response = new StatusResponse(
                _clusterState.orElse(_cluster.state()),
                _rebalanceState);
        sender.tell(response, self());
    }

    private static String hostFromActorRef(final ActorRef shardRegion) {

        return OptionConverters.toJava(
                shardRegion.path()
                        .address()
                        .host())
                .orElse("localhost");
    }

    private final Cluster _cluster;
    private final MetricsFactory _metricsFactory;
    private Optional<ClusterEvent.CurrentClusterState> _clusterState = Optional.empty();
    @Nullable
    private Cancellable _pollTimer;
    private Optional<ParallelLeastShardAllocationStrategy.RebalanceNotification> _rebalanceState = Optional.empty();

    private static final String POLL = "poll";

    /**
     * Request to get a cluster status.
     */
    public static final class GetRequest implements Serializable {
        private static final long serialVersionUID = 2804853560963013618L;
    }

    /**
     * Response to a cluster status request.
     */
    public static final class StatusResponse implements Serializable {

        /**
         * Public constructor.
         *
         * @param clusterState the cluster state
         * @param rebalanceNotification the last rebalance data
         */
        public StatusResponse(
                final ClusterEvent.CurrentClusterState clusterState,
                final Optional<ParallelLeastShardAllocationStrategy.RebalanceNotification> rebalanceNotification) {
            _clusterState = clusterState;

            if (rebalanceNotification.isPresent()) {
                final ParallelLeastShardAllocationStrategy.RebalanceNotification notification = rebalanceNotification.get();

                // There may be a shard joining the cluster that is not in the currentAllocations list yet, but will
                // have pending rebalances to it.  Compute the set of all shard regions by unioning the current allocation list
                // with the destinations of the rebalances.
                final Set<ActorRef> allRefs = Sets.union(
                        notification.getCurrentAllocations().keySet(),
                        Sets.newHashSet(notification.getPendingRebalances().values()));

                final Map<String, ActorRef> pendingRebalances = notification.getPendingRebalances();

                final Map<ActorRef, Set<String>> currentAllocations = notification.getCurrentAllocations();

                _allocations =
                        allRefs.stream()
                                .map(shardRegion -> computeShardAllocation(pendingRebalances, currentAllocations, shardRegion))
                                .collect(Collectors.toCollection(ArrayList::new));
            } else {
                _allocations = null;
            }
        }

        private ShardAllocation computeShardAllocation(
                final Map<String, ActorRef> pendingRebalances,
                final Map<ActorRef, Set<String>> currentAllocations,
                final ActorRef shardRegion) {
            // Setup the map of current shard allocations
            final Set<String> currentShards = currentAllocations.getOrDefault(shardRegion, Collections.emptySet());


            // Setup the list of incoming shard allocations
            final Map<ActorRef, Collection<String>> invertPending = Multimaps
                    .invertFrom(Multimaps.forMap(pendingRebalances), ArrayListMultimap.create())
                    .asMap();
            final Set<String> incomingShards = Sets.newHashSet(invertPending.getOrDefault(shardRegion, Collections.emptyList()));

            // Setup the list of outgoing shard allocations
            final Set<String> outgoingShards = Sets.intersection(currentShards, pendingRebalances.keySet()).immutableCopy();

            // Remove the outgoing shards from the currentShards list
            currentShards.removeAll(outgoingShards);

            return new ShardAllocation.Builder()
                    .setCurrentShards(currentShards)
                    .setIncomingShards(incomingShards)
                    .setOutgoingShards(outgoingShards)
                    .setHost(hostFromActorRef(shardRegion))
                    .setShardRegion(shardRegion)
                    .build();
        }

        public ClusterEvent.CurrentClusterState getClusterState() {
            return _clusterState;
        }

        public Optional<List<ShardAllocation>> getAllocations() {
            return Optional.ofNullable(_allocations);
        }

        private final ClusterEvent.CurrentClusterState _clusterState;
        @Nullable
        private final ArrayList<ShardAllocation> _allocations;
        private static final long serialVersionUID = 603308359721162702L;
    }
}
