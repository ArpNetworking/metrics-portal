/*
 * Copyright 2016 Groupon.com
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
package models.internal;

import java.util.List;
import java.util.UUID;

/**
 * Internal model interface for a VersionSpecification.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
public interface VersionSpecification {

    /**
     * Accessor for the URI.
     *
     * @return the URI.
     */
    UUID getUuid();

    /**
     * Accesspr for the version set.
     *
     * @return the version set.
     */
    VersionSet getVersionSet();

    /**
     * Accessor for the list of version specification attributes.
     *
     * @return the list of version specification attributes.
     */
    List<VersionSpecificationAttribute> getVersionSpecificationAttributes();

    /**
     * Accessor for the position of this version specification in the global list of version specifications.
     *
     * @return the position.
     */
    long getPosition();
}
