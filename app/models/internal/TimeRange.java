/*
 * Copyright 2019 Dropbox, Inc.
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

import com.google.common.base.MoreObjects;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an interval of time.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class TimeRange {
    private final Instant _start;
    private final Instant _end;

    public Instant getStart() {
        return _start;
    }

    public Instant getEnd() {
        return _end;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_start", _start)
                .add("_end", _end)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeRange)) {
            return false;
        }
        final TimeRange that = (TimeRange) o;
        return _start.equals(that._start)
                && _end.equals(that._end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_start, _end);
    }

    /**
     * Public constructor.
     *
     * @param start The start of the time interval.
     * @param end The end of the time interval.
     */
    public TimeRange(final Instant start, final Instant end) {
        _start = start;
        _end = end;
    }
}
