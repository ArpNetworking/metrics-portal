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

package com.arpnetworking.notcommons.tagger;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableMap;

/**
 * A tagger provides a method of promoting metrics metadata into tags that can
 * then be used within instrumentation. This can be useful for extracting only
 * a portion of the metadata, such as specific tags or a subset of the metric name.
 *
 * A tagger is not required to use all the information it is given.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        property = "type"
)
public interface Tagger {
    /**
     * Return the tags to apply.
     *
     * @param metricName the metric name associated with this operation.
     * @return the tags.
     */
    ImmutableMap<String, String> getTags(String metricName);
}
