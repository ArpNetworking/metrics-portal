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

package com.arpnetworking.metrics.portal.scheduling;

import com.arpnetworking.notcommons.serialization.Deserializer;
import com.arpnetworking.notcommons.serialization.Serializer;

/**
 * A type that allows for both serialization and deserialization of JobRefs.
 * <p>
 * Alongside a {@link JobMessageExtractor}, this can be used as a workaround
 * for the lack of dynamic props in Akka's classic cluster sharding.
 * <p>
 * See <a href=https://stackoverflow.com/a/26524666>this SO post</a> for more details. This is Option A.
 * <p>
 * This type is intended for use as a Guice binding type-token.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface JobRefSerializer extends Serializer<JobRef<?>>, Deserializer<JobRef<?>> {}
