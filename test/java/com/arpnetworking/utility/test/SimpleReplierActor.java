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
package com.arpnetworking.utility.test;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;

/**
 * Actor implementation that will always return a static value for every message.
 *
 * @param <V> The type of the value.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class SimpleReplierActor<V> extends AbstractActor {
    /**
     * Gets a {@link Props} that will create a {@link SimpleReplierActor} that returns
     * the given value.
     *
     * @param value the value the {@link SimpleReplierActor} should return
     * @return a {@link Props}
     * @param <V> type of the value
     */
    public static <V> Props props(final V value) {
        return Props.create(SimpleReplierActor.class, () -> new SimpleReplierActor<>(value));
    }

    /**
     * Public constructor.
     *
     * @param value the value to always return
     */
    public SimpleReplierActor(final V value) {
        _value = value;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().matchAny(msg -> sender().tell(_value, self())).build();
    }

    private final V _value;
}
