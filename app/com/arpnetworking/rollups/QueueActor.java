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
public class QueueActor<T extends Serializable> extends AbstractActor {

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .match(Add.class, message -> {
                    final T item;
                    try {
                        item = (T) message.getWork();
                    } catch (final ClassCastException err) {
                        getSender().tell(new AddRejected<>(message._work), getSelf());
                        return;
                    }
                    if (_queue.size() < _maxSize) {
                        _queue.add(item);
                        getSender().tell(new AddAccepted<>(item), getSelf());
                    } else {
                        getSender().tell(new AddRejected<>(item), getSelf());
                    }
                })
                .match(Poll.class, request -> {
                    getSender().tell(
                            Optional.ofNullable((Object) _queue.poll())
                                    .orElse(NoMoreWork.getInstance()),
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

    private final int _maxSize = 32; // TODO: make configurable
    private final Queue<T> _queue = new ArrayDeque<>();

    @Loggable
    public static final class Add<T> implements Serializable {
        private static final long serialVersionUID = 1488907657178973318L;
        private final T _work;

        public T getWork() {
            return _work;
        }

        public Add(final T _work) {
            this._work = _work;
        }
    }

    @Loggable
    public static final class AddAccepted<T> implements Serializable {
        private static final long serialVersionUID = 782305435749351105L;
        private final T _work;

        public T getWork() {
            return _work;
        }

        public AddAccepted(final T _work) {
            this._work = _work;
        }
    }

    @Loggable
    public static final class AddRejected<T> implements Serializable {
        private static final long serialVersionUID = 7324573883451548747L;
        private final T _work;

        public T getWork() {
            return _work;
        }

        public AddRejected(final T _work) {
            this._work = _work;
        }
    }

    @Loggable
    public static final class Poll implements Serializable {
        private static final long serialVersionUID = 2405941961370915325L;
        private static final Poll _INSTANCE = new Poll();
        public static Poll getInstance() {
            return _INSTANCE;
        }
        private Poll() {}
    }

    @Loggable
    public static final class NoMoreWork implements Serializable {
        private static final long serialVersionUID = 909545623248537954L;
        private static final NoMoreWork _INSTANCE = new NoMoreWork();
        public static NoMoreWork getInstance() {
            return _INSTANCE;
        }
        private NoMoreWork() {}
    }
}
