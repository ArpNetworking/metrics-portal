/*
 * Copyright 2016 Inscope Metrics Inc.
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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.junit.Test;
import org.mockito.Mockito;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

/**
 * Tests for <code>ConsulClient</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class ConsulClientTest {

    @Test
    public void testCatalogNodes() throws IOException, ExecutionException, InterruptedException {
        final WSClient client = Mockito.mock(WSClient.class);
        final WSRequest request = Mockito.mock(WSRequest.class);
        final WSResponse response = Mockito.mock(WSResponse.class);
        final CompletionStage<WSResponse> responseFuture = CompletableFuture.completedFuture(response);

        Mockito.doReturn(request).when(client).url(BASE_URI + "/v1/catalog/nodes");
        Mockito.doReturn(responseFuture).when(request).get();
        Mockito.doReturn(200).when(response).getStatus();
        Mockito.doReturn(
                Resources.toString(
                        Resources.getResource(this.getClass(), "consul_v0.6_catalog_nodes.json"),
                        Charsets.UTF_8))
                .when(response)
                .getBody();

        final ConsulClient consulClient = new ConsulClient.Builder()
                .setClient(client)
                .setBaseUrl(BASE_URI)
                .build();

        final CompletionStage<ImmutableList<ConsulClient.Host>> futureHostList = consulClient.getHostList();
        final List<ConsulClient.Host> hostList = futureHostList.toCompletableFuture().get();
        assertEquals(3, hostList.size());
        assertEquals("host1.example.com", hostList.get(0).getNode());
        assertEquals("10.1.0.1", hostList.get(0).getAddress());
        assertEquals("host2.example.com", hostList.get(1).getNode());
        assertEquals("10.1.0.12", hostList.get(1).getAddress());
        assertEquals("host3.example.com", hostList.get(2).getNode());
        assertEquals("10.1.0.18", hostList.get(2).getAddress());
    }

    private static final URI BASE_URI = URI.create("https://consul.example.com");
}
