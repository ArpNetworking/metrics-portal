/*
 * Copyright 2020 Dropbox, Inc.
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

import jakarta.inject.Inject;
import org.apache.pekko.actor.ActorSystem;
import play.libs.concurrent.CustomExecutionContext;

/**
 * An execution context for handling execution of blocking operations.
 * <br>
 * This should be used when an operation could potentially block the actor dispatcher thread.
 * In particular, most database operations which use Ebean will be blocking and should be
 * executed here.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class BlockingIOExecutionContext extends CustomExecutionContext {

    /**
     * Constructor.
     *
     * @param actorSystem the actor system used in this application.
     */
    @Inject
    public BlockingIOExecutionContext(final ActorSystem actorSystem) {
        super(actorSystem, "blocking-io-dispatcher");
    }
}
