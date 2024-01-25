/*
 * Copyright 2023 Brandon Arp
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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Implementation of {@link HealthProvider} interface which looks at the Pekko cluster
 * state to determine health. If the host is a member and
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class PekkoMembershipHealthProvider implements HealthProvider {
    /**
     * Public constructor.
     *
     * @param statusActor the {@link StatusActor} to retrieve the cluster status from.
     */
    @Inject
    public PekkoMembershipHealthProvider(@Named("status") final ActorRef statusActor) {
        _statusActor = statusActor;
    }

    @Override
    public boolean isHealthy() {
        try {
            return ask(_statusActor, new StatusActor.HealthRequest(), Boolean.FALSE).toCompletableFuture().get(3, TimeUnit.SECONDS);
        } catch (final InterruptedException | TimeoutException | ExecutionException e) {
            return false;
        }

    }

    @SuppressWarnings("unchecked")
    private <T> CompletionStage<T> ask(final ActorRef actor, final Object request, @Nullable final T defaultValue) {
        return
                Patterns.ask(
                                actor,
                                request,
                                Duration.ofSeconds(3))
                        .thenApply(o -> (T) o)
                        .exceptionally(throwable -> {
                            LOGGER.error()
                                    .setMessage("error when routing ask")
                                    .addData("actor", actor)
                                    .addData("request", request)
                                    .setThrowable(throwable)
                                    .log();
                            return defaultValue;
                        });
    }

    private final ActorRef _statusActor;

    private static final Logger LOGGER = LoggerFactory.getLogger(PekkoMembershipHealthProvider.class);
}
