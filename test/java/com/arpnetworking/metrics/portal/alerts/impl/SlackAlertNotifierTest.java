/*
 * Copyright 2023 Inscope Metrics Inc
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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.MetricsQueryFormat;
import models.internal.Organization;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultAlertEvaluationResult;
import models.internal.impl.DefaultMetricsQuery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for {@link SlackAlertNotifier}.
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class SlackAlertNotifierTest {
    private Alert _alert;
    private AlertEvaluationResult _result;
    private WSClient _wsClient;
    private WSRequest _wsRequestMock;
    private ArgumentCaptor<JsonNode> _postCaptor;

    @Before
    public void setUp() {
        final Organization organization = TestBeanFactory.createOrganization();
        final UUID id = UUID.randomUUID();
        _alert = new DefaultAlert.Builder()
                .setId(id)
                .setOrganization(organization)
                .setEnabled(true)
                .setName("TestAlert")
                .setDescription("Used in a test.")
                .setQuery(
                        new DefaultMetricsQuery.Builder()
                                .setQuery("This query is invalid but never evaluated")
                                .setFormat(MetricsQueryFormat.KAIROS_DB)
                                .build()
                )
                .build();
        _result = new DefaultAlertEvaluationResult.Builder()
                .setSeriesName(_alert.getName())
                .setFiringTags(ImmutableList.of())
                .setGroupBys(ImmutableList.of("tag"))
                .setQueryStartTime(Instant.parse("2020-08-03T10:00:00Z"))
                .setQueryEndTime(Instant.parse("2020-08-03T11:00:00Z"))
                .build();
        _wsClient = Mockito.mock(WSClient.class);

        _wsRequestMock = Mockito.mock(WSRequest.class, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SMART_NULLS));
        Mockito.when(_wsClient.url(any())).thenReturn(_wsRequestMock);

        Mockito.when(_wsRequestMock.addHeader(any(), any())).thenReturn(_wsRequestMock);
        final WSResponse response = Mockito.mock(WSResponse.class);
        Mockito.when(response.getStatus()).thenReturn(200);
        _postCaptor = ArgumentCaptor.forClass(JsonNode.class);
        Mockito.when(_wsRequestMock.post(_postCaptor.capture())).thenReturn(CompletableFuture.completedFuture(response));
    }

    @Test
    public void testUsesSpecifiedUrl() {
        final String postUrl = "https://example.com";
        final SlackAlertNotifier notifier = new SlackAlertNotifier.Builder()
                .setMessagePostUrl(postUrl)
                .setWSClient(_wsClient)
                .setDefaultChannelId("123asdi")
                .setApiKey("notarealapikey")
                .build();
        notifier.notify(_alert, _result);
        Mockito.verify(_wsClient).url(eq(postUrl));
        Mockito.verify(_wsRequestMock).post(Mockito.any(JsonNode.class));
    }

    @Test
    public void testUsesSpecifiedApiKey() {
        final String postUrl = "https://example.com";
        final SlackAlertNotifier notifier = new SlackAlertNotifier.Builder()
                .setMessagePostUrl(postUrl)
                .setWSClient(_wsClient)
                .setDefaultChannelId("123asdi")
                .setApiKey("notarealapikey")
                .build();
        notifier.notify(_alert, _result);
        Mockito.verify(_wsRequestMock).addHeader(eq("Authorization"), eq("Bearer notarealapikey"));
        Mockito.verify(_wsRequestMock).post(Mockito.any(JsonNode.class));
    }

    @Test
    public void testRespectsChannelMap() {
        final String postUrl = "https://example.com";
        final SlackAlertNotifier notifier = new SlackAlertNotifier.Builder()
                .setMessagePostUrl(postUrl)
                .setWSClient(_wsClient)
                .setDefaultChannelId("123asdi")
                .setApiKey("notarealapikey")
                .setChannelIdMap(ImmutableMap.of(_alert.getName(), "alertspecificchannel"))
                .build();
        notifier.notify(_alert, _result);
        Mockito.verify(_wsRequestMock).addHeader(eq("Authorization"), eq("Bearer notarealapikey"));

        final JsonNode value = _postCaptor.getValue();
        Assert.assertThat(value.get("channel").asText(), org.hamcrest.Matchers.equalTo("alertspecificchannel"));
    }

    @Test
    public void testFallsBackToDefaultChannel() {
        final String postUrl = "https://example.com";
        final SlackAlertNotifier notifier = new SlackAlertNotifier.Builder()
                .setMessagePostUrl(postUrl)
                .setWSClient(_wsClient)
                .setDefaultChannelId("123asdi")
                .setApiKey("notarealapikey")
                .setChannelIdMap(ImmutableMap.of(_alert.getName() + "2", "alertspecificchannel"))
                .build();
        notifier.notify(_alert, _result);
        Mockito.verify(_wsRequestMock).addHeader(eq("Authorization"), eq("Bearer notarealapikey"));

        final JsonNode value = _postCaptor.getValue();
        Assert.assertThat(value.get("channel").asText(), org.hamcrest.Matchers.equalTo("123asdi"));
    }
}
