/*
 * Copyright 2014 Groupon.com
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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.logback.annotations.LogValue;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.LogValueMapFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.MetricsSoftwareState;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultHost;
import models.internal.impl.DefaultHostQuery;
import models.internal.impl.DefaultQueryResult;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import play.Environment;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of <code>HostRepository</code> using Elastic Search.
 *
 * @author Ruchita Venugopal (rvenugopal at groupon dot com)
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class ElasticSearchHostRepository implements HostRepository {

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's <code>Configuration</code>.
     * @param environment Instance of Play <code>Environment</code>.
     */
    @Inject
    public ElasticSearchHostRepository(final Config configuration, final Environment environment) {
        // For more information about these settings please see:
        //
        // Elastic Search Configuration:
        // http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/setup-configuration.html
        //
        // Index Settings:
        // http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/indices-update-settings.html
        this(buildNodeSettings(configuration, environment), buildIndexSettings(configuration));
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening host repository").log();

        // Initialize Elastic Search
        _node = new NodeBuilder()
                .loadConfigSettings(false)
                .settings(_settings)
                .build();
        _node.start();

        _client = _node.client();
        _client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();

        final ClusterStateResponse response = _client.admin().cluster().prepareState().execute().actionGet();
        final boolean hasIndex = response.getState().metaData().hasIndex(INDEX);
        if (!hasIndex) {
            _client.admin().indices().create(
                    Requests.createIndexRequest(INDEX)
                            .settings(_indexSettings)
                            .mapping(
                                    TYPE,
                                    "{\n"
                                            + "    \"properties\" : {\n"
                                            + "        \"hostname\" : {\n"
                                            + "            \"type\" : \"string\",\n"
                                            + "            \"store\" : true,\n"
                                            + "            \"fields\": {\n"
                                            + "                \"raw\": {\n"
                                            + "                    \"type\":  \"string\",\n"
                                            + "                    \"index\": \"not_analyzed\"\n"
                                            + "                }\n"
                                            + "            }\n"
                                            + "        },\n"
                                            + "        \"metricsSoftwareVersion\" : {\n"
                                            + "            \"type\" : \"string\", \n"
                                            + "            \"store\" : true\n"
                                            + "        },\n"
                                            + "        \"metricsSoftwareSha\" : {\n"
                                            + "            \"type\" : \"string\", \n"
                                            + "            \"store\" : true\n"
                                            + "        },\n"
                                            + "        \"organization\" : {\n"
                                            + "            \"type\" : \"string\", \n"
                                            + "            \"store\" : true\n"
                                            + "        },\n"
                                            + "        \"metricsSoftwareState\" : {\n"
                                            + "            \"type\" : \"string\", \n"
                                            + "            \"store\" : true\n"
                                            + "        },\n"
                                            + "        \"cluster\" : {\n"
                                            + "            \"type\" : \"string\",\n"
                                            + "            \"store\": true\n"
                                            + "        }\n"
                                            + "    }\n"
                                            + "}")
                            ).actionGet();

            _client.admin().cluster().health(new ClusterHealthRequest(INDEX).waitForGreenStatus()).actionGet();
        }

        _isOpen.set(true);
        LOGGER.info().setMessage("ElasticSearchHostRepository up and healthy").log();
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing host repository").log();
        _isOpen.set(false);

        // Shutdown Elastic Search
        _client.close();
        _node.close();
    }

    @Override
    public Optional<Host> getHost(final String hostname, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();

        final QueryBuilder esQuery = QueryBuilders.matchQuery("hostname", hostname);
        final SearchRequestBuilder request = _client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .setQuery(esQuery)
                .setSize(1);

        final Iterator<? extends Host> iterator =
                deserializeHits(request.execute().actionGet(), organization).values().iterator();
        if (iterator.hasNext()) {
            return Optional.of(iterator.next());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void addOrUpdateHost(final Host host, final Organization organization) {
        //TODO(barp): Support organizational separation in ElasticSearch [?]
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Adding or updating host")
                .addData("host", host)
                .addData("organization", organization)
                .log();

        final String hostJson;
        try {
            hostJson = OBJECT_MAPPER.writeValueAsString(host);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(String.format("Unable to serialize host; host=%s", host), e);
        }

        final IndexRequest indexRequest = new IndexRequest(INDEX, TYPE, host.getHostname())
                .source(hostJson);

        final UpdateRequest updateRequest = new UpdateRequest(INDEX, TYPE, host.getHostname())
                .doc(hostJson)
                .upsert(indexRequest);

        final UpdateResponse response = _client.update(updateRequest).actionGet();
        LOGGER.info()
                .setMessage("Upserted host")
                .addData("host", host)
                .addData("organization", organization)
                .addData("isCreated", response.isCreated())
                .log();
    }

    @Override
    public void deleteHost(final String hostname, final Organization organization) {
        //TODO(barp): Support organizational separation in ElasticSearch [?]
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Deleting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();

        final DeleteResponse response = _client.prepareDelete(INDEX, TYPE, hostname)
                .setRefresh(true)
                .execute()
                .actionGet();
        if (response.isFound()) {
            LOGGER.info()
                    .setMessage("Deleted host")
                    .addData("hostname", hostname)
                    .addData("organization", organization)
                    .log();
        } else {
            LOGGER.info()
                    .setMessage("Host not found")
                    .addData("hostname", hostname)
                    .addData("organization", organization)
                    .log();
        }
    }

    @Override
    public HostQuery createQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultHostQuery(this, organization);
    }

    @Override
    public QueryResult<Host> query(final HostQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();
        final Organization organization = query.getOrganization();

        QueryBuilder esQuery = null;
        if (query.getPartialHostname().isPresent()) {
            esQuery = QueryBuilders.matchPhrasePrefixQuery("hostname", query.getPartialHostname().get()).maxExpansions(MAX_EXPANSIONS);
        }
        if (query.getMetricsSoftwareState().isPresent()) {
            final QueryBuilder queryState = QueryBuilders.matchQuery("metricsSoftwareState", query.getMetricsSoftwareState().get());
            esQuery = esQuery == null ? queryState : QueryBuilders.boolQuery().must(esQuery).must(queryState);
        }
        if (query.getCluster().isPresent()) {
            final QueryBuilder queryState = QueryBuilders.matchQuery("cluster", query.getCluster().get());
            esQuery = esQuery == null ? queryState : QueryBuilders.boolQuery().must(esQuery).must(queryState);
        }

        final SearchRequestBuilder request = _client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        if (esQuery != null) {
            request.setQuery(esQuery);
        }
        if (query.getSortBy().isPresent()) {
            request.addSort(mapField(query.getSortBy().get()), SortOrder.ASC);
        } else {
            request.addSort(new ScoreSortBuilder());
        }
        if (query.getOffset().isPresent()) {
            request.setFrom(query.getOffset().get());
        }
        request.setSize(query.getLimit());

        return deserializeHits(request.execute().actionGet(), organization);
    }

    @Override
    public long getHostCount(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting host count")
                .addData("organization", organization)
                .log();

        final CountResponse response = _client.prepareCount(INDEX)
                .execute()
                .actionGet();
        return response.getCount();
    }

    @Override
    public long getHostCount(final MetricsSoftwareState metricsSoftwareState, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting host count in state")
                .addData("state", metricsSoftwareState)
                .addData("organization", organization)
                .log();

        final QueryBuilder queryState = QueryBuilders.matchQuery("metricsSoftwareState", metricsSoftwareState.toString());

        final CountResponse response = _client.prepareCount(INDEX)
                .setQuery(queryState)
                .execute()
                .actionGet();
        return response.getCount();
    }

    /**
     * Generate a Steno log compatible representation.
     *
     * @return Steno log compatible representation.
     */
    @LogValue
    public Object toLogValue() {
        return LogValueMapFactory.builder(this)
                .put("client", _client)
                .put("isOpen", _isOpen)
                .build();
    }

    @Override
    public String toString() {
        return toLogValue().toString();
    }

    private String mapField(final HostQuery.Field field) {
        switch (field) {
            case HOSTNAME:
                return "hostname.raw";
            case METRICS_SOFTWARE_STATE:
                return "metricsSoftwareState";
            default:
                throw new UnsupportedOperationException(String.format("Unrecognized field; field=%s", field));
        }
    }

    private QueryResult<Host> deserializeHits(final SearchResponse response, final Organization organization) {
        final List<Host> hosts = Lists.newArrayList();
        for (final SearchHit hit : response.getHits().hits()) {
            try {
                hosts.add(OBJECT_MAPPER.readValue(hit.getSourceAsString(), DefaultHost.class));
            } catch (final IOException e) {
                LOGGER.error()
                        .setMessage("Unable to deserialize host")
                        .addData("json", hit.getSourceAsString())
                        .addData("organization", organization)
                        .setThrowable(e)
                        .log();
                LOGGER.warn()
                        .setMessage("Deleting malformed host")
                        .addData("id", hit.id())
                        .addData("organization", organization)
                        .log();
                deleteHost(hit.getId(), organization);
            }
        }
        return new DefaultQueryResult<>(hosts, response.getHits().getTotalHits());
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Host repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    /*package private*/ ElasticSearchHostRepository(final Settings settings, final Settings indexSettings) {
        _settings = settings;
        _indexSettings = indexSettings;
    }

    private static Settings buildIndexSettings(final Config configuration) {
        return ImmutableSettings.settingsBuilder()
                .put("number_of_shards", configuration.getString("elasticSearch.index.hosts.shards"))
                .put("number_of_replicas", configuration.getString("elasticSearch.index.hosts.replicas"))
                .put("refresh_interval", configuration.getString("elasticSearch.index.hosts.refresh"))
                .build();
    }

    private static Settings buildNodeSettings(final Config configuration, final Environment environment) {
        return ImmutableSettings.settingsBuilder()
                .put("cluster.name", configuration.getString("elasticSearch.cluster.name"))
                .put("node.local", configuration.getString("elasticSearch.node.local"))
                .put("node.data", configuration.getString("elasticSearch.node.data"))
                .put("path.logs", ConfigurationHelper.getFile(configuration, "elasticSearch.path.logs", environment).getAbsolutePath())
                .put("path.data", ConfigurationHelper.getFile(configuration, "elasticSearch.path.data", environment).getAbsolutePath())
                .put("discovery.zen.ping.unicast.hosts", configuration.getString("elasticSearch.discovery.zen.ping.unicast.hosts"))
                .put("discovery.zen.minimum_master_nodes", configuration.getInt("elasticSearch.discovery.zen.minimum_master_nodes"))
                .build();
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final Settings _settings;
    private final Settings _indexSettings;
    private Client _client;
    private Node _node;

    private static final String INDEX = "hosts";
    private static final String TYPE = "host";
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchHostRepository.class);
    private static final int MAX_EXPANSIONS = 10000;
}


