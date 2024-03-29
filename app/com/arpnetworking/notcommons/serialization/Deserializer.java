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

package com.arpnetworking.notcommons.serialization;

/**
 * An object that can deserialize a structured value from a string.
 *
 * @param <T> the output type
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface Deserializer<T> {
    /**
     * Deserialize a value from the given string.
     *
     * @param value the string to deserialize
     * @return the value stored within the string
     * @throws DeserializationException if unable to deserialize
     */
    T deserialize(String value) throws DeserializationException;
}
