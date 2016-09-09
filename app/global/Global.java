/**
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

package global;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import scala.compat.java8.JFunction;
import scala.concurrent.ExecutionContext$;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Setup the global application components.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class Global {
    /**
     * Public constructor.
     *
     * @param akka the actor system.
     * @param lifecycle injected lifecycle.
     */
    @Inject
    public Global(final ActorSystem akka, final ApplicationLifecycle lifecycle) {
        LOGGER.info().setMessage("Starting application...").log();

        _akka = akka;
        lifecycle.addStopHook(this::onStop);

        // Configure Json serialization
        Json.setObjectMapper(ObjectMapperFactory.getInstance());

        LOGGER.debug().setMessage("Startup complete").log();
    }

    private CompletionStage<Void> onStop() {
        final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
        LOGGER.info().setMessage("Shutting down application...").log();

        final Cluster cluster = Cluster.get(_akka);
        cluster.leave(cluster.selfAddress());
        // Give the message 3 seconds to propagate through the fleet
        try {
            Thread.sleep(3000);
        } catch (final InterruptedException ignored) {
            // Clear the interrupted status
            Thread.interrupted();
        }

        _akka.terminate().onComplete(JFunction.func((t) -> {
            LOGGER.debug().setMessage("Shutdown complete").log();
            return shutdownFuture.complete(null);
        }), ExecutionContext$.MODULE$.global());

        return shutdownFuture;
    }

    private final ActorSystem _akka;

    private static final Logger LOGGER = LoggerFactory.getLogger(Global.class);
}
