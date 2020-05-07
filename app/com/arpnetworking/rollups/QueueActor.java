/*
 * Copyright 2020 Dropbox Inc.
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
package com.arpnetworking.rollups;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import com.arpnetworking.logback.annotations.Loggable;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import javax.inject.Inject;

/**
 * TODO(spencerpearson)
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 *
 */
public class QueueActor<T> extends AbstractActor {

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .match(Task.class, task -> {
                    _queue.add(task);
                })
                .match(WorkRequest.class, request -> {
                    getSender().tell(
                            Optional.ofNullable((Object) _queue.poll()).orElse(NoMoreWork.getInstance()),
                            getSelf()
                    );
                })
                .build();
    }

    /**
     * {@link QueueActor} actor constructor.
     */
    @Inject
    public QueueActor() {}

    private final Queue<Task<T>> _queue = new ArrayDeque<>();

    @Loggable
    public static final class Task<T> implements Serializable {
        private final T _wrapped;

        public Task(final T _wrapped) {
            this._wrapped = _wrapped;
        }
    }

    @Loggable
    public static final class WorkRequest implements Serializable {
        private static final WorkRequest _INSTANCE = new WorkRequest();
        public static WorkRequest getInstance() {
            return _INSTANCE;
        }
        private WorkRequest() {}
    }

    @Loggable
    public static final class NoMoreWork implements Serializable {
        private static final NoMoreWork _INSTANCE = new NoMoreWork();
        public static NoMoreWork getInstance() {
            return _INSTANCE;
        }
        private NoMoreWork() {}
    }
}
