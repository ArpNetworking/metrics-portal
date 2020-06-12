/*
 * Copyright 2020 Dropbox Inc.
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

import com.google.common.collect.ImmutableMultimap;
import org.junit.Test;

import java.util.function.Supplier;

/**
 * Test cases for {@link ConsistencyChecker}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class TagNamesMessageTest {

    private static final Supplier<TagNamesMessage.Builder> BUILDER = () -> new TagNamesMessage.Builder()
            .setMetricName("foo")
            .setTags(ImmutableMultimap.of("key", "value"))
            .setFailure(new RuntimeException());

    @Test
    public void testBuilderReset() throws Exception {
        com.arpnetworking.commons.test.ThreadLocalBuildableTestHelper.testReset(BUILDER.get());
    }
}
