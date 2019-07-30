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

import java.io.Serializable;

/**
 * Message class used to signify that no more rollups are available.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class NoMoreRollups implements Serializable {

    private static final long serialVersionUID = -3503619526731721351L;
    private static final NoMoreRollups THE_INSTANCE = new NoMoreRollups();

    /**
     * Creates a NoMoreRollups instance.
     */
    private NoMoreRollups() {
        // Protected against negative times.
    }

    public static NoMoreRollups getInstance() {
        return THE_INSTANCE;
    }
}
