/*
 * Copyright 2019 Dropbox, Inc
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
package com.arpnetworking.kairos.client.models;

import com.arpnetworking.commons.test.EnumerationTestHelper;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

/**
 * Tests for {@link TimeUnit}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class TimeUnitTest {

    @Test
    public void test() throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        EnumerationTestHelper.testValueOf(TimeUnit.class);
    }
}
