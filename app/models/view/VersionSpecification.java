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
package models.view;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;

import java.util.Map;
import java.util.UUID;

/**
 * View model for a version specification.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
@Loggable
public final class VersionSpecification {

    public Map<String, String> getTags() {
        return _tags;
    }

    public void setTags(final Map<String, String> value) {
        _tags = value;
    }

    public UUID getVersionSetId() {
        return _versionSetId;
    }

    public void setVersionSetId(final UUID value) {
        _versionSetId = value;
    }

    public long getPosition() {
        return _position;
    }

    public void setPosition(final long value) {
        _position = value;
    }

    public UUID getUuid() {
        return _uuid;
    }

    public void setUuid(final UUID value) {
        _uuid = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Uuid", _uuid)
                .add("Tags", _tags)
                .add("VersionSetId", _versionSetId)
                .add("Position", _position)
                .toString();
    }

    private UUID _uuid;
    private Map<String, String> _tags;
    private UUID _versionSetId;
    private long _position;
}
