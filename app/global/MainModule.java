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
package global;

import actors.JvmMetricsCollector;
import actors.NoopActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.routing.ClusterRouterPool;
import akka.cluster.routing.ClusterRouterPoolSettings;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import akka.routing.ConsistentHashingPool;
import com.arpnetworking.commons.akka.GuiceActorCreator;
import com.arpnetworking.commons.akka.ParallelLeastShardAllocationStrategy;
import com.arpnetworking.commons.jackson.databind.EnumerationDeserializer;
import com.arpnetworking.commons.jackson.databind.EnumerationDeserializerStrategyUsingToUpperCase;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.commons.jackson.databind.module.akka.AkkaModule;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.KairosDbClientImpl;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.config.MetricsQueryConfig;
import com.arpnetworking.kairos.config.MetricsQueryConfigImpl;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.impl.ApacheHttpSink;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.incubator.impl.TsdPeriodicMetrics;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.health.ClusterStatusCacheActor;
import com.arpnetworking.metrics.portal.health.HealthProvider;
import com.arpnetworking.metrics.portal.health.StatusActor;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.metrics.portal.hosts.impl.HostProviderFactory;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.query.QueryExecutorRegistry;
import com.arpnetworking.metrics.portal.reports.ReportExecutionContext;
import com.arpnetworking.metrics.portal.reports.ReportRepository;
import com.arpnetworking.metrics.portal.reports.impl.DatabaseReportExecutionRepository;
import com.arpnetworking.metrics.portal.reports.impl.chrome.DefaultDevToolsFactory;
import com.arpnetworking.metrics.portal.reports.impl.chrome.DevToolsFactory;
import com.arpnetworking.metrics.portal.scheduling.JobCoordinator;
import com.arpnetworking.metrics.portal.scheduling.JobExecutorActor;
import com.arpnetworking.metrics.portal.scheduling.JobMessageExtractor;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.rollups.MetricsDiscovery;
import com.arpnetworking.rollups.RollupExecutor;
import com.arpnetworking.rollups.RollupForwarder;
import com.arpnetworking.rollups.RollupGenerator;
import com.arpnetworking.rollups.RollupManager;
import com.arpnetworking.utility.ConfigTypedProvider;
import com.arpnetworking.utility.ConfigurationOverrideModule;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.extras.codecs.enums.EnumNameCodec;
import com.datastax.driver.extras.codecs.jdk8.InstantCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.ebean.Ebean;
import io.ebean.EbeanServer;
import models.internal.Context;
import models.internal.Features;
import models.internal.Operator;
import models.internal.impl.DefaultFeatures;
import play.Environment;
import play.api.Configuration;
import play.api.db.evolutions.DynamicEvolutions;
import play.api.libs.json.JsonParserSettings;
import play.api.libs.json.jackson.PlayJsonModule;
import play.db.ebean.EbeanConfig;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import scala.concurrent.duration.FiniteDuration;

import java.net.URI;
import java.time.Clock;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Module that defines the main bindings.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class MainModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Global.class).asEagerSingleton();
        bind(HealthProvider.class)
                .toProvider(ConfigTypedProvider.provider("http.healthProvider.type"))
                .in(Scopes.SINGLETON);

        // Databases
        // NOTE: These are not singletons because the lifecycle is controlled by
        // Ebean itself and we are just binding the instances by name through Guice
        bind(EbeanServer.class)
                .annotatedWith(Names.named("metrics_portal"))
                .toProvider(MetricsPortalEbeanServerProvider.class);

        // Repositories
        bind(OrganizationRepository.class)
                .toProvider(OrganizationRepositoryProvider.class)
                .asEagerSingleton();
        bind(HostRepository.class)
                .toProvider(HostRepositoryProvider.class)
                .asEagerSingleton();
        bind(AlertRepository.class)
                .toProvider(AlertRepositoryProvider.class)
                .asEagerSingleton();
        bind(ReportRepository.class)
                .toProvider(ReportRepositoryProvider.class)
                .asEagerSingleton();

        // Background tasks
        bind(ActorRef.class)
                .annotatedWith(Names.named("JvmMetricsCollector"))
                .toProvider(JvmMetricsCollectorProvider.class)
                .asEagerSingleton();
        bind(ActorRef.class)
                .annotatedWith(Names.named("HostProviderScheduler"))
                .toProvider(HostProviderProvider.class)
                .asEagerSingleton();
        bind(ActorRef.class)
                .annotatedWith(Names.named("ReportJobCoordinator"))
                .toProvider(ReportRepositoryJobCoordinatorProvider.class)
                .asEagerSingleton();
        bind(ActorRef.class)
                .annotatedWith(Names.named("RollupMetricsDiscovery"))
                .toProvider(RollupMetricsDiscoveryProvider.class)
                .asEagerSingleton();
        bind(ActorRef.class)
                .annotatedWith(Names.named("RollupGenerator"))
                .toProvider(RollupGeneratorProvider.class)
                .asEagerSingleton();
        bind(ActorRef.class)
                .annotatedWith(Names.named("RollupManager"))
                .toProvider(RollupManagerProvider.class)
                .asEagerSingleton();
        bind(ActorRef.class)
                .annotatedWith(Names.named("RollupExecutor"))
                .toProvider(RollupExecutorProvider.class)
                .asEagerSingleton();
        bind(ActorRef.class)
                .annotatedWith(Names.named("RollupManagerPool"))
                .toProvider(RollupManagerPoolProvider.class)
                .asEagerSingleton();

        // Reporting
        bind(ReportExecutionContext.class).asEagerSingleton();

        // Rollups
        bind(MetricsQueryConfig.class).to(MetricsQueryConfigImpl.class).asEagerSingleton();
    }

    @Singleton
    @Named("HostProviderProps")
    @Provides
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private Props getHostProviderProps(final HostProviderFactory provider, final Environment environment, final Config config) {
        return provider.create(config.getConfig("hostProvider"), ConfigurationHelper.getType(environment, config, "hostProvider.type"));
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private MetricsFactory getMetricsFactory(final Config configuration) {
        return new TsdMetricsFactory.Builder()
                .setClusterName(configuration.getString("metrics.cluster"))
                .setServiceName(configuration.getString("metrics.service"))
                .setSinks(Collections.singletonList(
                        new ApacheHttpSink.Builder()
                                .setUri(URI.create(configuration.getString("metrics.uri") + "/metrics/v1/application"))
                                .build()
                ))
                .build();
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private Features getFeatures(final Config configuration) {
        return new DefaultFeatures(configuration);
    }

    @Provides
    @Singleton
    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "Invoked reflectively by Guice")
    private DevToolsFactory provideChromeDevToolsFactory(final Config config, final ObjectMapper mapper) {
        return new DefaultDevToolsFactory.Builder()
                .setConfig(config.getConfig("chrome"))
                .setObjectMapper(mapper)
                .build();
    }


    @Provides
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private CodecRegistry provideCodecRegistry() {
        final CodecRegistry registry = CodecRegistry.DEFAULT_INSTANCE;
        registry.register(InstantCodec.instance);
        registry.register(new EnumNameCodec<>(Operator.class));
        registry.register(new EnumNameCodec<>(Context.class));
        return registry;
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private KairosDbClient provideKairosDbClient(
            final ActorSystem actorSystem,
            final ObjectMapper mapper,
            final Config configuration) {
        return new KairosDbClientImpl.Builder()
                .setActorSystem(actorSystem)
                .setMapper(mapper)
                .setUri(URI.create(configuration.getString("kairosdb.uri")))
                .setReadTimeout(ConfigurationHelper.getFiniteDuration(configuration, "kairosdb.timeout"))
                .build();
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private QueryExecutorRegistry provideQueryExecutorRegistry(
            final Config configuration,
            final Injector injector,
            final Environment environment) {
        final ImmutableMap.Builder<String, QueryExecutor> registryMapBuilder = ImmutableMap.builder();
        final Config executorsConfig = configuration.getConfig("query.executors");
        final Set<String> keys = executorsConfig.root().keySet();
        for (final String key: keys) {
            final Config subconfig = executorsConfig.getConfig(key);
            final Injector childInjector = injector.createChildInjector(new ConfigurationOverrideModule(subconfig));
            final Class<? extends QueryExecutor> clazz = ConfigurationHelper.getType(environment, subconfig, "type");
            registryMapBuilder.put(key, childInjector.getInstance(clazz));
        }
        return new QueryExecutorRegistry.Builder()
                .setExecutors(registryMapBuilder.build())
                .build();
    }

    //Note: This is essentially the same as Play's ObjectMapperModule, but uses the Commons ObjectMapperFactory
    //  instance as the base
    @Singleton
    @Provides
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private ObjectMapper provideObjectMapper(
            final ApplicationLifecycle lifecycle,
            final ActorSystem actorSystem) {
        final SimpleModule customModule = new SimpleModule();
        customModule.addDeserializer(
                com.arpnetworking.kairos.client.models.TimeUnit.class,
                EnumerationDeserializer.newInstance(
                        com.arpnetworking.kairos.client.models.TimeUnit.class,
                        EnumerationDeserializerStrategyUsingToUpperCase.newInstance()));
        customModule.addDeserializer(
                SamplingUnit.class,
                EnumerationDeserializer.newInstance(
                        SamplingUnit.class,
                        EnumerationDeserializerStrategyUsingToUpperCase.newInstance()));
        customModule.addDeserializer(
                Metric.Order.class,
                EnumerationDeserializer.newInstance(
                        Metric.Order.class,
                        EnumerationDeserializerStrategyUsingToUpperCase.newInstance()));
        final ObjectMapper objectMapper = ObjectMapperFactory.createInstance();
        objectMapper.registerModule(new PlayJsonModule(JsonParserSettings.apply()));
        objectMapper.registerModule(new AkkaModule(actorSystem));
        objectMapper.registerModule(customModule);
        Json.setObjectMapper(objectMapper);
        lifecycle.addStopHook(() -> {
            Json.setObjectMapper(null);
            return CompletableFuture.completedFuture(null);
        });

        return objectMapper;
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private Clock provideClock() {
        return Clock.systemUTC();
    }

    @Provides
    @Singleton
    @Named("job-execution-shard-region")
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private ActorRef provideJobExecutorShardRegion(
            final ActorSystem system,
            final Injector injector,
            final JobMessageExtractor extractor,
            final Clock clock,
            final PeriodicMetrics periodicMetrics) {
        final ClusterSharding clusterSharding = ClusterSharding.get(system);
        return clusterSharding.start(
                "JobExecutor",
                JobExecutorActor.props(injector, clock, periodicMetrics),
                ClusterShardingSettings.create(system).withRememberEntities(true),
                extractor,
                new ParallelLeastShardAllocationStrategy(
                        100,
                        3,
                        Optional.of(system.actorSelection("/user/cluster-status"))),
                PoisonPill.getInstance());
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private PeriodicMetrics providePeriodicMetrics(final MetricsFactory metricsFactory, final ActorSystem actorSystem) {
        final TsdPeriodicMetrics periodicMetrics = new TsdPeriodicMetrics.Builder()
                .setMetricsFactory(metricsFactory)
                .setPollingExecutor(actorSystem.dispatcher())
                .build();
        final FiniteDuration delay = FiniteDuration.apply(1, TimeUnit.SECONDS);
        actorSystem.scheduler().schedule(delay, delay, periodicMetrics, actorSystem.dispatcher());
        return periodicMetrics;
    }

    @Provides
    @com.google.inject.Singleton
    @com.google.inject.name.Named("status")
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private ActorRef provideStatusActor(
            final ActorSystem system,
            final MetricsFactory metricsFactory) {
        final Cluster cluster = Cluster.get(system);
        final ActorRef clusterStatusCache = system.actorOf(ClusterStatusCacheActor.props(cluster, metricsFactory), "cluster-status");
        return system.actorOf(StatusActor.props(cluster, clusterStatusCache), "status");
    }

    private static final class MetricsPortalEbeanServerProvider implements Provider<EbeanServer> {
        @Inject
        MetricsPortalEbeanServerProvider(
                final Configuration configuration,
                final DynamicEvolutions dynamicEvolutions,
                final EbeanConfig ebeanConfig) {
            // Constructor arguments injected for dependency resolution only
        }

        @Override
        public EbeanServer get() {
            return Ebean.getServer("metrics_portal");
        }
    }

    private static final class OrganizationRepositoryProvider implements Provider<OrganizationRepository> {
        @Inject
        OrganizationRepositoryProvider(
                final Injector injector,
                final Environment environment,
                final Config configuration,
                final ApplicationLifecycle lifecycle) {
            _injector = injector;
            _environment = environment;
            _configuration = configuration;
            _lifecycle = lifecycle;
        }

        @Override
        public OrganizationRepository get() {
            final OrganizationRepository organizationRepository = _injector.getInstance(
                    ConfigurationHelper.<OrganizationRepository>getType(_environment, _configuration, "organizationRepository.type"));
            organizationRepository.open();
            _lifecycle.addStopHook(
                    () -> {
                        organizationRepository.close();
                        return CompletableFuture.completedFuture(null);
                    });
            return organizationRepository;
        }

        private final Injector _injector;
        private final Environment _environment;
        private final Config _configuration;
        private final ApplicationLifecycle _lifecycle;
    }

    private static final class HostRepositoryProvider implements Provider<HostRepository> {

        @Inject
        HostRepositoryProvider(
                final Injector injector,
                final Environment environment,
                final Config configuration,
                final ApplicationLifecycle lifecycle) {
            _injector = injector;
            _environment = environment;
            _configuration = configuration;
            _lifecycle = lifecycle;
        }

        @Override
        public HostRepository get() {
            final HostRepository hostRepository = _injector.getInstance(
                    ConfigurationHelper.<HostRepository>getType(_environment, _configuration, "hostRepository.type"));
            hostRepository.open();
            _lifecycle.addStopHook(
                    () -> {
                        hostRepository.close();
                        return CompletableFuture.completedFuture(null);
                    });
            return hostRepository;
        }

        private final Injector _injector;
        private final Environment _environment;
        private final Config _configuration;
        private final ApplicationLifecycle _lifecycle;
    }

    private static final class AlertRepositoryProvider implements Provider<AlertRepository> {

        @Inject
        AlertRepositoryProvider(
                final Injector injector,
                final Environment environment,
                final Config configuration,
                final ApplicationLifecycle lifecycle) {
            _injector = injector;
            _environment = environment;
            _configuration = configuration;
            _lifecycle = lifecycle;
        }

        @Override
        public AlertRepository get() {
            final AlertRepository alertRepository = _injector.getInstance(
                    ConfigurationHelper.<AlertRepository>getType(_environment, _configuration, "alertRepository.type"));
            alertRepository.open();
            _lifecycle.addStopHook(
                    () -> {
                        alertRepository.close();
                        return CompletableFuture.completedFuture(null);
                    });
            return alertRepository;
        }

        private final Injector _injector;
        private final Environment _environment;
        private final Config _configuration;
        private final ApplicationLifecycle _lifecycle;
    }

    private static final class ReportRepositoryProvider implements Provider<ReportRepository> {
        @Inject
        ReportRepositoryProvider(
                final Injector injector,
                final Environment environment,
                final Config configuration,
                final ApplicationLifecycle lifecycle) {
            _injector = injector;
            _environment = environment;
            _configuration = configuration;
            _lifecycle = lifecycle;
        }

        @Override
        public ReportRepository get() {
            final ReportRepository reportRepository = _injector.getInstance(
                    ConfigurationHelper.<ReportRepository>getType(_environment, _configuration, "reportRepository.type"));
            reportRepository.open();
            _lifecycle.addStopHook(
                    () -> {
                        reportRepository.close();
                        return CompletableFuture.completedFuture(null);
                    });
            return reportRepository;
        }

        private final Injector _injector;
        private final Environment _environment;
        private final Config _configuration;
        private final ApplicationLifecycle _lifecycle;
    }

    private static final class HostProviderProvider implements Provider<ActorRef> {
        @Inject
        HostProviderProvider(
                final ActorSystem system,
                @Named("HostProviderProps") final Props hostProviderProps) {
            _system = system;
            _hostProviderProps = hostProviderProps;
        }

        @Override
        public ActorRef get() {
            final Cluster cluster = Cluster.get(_system);
            // Start a singleton instance of the scheduler on a "host_indexer" node in the cluster.
            if (cluster.selfRoles().contains(INDEXER_ROLE)) {
                return _system.actorOf(ClusterSingletonManager.props(
                        _hostProviderProps,
                        PoisonPill.getInstance(),
                        ClusterSingletonManagerSettings.create(_system).withRole(INDEXER_ROLE)),
                        "host-provider-scheduler");
            }
            return null;
        }

        private final ActorSystem _system;
        private final Props _hostProviderProps;

        private static final String INDEXER_ROLE = "host_indexer";
    }

    private static final class ReportRepositoryJobCoordinatorProvider implements Provider<ActorRef> {
        @Inject
        ReportRepositoryJobCoordinatorProvider(
                final ActorSystem system,
                final Injector injector,
                final OrganizationRepository organizationRepository,
                @Named("job-execution-shard-region")
                final ActorRef executorRegion,
                final PeriodicMetrics periodicMetrics) {
            _system = system;
            _injector = injector;
            _organizationRepository = organizationRepository;
            _executorRegion = executorRegion;
            _periodicMetrics = periodicMetrics;
        }

        @Override
        public ActorRef get() {
            final Cluster cluster = Cluster.get(_system);
            // Start a singleton instance of the scheduler on a "host_indexer" node in the cluster.
            if (cluster.selfRoles().contains(ANTI_ENTROPY_ROLE)) {
                return _system.actorOf(ClusterSingletonManager.props(
                        JobCoordinator.props(_injector,
                                ReportRepository.class,
                                DatabaseReportExecutionRepository.class,
                                _organizationRepository,
                                _executorRegion,
                                _periodicMetrics),
                        PoisonPill.getInstance(),
                        ClusterSingletonManagerSettings.create(_system).withRole(ANTI_ENTROPY_ROLE)),
                        "ReportJobCoordinator");
            }
            return null;
        }

        private final ActorSystem _system;
        private final Injector _injector;
        private final OrganizationRepository _organizationRepository;
        private final ActorRef _executorRegion;
        private final PeriodicMetrics _periodicMetrics;

        private static final String ANTI_ENTROPY_ROLE = "report_repository_anti_entropy";
    }

    private static final class RollupGeneratorProvider implements Provider<ActorRef> {
        @Inject
        RollupGeneratorProvider(
                final Injector injector,
                final ActorSystem system,
                final Config configuration,
                final Features features) {
            _injector = injector;
            _system = system;
            _configuration = configuration;
            _enabled = features.isRollupsEnabled();
        }

        @Override
        public ActorRef get() {
            final Cluster cluster = Cluster.get(_system);
            final int actorCount = _configuration.getInt("rollup.generator.count");
            if (_enabled && cluster.selfRoles().contains(RollupMetricsDiscoveryProvider.ROLLUP_METRICS_DISCOVERY_ROLE)) {
                for (int i = 0; i < actorCount; i++) {
                    _system.actorOf(GuiceActorCreator.props(_injector, RollupGenerator.class));
                }
            }
            return null;
        }

        private final Injector _injector;
        private final ActorSystem _system;
        private final Config _configuration;
        private final boolean _enabled;
    }

    private static final class RollupMetricsDiscoveryProvider implements Provider<ActorRef> {
        @Inject
        RollupMetricsDiscoveryProvider(
                final Injector injector,
                final ActorSystem system,
                final Features features) {
            _enabled = features.isRollupsEnabled();
            _injector = injector;
            _system = system;
        }

        @Override
        public ActorRef get() {
            final Cluster cluster = Cluster.get(_system);
            if (_enabled && cluster.selfRoles().contains(ROLLUP_METRICS_DISCOVERY_ROLE)) {
                final ActorRef manager = _system.actorOf(ClusterSingletonManager.props(
                        GuiceActorCreator.props(_injector, MetricsDiscovery.class),
                        PoisonPill.getInstance(),
                        ClusterSingletonManagerSettings.create(_system).withRole(ROLLUP_METRICS_DISCOVERY_ROLE)),
                        "rollup-metrics-discovery"
                );
                return _system.actorOf(ClusterSingletonProxy.props(
                        manager.path().toStringWithoutAddress(),
                        ClusterSingletonProxySettings.create(_system)));
            }
            return _system.actorOf(Props.create(NoopActor.class));
        }

        private final boolean _enabled;
        private final Injector _injector;
        private final ActorSystem _system;

        static final String ROLLUP_METRICS_DISCOVERY_ROLE = "rollup_metrics_discovery";
    }

    private static final class JvmMetricsCollectorProvider implements Provider<ActorRef> {
        @Inject
        JvmMetricsCollectorProvider(final Injector injector, final ActorSystem system) {
            _injector = injector;
            _system = system;
        }

        @Override
        public ActorRef get() {
            return _system.actorOf(GuiceActorCreator.props(_injector, JvmMetricsCollector.class));
        }

        private final Injector _injector;
        private final ActorSystem _system;
    }

    private static final class RollupManagerProvider implements Provider<ActorRef> {
        private final boolean _enabled;
        private final Injector _injector;
        private final ActorSystem _system;
        static final String ROLLUP_MANAGER_ROLE = "rollup_manager";

        @Inject
        RollupManagerProvider(final Injector injector, final ActorSystem system, final Features features) {
            _enabled = features.isRollupsEnabled();
            _injector = injector;
            _system = system;
        }

        @Override
        public ActorRef get() {
            final Cluster cluster = Cluster.get(_system);
            if (_enabled && cluster.selfRoles().contains(ROLLUP_MANAGER_ROLE)) {
                return _system.actorOf(GuiceActorCreator.props(_injector, RollupManager.class));
            }
            return _system.actorOf(Props.create(NoopActor.class));
        }
    }

    private static final class RollupManagerPoolProvider implements Provider<ActorRef> {
        private final boolean _enabled;
        private final Injector _injector;
        private final ActorSystem _system;
        static final String ROLLUP_MANAGER_ROLE = "rollup_manager";

        @Inject
        RollupManagerPoolProvider(final Injector injector, final ActorSystem system, final Features features) {
            _enabled = features.isRollupsEnabled();
            _injector = injector;
            _system = system;
        }

        @Override
        public ActorRef get() {
            final Cluster cluster = Cluster.get(_system);
            if (_enabled && cluster.selfRoles().contains(ROLLUP_MANAGER_ROLE)) {
                final Set<String> roles = Sets.newHashSet(ROLLUP_MANAGER_ROLE);
                return _system.actorOf(new ClusterRouterPool(
                                new ConsistentHashingPool(0),
                                new ClusterRouterPoolSettings(
                                        10000,
                                        1,
                                        true,
                                        roles
                                ))
                                .props(GuiceActorCreator.props(_injector, RollupForwarder.class)),
                        "rollup-manager"
                );
            }
            return _system.actorOf(Props.create(NoopActor.class));
        }
    }

    private static final class RollupExecutorProvider implements Provider<ActorRef> {
        @Inject
        RollupExecutorProvider(
                final Injector injector,
                final ActorSystem system,
                final Config configuration,
                final Features features) {
            _injector = injector;
            _system = system;
            _configuration = configuration;
            _enabled = features.isRollupsEnabled();
        }

        @Override
        public ActorRef get() {
            final int actorCount = _configuration.getInt("rollup.executor.count");
            if (_enabled) {
                for (int i = 0; i < actorCount; i++) {
                    _system.actorOf(GuiceActorCreator.props(_injector, RollupExecutor.class));
                }
            }
            return null;
        }

        private final Injector _injector;
        private final ActorSystem _system;
        private final Config _configuration;
        private final boolean _enabled;
    }
}
