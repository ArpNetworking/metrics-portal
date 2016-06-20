/**
 * Copyright 2015 Groupon.com
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
package com.arpnetworking.metrics.portal.hosts.impl;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Tests for <code>DatabaseHostRepository</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class DatabaseHostRepositoryTest {

    @Test
    public void testPostgresqlHostQueryGeneratorTokenize() {
        List<String> tokens;

        tokens = DatabaseHostRepository.PostgresqlHostQueryGenerator.tokenize("test-app1.snc1");
        Assert.assertArrayEquals("Actual = " + tokens, new String[] {"test", "app", "1", "snc", "1"}, tokens.toArray());

        tokens = DatabaseHostRepository.PostgresqlHostQueryGenerator.tokenize("a1--b2  c3- d4..host1234app");
        Assert.assertArrayEquals("Actual = " + tokens, new String[]{"a", "1", "b", "2", "c", "3", "d", "4", "host", "1234", "app"}, tokens.toArray());

        tokens = DatabaseHostRepository.PostgresqlHostQueryGenerator.tokenize("");
        Assert.assertArrayEquals("Actual = " + tokens, new String[]{}, tokens.toArray());

        tokens = DatabaseHostRepository.PostgresqlHostQueryGenerator.tokenize("  ");
        Assert.assertArrayEquals("Actual = " + tokens, new String[]{}, tokens.toArray());
    }
}
