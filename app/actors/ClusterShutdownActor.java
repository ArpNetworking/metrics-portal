/*
 * Copyright 2016 Smartsheet.com
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
package actors;

import akka.actor.AbstractActor;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;

import java.util.concurrent.CompletableFuture;

/**
 * Actor that subscribes to cluster events and triggers a CompletableFuture when it sees itself removed
 * from the cluster.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public class ClusterShutdownActor extends AbstractActor {
    /**
     * Creates a {@link Props} for this actor.
     *
     * @param shutdownFuture A future to be completed at cluster shutdown.
     * @return A new Props.
     */
    public static Props props(final CompletableFuture<Boolean> shutdownFuture) {
        return Props.create(ClusterShutdownActor.class, shutdownFuture);
    }

    /**
     * Public constructor.
     *
     * @param shutdownFuture A future to be completed at cluster shutdown.
     */
    public ClusterShutdownActor(final CompletableFuture<Boolean> shutdownFuture) {
        _shutdownFuture = shutdownFuture;

        final Cluster cluster = Cluster.get(context().system());
        _selfAddress = cluster.selfAddress();
        cluster.subscribe(self(), ClusterEvent.initialStateAsEvents(), ClusterEvent.MemberRemoved.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ClusterEvent.MemberRemoved.class, removed -> {
                    final Member member = removed.member();
                    if (_selfAddress.equals(member.address())) {
                        _shutdownFuture.complete(Boolean.TRUE);
                    }
                })
                .build();
    }

    private final CompletableFuture<Boolean> _shutdownFuture;
    private final Address _selfAddress;
}
