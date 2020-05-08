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
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;

/**
 * An actor with a bounded queue.
 *
 * @param <T>
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class QueueActor<T extends Serializable> extends AbstractActor {
    /**
     * Creates a {@link Props} for this actor.
     *
     * @param maxSize The maximum size for the queue (if any).
     * @return A new Props.
     */
    public static Props props(final Optional<Long> maxSize) {
        return Props.create(QueueActor.class, maxSize);
    }

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .match(Add.class, message -> {
                    final T item;
                    try {
                        item = (T) message.getItem();
                    } catch (final ClassCastException err) {
                        getSender().tell(new AddRejected<>(message.getItem()), getSelf());
                        return;
                    }
                    if (_maxSize.isPresent() && _queue.size() < _maxSize.get()) {
                        _queue.add(item);
                        getSender().tell(new AddAccepted<>(item), getSelf());
                    } else {
                        getSender().tell(new AddRejected<>(item), getSelf());
                    }
                })
                .match(Poll.class, request -> {
                    getSender().tell(
                            Optional.ofNullable((Object) _queue.poll())
                                    .orElse(QueueEmpty.getInstance()),
                            getSelf()
                    );
                })
                .build();
    }

    /**
     * {@link QueueActor} actor constructor.
     *
     * @param maxSize the maximum size of the queue
     */
    public QueueActor(final Optional<Long> maxSize) {
        _maxSize = maxSize;
    }

    private final Optional<Long> _maxSize;
    private final Queue<T> _queue = new ArrayDeque<>();

    /**
     * Request that an item be added to the queue.
     *
     * @param <T> the type of item being added
     */
    @Loggable
    public static final class Add<T> implements Serializable {
        private static final long serialVersionUID = 1488907657178973318L;
        private final T _item;

        public T getItem() {
            return _item;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Add<?> that = (Add<?>) o;
            return _item.equals(that._item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_item);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_item", _item)
                    .toString();
        }

        /**
         * Public constructor.
         * @param item the item to enqueue
         */
        public Add(final T item) {
            this._item = item;
        }
    }

    /**
     * Response to {@link Add}, indicating that the actor has accepted the submission.
     *
     * @param <T> the type of item being added
     */
    @Loggable
    public static final class AddAccepted<T> implements Serializable {
        private static final long serialVersionUID = 782305435749351105L;
        private final T _item;

        public T getItem() {
            return _item;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final AddAccepted<?> that = (AddAccepted<?>) o;
            return _item.equals(that._item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_item);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_item", _item)
                    .toString();
        }

        /**
         * Public constructor.
         * @param item the item that was accepted
         */
        public AddAccepted(final T item) {
            this._item = item;
        }
    }

    /**
     * Response to {@link Add}, indicating that the actor has refused the submission.
     *
     * @param <T> the type of item being added
     */
    @Loggable
    public static final class AddRejected<T> implements Serializable {
        private static final long serialVersionUID = 7324573883451548747L;
        private final T _item;

        public T getItem() {
            return _item;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final AddRejected<?> that = (AddRejected<?>) o;
            return _item.equals(that._item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_item);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_item", _item)
                    .toString();
        }

        /**
         * Public constructor.
         * @param item the item that was rejected
         */
        public AddRejected(final T item) {
            this._item = item;
        }
    }

    /**
     * Requests an item from the queue.
     */
    @Loggable
    public static final class Poll implements Serializable {
        private static final long serialVersionUID = 2405941961370915325L;
        private static final Poll INSTANCE = new Poll();
        public static Poll getInstance() {
            return INSTANCE;
        }
        private Poll() {}
    }

    /**
     * Response to {@link Poll} indicating that the queue is empty.
     */
    @Loggable
    public static final class QueueEmpty implements Serializable {
        private static final long serialVersionUID = 909545623248537954L;
        private static final QueueEmpty INSTANCE = new QueueEmpty();
        public static QueueEmpty getInstance() {
            return INSTANCE;
        }
        private QueueEmpty() {}
    }
}
