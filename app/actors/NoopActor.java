/*
 * Copyright 2019 Dropbox Inc.
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

import org.apache.pekko.actor.AbstractActor;

/**
 * Simple actor that does nothing, used for passing actorRefs to controllers that are disabled.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class NoopActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return emptyBehavior();
    }
}
