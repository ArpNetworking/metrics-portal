/**
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

import java.time.Instant;
import java.util.Optional;

/**
 * Internal model interface for a VersionSetLookupResult; the reification of the various possible results of a lookup of a version set.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
public interface VersionSetLookupResult {

    /**
     * Accessor for the VersionSet.
     *
     * @return the an optional version set; the version set will be present if the lookup resulted in a version set and
     * <code>isNotModified()</code> is false; a version set MAY be present if <code>isNotModified()</code> is true.
     */
    Optional<VersionSet> getVersionSet();

    /**
     * Accessor for the 'is not modified' result for the lookup.
     *
     * @return true if the lookup found only data older than the implied e.g. max-age or if-modified-since datetime; otherwise false.
     */
    boolean isNotModified();

    /**
     * Accessor for the 'is not found' result for the lookup; otherwise false.
     *
     * @return true if no version set was found; otherwise false.
     */
    boolean isNotFound();

    /**
     * Accessor for the datetime of last modification of the underlying data. Will be present if and only if the <code>VersionSet</code>
     * is present.
     *
     * @return the datetime of last modification of the underlying data.
     */
    Optional<Instant> getLastModified();
}
