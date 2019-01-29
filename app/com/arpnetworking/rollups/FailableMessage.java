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
package com.arpnetworking.rollups;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;

import java.io.Serializable;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Base class for all rollup messages that can also represent a failure.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public abstract class FailableMessage implements Serializable {

    /**
     * Protected constructor for subclasses to use by passing a subclass of Builder.
     *
     * @param builder subclass builder
     */
    protected FailableMessage(final Builder<?, ?> builder) {
        _failure = builder._failure;
        _throwable = builder._throwable;
    }

    public boolean isFailure() {
        return _failure;
    }

    @Nullable
    public Throwable getThrowable() {
        return _throwable;
    }

    private final boolean _failure;
    private final Throwable _throwable;
    private static final long serialVersionUID = -255159605861224426L;


    /**
     * {@code FailableMessage} builder static inner class.
     *
     * @param <B> subtype of this Builder
     * @param <T> subtype of FailableMessage
     */
    public abstract static class Builder<B extends Builder<?, T>, T extends FailableMessage> extends ThreadLocalBuilder<T> {

        /**
         * Extendable constructor.
         *
         * @param targetConstructor constructor of underlying class
         */
        protected Builder(final Function<B, T> targetConstructor) {
            super(targetConstructor);
        }

        /**
         * Method to return the instance of the subclass builder.
         *
         * @return an instance of the builder subclass
         */
        protected abstract B self();

        /**
         * Sets the {@code _failure} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _failure} to set
         * @return a reference to this Builder
         */
        public B setFailure(final boolean value) {
            _failure = value;
            return self();
        }

        /**
         * Sets the {@code _throwable} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _throwable} to set
         * @return a reference to this Builder
         */
        public B setThrowable(@Nullable final Throwable value) {
            _throwable = value;
            return self();
        }

        @Override
        protected void reset() {
            _failure = false;
            _throwable = null;
        }

        private boolean _failure = false;
        private Throwable _throwable;
    }
}
