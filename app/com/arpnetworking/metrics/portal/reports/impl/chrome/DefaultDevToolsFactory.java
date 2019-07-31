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

import com.github.kklisura.cdt.launch.ChromeArguments;
import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.launch.config.ChromeLauncherConfiguration;
import com.github.kklisura.cdt.launch.support.impl.ProcessLauncherImpl;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.types.ChromeTab;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

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
        return new DevToolsServiceWrapper(service, tab, result, _executor);
    }

    /**
     * Public constructor.
     *
     * @param config is a config that has the following fields: <ul>
     *   <li>{@code path} points to the Chrome executable</li>
     *   <li>
     *     {@code args} is a map of command-line flags passed to Chrome.
     *     List of valid args: https://peter.sh/experiments/chromium-command-line-switches/
     *     Keys are flags without the leading {@code --};
     *     values are strings (for flags that take args) or {@code true} (for flags that don't).
     *     For example, {@code foo=true, bar="baz"} would result in the Chrome invocation {@code chromium --foo --bar=baz}.
     *   </li>
     *   <li>{@code executor} is a sub-object with the fields: <ul>
     *     <li>{@code corePoolSize} and {@code maximumPoolSize}, which map straightforwardly to
     *       {@link ThreadPoolExecutor#ThreadPoolExecutor} arguments;</li>
     *     <li>{@code keepAlive} is an ISO-8601 duration that maps straightforwardly to the same constructor;</li>
     *     <li>{@code queueSize} is how large the executor's queue should be before task-submissions start blocking.</li>
     *     </ul>
     *   </ul>
     *
     * TODO(spencerpearson): I don't like exposing the threadpool implementation details, but I can very easily imagine the user
     *   wanting to configure them.
     */
    @Inject
    public DefaultDevToolsFactory(final Config config) {
        _chromePath = config.getString("path");
        _chromeArgs = ImmutableMap.copyOf(config.getObject("args").unwrapped());
        _executor = createExecutorService(config.hasPath("executor") ? config.getConfig("executor") : ConfigFactory.empty());
        _service = Suppliers.memoize(this::createService);
    }

    private ChromeService createService() {
        // The config should be able to override the CHROME_PATH environment variable that ChromeLauncher uses.
        // This requires in our own custom "environment" (since it defaults to using System::getEnv).
        final ImmutableMap<String, String> env = ImmutableMap.of(
                ChromeLauncher.ENV_CHROME_PATH, _chromePath
        );
        // ^^^ In order to pass this environment in, we need to use a many-argument constructor,
        //   which doesn't have obvious default values. So I stole the arguments from the fewer-argument constructor:
        // CHECKSTYLE.OFF: LineLength
        //   https://github.com/kklisura/chrome-devtools-java-client/blob/master/cdt-java-client/src/main/java/com/github/kklisura/cdt/launch/ChromeLauncher.java#L105
        // CHECKSTYLE.ON: LineLength
        final ChromeLauncher launcher = new ChromeLauncher(
                new ProcessLauncherImpl(),
                env::get,
                new ChromeLauncher.RuntimeShutdownHookRegistry(),
                new ChromeLauncherConfiguration()
        );
        return launcher.launch(ChromeArguments.defaults(true)
                .additionalArguments(_chromeArgs)
                .build());

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
}
