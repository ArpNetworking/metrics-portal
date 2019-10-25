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
package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.types.ChromeTab;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.sf.oval.constraint.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Default implementation of {@link DevToolsFactory}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class DefaultDevToolsFactory implements DevToolsFactory {

    @Override
    public DevToolsService create(final boolean ignoreCertificateErrors) {
        final ChromeService service = _service.get();
        final ChromeTab tab = service.createTab();
        final com.github.kklisura.cdt.services.ChromeDevToolsService result = service.createDevToolsService(tab);
        if (ignoreCertificateErrors) {
            result.getSecurity().setIgnoreCertificateErrors(true);
        }
        return new DevToolsServiceWrapper(service, _originConfigs, tab, result, _executor, _networkConfigurationProtocol);
    }

    private DefaultDevToolsFactory(final Builder builder) {
        _chromePath = builder._config.getString("path");
        _chromeArgs = ImmutableMap.copyOf(builder._config.getObject("args").unwrapped());
        _executor = createExecutorService(builder._config.hasPath("executor")
                ? builder._config.getConfig("executor")
                : ConfigFactory.empty());
        _service = () -> builder._serviceFactory.getOrCreate(_chromePath, _chromeArgs);
        try {
            _originConfigs = builder._objectMapper.readValue(
                    ConfigurationHelper.toJson(builder._config, "originConfigs"),
                    PerOriginConfigs.class
            );
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
        _networkConfigurationProtocol = builder._config.hasPath("networkConfigurationProtocol")
                ? DevToolsNetworkConfigurationProtocol.valueOf(builder._config.getString("networkConfigurationProtocol"))
                : DevToolsNetworkConfigurationProtocol.FETCH;
    }

    @Override
    public PerOriginConfigs getOriginConfigs() {
        return _originConfigs;
    }

    private static ExecutorService createExecutorService(final Config config) {
        final Duration executorKeepAliveTime =
                config.hasPath("keepAlive")
                        ? Duration.parse(config.getString("keepAlive"))
                        : Duration.ofSeconds(1);
        final int corePoolSize = config.hasPath("corePoolSize") ? config.getInt("corePoolSize") : 8;
        final int maximumPoolSize = config.hasPath("maximumPoolSize") ? config.getInt("maximumPoolSize") : 8;
        final int queueSize = config.hasPath("queueSize") ? config.getInt("queueSize") : 1024;
        return new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                executorKeepAliveTime.toNanos(),
                TimeUnit.NANOSECONDS,
                new ArrayBlockingQueue<>(queueSize)
        );
    }

    private final String _chromePath;
    private final ImmutableMap<String, Object> _chromeArgs;
    private final ExecutorService _executor;
    private final Supplier<ChromeService> _service;
    private final PerOriginConfigs _originConfigs;
    private final DevToolsNetworkConfigurationProtocol _networkConfigurationProtocol;

    /**
     * Implementation of builder pattern for {@link DefaultDevToolsFactory}.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder extends OvalBuilder<DefaultDevToolsFactory> {
        @NotNull
        private Config _config;
        @NotNull
        private ObjectMapper _objectMapper = ObjectMapperFactory.createInstance();
        @NotNull
        private CachingChromeServiceFactory _serviceFactory = new CachingChromeServiceFactory();

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultDevToolsFactory::new);
        }

        /**
         * Sets config. Required. Cannot be null.
         *
         * @param config is a {@link Config} that has the following fields: <ul>
         *   <li>{@code path} points to the Chrome executable</li>
         *   <li>
         *     {@code args} is a map of command-line flags passed to Chrome.
         *     List of valid args: https://peter.sh/experiments/chromium-command-line-switches/
         *     Keys are flags without the leading {@code --};
         *     values are strings (for flags that take args) or {@code true} (for flags that don't).
         *     For example, {@code foo=true, bar="baz"} would result in the Chrome invocation {@code chromium --foo --bar=baz}.
         *   </li>
         *   <li>{@code executor} is a sub-object with the fields:
         *     <ul>
         *       <li>{@code corePoolSize} and {@code maximumPoolSize}, which map straightforwardly to
         *         {@link ThreadPoolExecutor#ThreadPoolExecutor} arguments;</li>
         *       <li>{@code keepAlive} is an ISO-8601 duration that maps straightforwardly to the same constructor;</li>
         *       <li>{@code queueSize} is how large the executor's queue should be before task-submissions start blocking.</li>
         *     </ul>
         *   </li>
         *   <li>{@code originConfigs} is a {@link PerOriginConfigs} object,
         *     describing each allowed origin's permissions/configuration.</li>
         *   </ul>
         * @return this builder
         *
         * TODO(spencerpearson): I don't like exposing the threadpool implementation details, but I can very easily imagine the user
         *   wanting to configure them.
         */
        public Builder setConfig(final Config config) {
            _config = config;
            return this;
        }

        /**
         * Sets object mapper. Optional. Cannot be null. Defaults to a new one.
         *
         * @param objectMapper is an {@link ObjectMapper} used to deserialize parts of the config.
         * @return this builder
         */
        public Builder setObjectMapper(final ObjectMapper objectMapper) {
            _objectMapper = objectMapper;
            return this;
        }

        /**
         * Sets service factory. Optional. Cannot be null. Defaults to a new one.
         *
         * @param serviceFactory is used to create Chrome service instances.
         * @return this builder
         */
        public Builder setServiceFactory(final CachingChromeServiceFactory serviceFactory) {
            _serviceFactory = serviceFactory;
            return this;
        }
    }
}
