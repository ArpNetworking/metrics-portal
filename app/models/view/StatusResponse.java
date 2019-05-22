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
package models.view;

import akka.actor.Address;
import akka.cluster.Member;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.health.ClusterStatusCacheActor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Iterables;
import models.internal.ShardAllocation;
import net.sf.oval.constraint.NotNull;
import scala.collection.JavaConversions;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Response model for the status http endpoint.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public final class StatusResponse {
    @Nullable
    public String getClusterLeader() {
        return _clusterLeader != null ? _clusterLeader.toString() : null;
    }

    public String getLocalAddress() {
        return _localAddress.toString();
    }

    @JsonProperty("isLeader")
    public boolean isLeader() {
        return _localAddress.equals(_clusterLeader);
    }

    @JsonSerialize(contentUsing = MemberSerializer.class)
    public Iterable<Member> getMembers() {
        return Iterables.unmodifiableIterable(_members);
    }

    public Optional<List<ShardAllocation>> getAllocations() {
        return _allocations.map(Collections::unmodifiableList);
    }

    @JsonUnwrapped
    public VersionInfo getVersionInfo() {
        return VersionInfo.getInstance();
    }

    private StatusResponse(final Builder builder) {
        if (builder._clusterState == null) {
            _clusterLeader = null;
            _members = Collections.emptyList();
        } else {
            _clusterLeader = builder._clusterState.getClusterState().getLeader();
            _members = builder._clusterState.getClusterState().getMembers();
        }

        _localAddress = builder._localAddress;
        _allocations = flatten(
                Optional.ofNullable(builder._clusterState)
                        .map(ClusterStatusCacheActor.StatusResponse::getAllocations));
    }

    private <T> Optional<T> flatten(final Optional<Optional<T>> value) {
        if (value.isPresent()) {
            return value.get();
        }
        return Optional.empty();
    }

    private final Address _localAddress;
    @Nullable
    private final Address _clusterLeader;
    private final Iterable<Member> _members;
    private final Optional<List<ShardAllocation>> _allocations;

    /**
     * Builder for a {@link StatusResponse}.
     */
    public static class Builder extends OvalBuilder<StatusResponse> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(StatusResponse::new);
        }

        /**
         * Sets the cluster state. Optional.
         *
         * @param value The cluster state.
         * @return This builder.
         */
        public Builder setClusterState(@Nullable final ClusterStatusCacheActor.StatusResponse value) {
            _clusterState = value;
            return this;
        }

        /**
         * Sets the local address of this cluster node. Required. Cannot be null.
         *
         * @param value The address of the local node.
         * @return This builder.
         */
        public Builder setLocalAddress(final Address value) {
            _localAddress = value;
            return this;
        }

        @Nullable
        private ClusterStatusCacheActor.StatusResponse _clusterState;
        @NotNull
        private Address _localAddress;
    }

    private static final class MemberSerializer extends JsonSerializer<Member> {
        @Override
        public void serialize(
                final Member value,
                final JsonGenerator gen,
                final SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("address", value.address().toString());
            gen.writeObjectField("roles", JavaConversions.setAsJavaSet(value.roles()));
            gen.writeNumberField("upNumber", value.upNumber());
            gen.writeStringField("status", value.status().toString());
            gen.writeNumberField("uniqueAddress", value.uniqueAddress().longUid());
            gen.writeEndObject();
        }
    }
}
