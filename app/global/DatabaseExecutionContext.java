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

import akka.actor.ActorSystem;
import com.google.inject.Inject;
import play.libs.concurrent.CustomExecutionContext;

/**
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DatabaseExecutionContext extends CustomExecutionContext {

    /**
     * @param actorSystem
     */
    @Inject
    public DatabaseExecutionContext(final ActorSystem actorSystem) {
        super(actorSystem, "database-dispatcher");
    }
}