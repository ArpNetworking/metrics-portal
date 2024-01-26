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

package global;

import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.pekko.actor.ActorSystem;
import play.inject.ApplicationLifecycle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Setup the global application components.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class Global {
    /**
     * Public constructor.
     *
     * @param pekko the actor system.
     * @param lifecycle injected lifecycle.
     */
    @Inject
    public Global(final ActorSystem pekko, final ApplicationLifecycle lifecycle) {
        LOGGER.info().setMessage("Starting application...").log();
        _pekko = pekko;
        lifecycle.addStopHook(this::onStop);

        LOGGER.debug().setMessage("Startup complete").log();
    }

    @SuppressFBWarnings(value = "NP_NONNULL_PARAM_VIOLATION", justification = "CompletableFuture can take a null arg")
    private CompletionStage<Void> onStop() {
        final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
        LOGGER.info().setMessage("Shutting down application...").log();

        LOGGER.info().setMessage("Shutdown complete").log();
        shutdownFuture.complete(null);
        return shutdownFuture;
    }

    private final ActorSystem _pekko;
    private final CompletableFuture<Boolean> _shutdownFuture = new CompletableFuture<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(Global.class);
}
