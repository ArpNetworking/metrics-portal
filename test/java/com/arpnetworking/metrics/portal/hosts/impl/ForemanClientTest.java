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
import com.google.common.io.Resources;
import org.junit.Test;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ForemanClient}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class ForemanClientTest {

    @Test
    public void testHostsResponseFromForeman1dot8() throws IOException, ExecutionException, InterruptedException {
        final WSClient client = mock(WSClient.class);
        final WSRequest request = mock(WSRequest.class);
        final WSResponse response = mock(WSResponse.class);
        final CompletionStage<WSResponse> responseFuture = CompletableFuture.completedFuture(response);

        doReturn(request).when(client).url(BASE_URI + "/api/hosts?page=1&per_page=250");
        doReturn(responseFuture).when(request).get();
        doReturn(200).when(response).getStatus();
        doReturn(
                Resources.toString(
                        Resources.getResource(this.getClass(), "foreman_v1.8_hosts_response.json"),
                        Charsets.UTF_8))
                .when(response)
                .getBody();

        final ForemanClient foremanClient = new ForemanClient.Builder()
                .setClient(client)
                .setBaseUrl(BASE_URI)
                .build();

        final CompletionStage<ForemanClient.HostPageResponse> futureHostPageResponse = foremanClient.getHostPage(1);
        final ForemanClient.HostPageResponse hostPageResponse = futureHostPageResponse.toCompletableFuture().get();
        assertEquals(1, hostPageResponse.getPage());
        assertEquals(3, hostPageResponse.getPerPage());
        assertEquals(3, hostPageResponse.getSubtotal());
        assertEquals(3, hostPageResponse.getTotal());
        assertEquals(3, hostPageResponse.getResults().size());
        assertEquals("host1.example.com", hostPageResponse.getResults().get(0).getName());
        assertEquals("host2.example.com", hostPageResponse.getResults().get(1).getName());
        assertEquals("host3.example.com", hostPageResponse.getResults().get(2).getName());
    }

    private static final URI BASE_URI = URI.create("https://foreman.example.com");
}
