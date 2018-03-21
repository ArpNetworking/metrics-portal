/**
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
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import com.arpnetworking.commons.akka.GuiceActorCreator;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.impl.ApacheHttpSink;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.incubator.impl.TsdPeriodicMetrics;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.alerts.impl.AlertExecutor;
import com.arpnetworking.metrics.portal.alerts.impl.AlertMessageExtractor;
import com.arpnetworking.metrics.portal.alerts.impl.AlertScheduler;
import com.arpnetworking.metrics.portal.expressions.ExpressionRepository;
import com.arpnetworking.metrics.portal.health.HealthProvider;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.metrics.portal.hosts.impl.HostProviderFactory;
import com.arpnetworking.metrics.portal.notifications.NotificationRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationProvider;
import com.arpnetworking.metrics.util.JacksonCodec;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.utility.ParallelLeastShardAllocationStrategy;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.extras.codecs.enums.EnumNameCodec;
import com.datastax.driver.extras.codecs.joda.InstantCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import models.cassandra.NotificationRecipient;
import models.internal.Context;
import models.internal.Features;
import models.internal.Operator;
import models.internal.impl.DefaultFeatures;
import play.Environment;
import play.api.libs.json.jackson.PlayJsonModule$;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

/**
 * Module that defines the main bindings.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public class MainModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Global.class).asEagerSingleton();
        bind(HealthProvider.class)
                .toProvider(HealthProviderProvider.class);
        bind(ActorRef.class)
                .annotatedWith(Names.named("JvmMetricsCollector"))
                .toProvider(JvmMetricsCollectorProvider.class)
                .asEagerSingleton();
        bind(HostRepository.class)
                .toProvider(HostRepositoryProvider.class)
                .asEagerSingleton();
        bind(NotificationRepository.class)
                .toProvider(NotificationRepositoryProvider.class)
                .asEagerSingleton();
        bind(AlertRepository.class)
                .toProvider(AlertRepositoryProvider.class)
                .asEagerSingleton();
        bind(ExpressionRepository.class)
                .toProvider(ExpressionRepositoryProvider.class)
                .asEagerSingleton();
        bind(ActorRef.class)
                .annotatedWith(Names.named("HostProviderScheduler"))
                .toProvider(HostProviderProvider.class)
                .asEagerSingleton();
        bind(OrganizationProvider.class)
                .toProvider(OrganizationProviderProvider.class);
        bind(ActorRef.class)
                .annotatedWith(Names.named("AlertScheduler"))
                .toProvider(AlertExecutionSchedulerProvider.class)
                .asEagerSingleton();
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
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private CodecRegistry provideCodecRegistry(final ObjectMapper mapper) {
        final CodecRegistry registry = CodecRegistry.DEFAULT_INSTANCE;
        registry.register(InstantCodec.instance);
        registry.register(new EnumNameCodec<>(Operator.class));
        registry.register(new EnumNameCodec<>(Context.class));
        registry.register(new JacksonCodec<>(mapper, NotificationRecipient.class));
        return registry;
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private KairosDbClient provideKairosDbClient(
            final ActorSystem actorSystem,
            final ObjectMapper mapper,
            final Config configuration) {
        return new KairosDbClient.Builder()
                .setActorSystem(actorSystem)
                .setMapper(mapper)
                .setUri(URI.create(configuration.getString("kairosdb.uri")))
                .setReadTimeout(ConfigurationHelper.getFiniteDuration(configuration, "kairosdb.timeout"))
                .build();
    }

    //Note: This is essentially the same as Play's ObjectMapperModule, but uses the Commons ObjectMapperFactory
    //  instance as the base
    @Singleton
    @Provides
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private ObjectMapper provideObjectMapper(final ApplicationLifecycle lifecycle) {
        final ObjectMapper objectMapper = ObjectMapperFactory.createInstance();
        objectMapper.registerModule(PlayJsonModule$.MODULE$);
        Json.setObjectMapper(objectMapper);
        lifecycle.addStopHook(() -> {
            Json.setObjectMapper(null);
            return CompletableFuture.completedFuture(null);
        });

        return objectMapper;
    }

    @Provides
    @Singleton
    @Named("alert-execution-shard-region")
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private ActorRef provideAlertExecutorShardRegion(
            final ActorSystem system,
            final Injector injector,
            final AlertMessageExtractor extractor) {
        final ClusterSharding clusterSharding = ClusterSharding.get(system);
        return clusterSharding.start(
                "AlertExecutor",
                GuiceActorCreator.props(injector, AlertExecutor.class),
                ClusterShardingSettings.create(system).withRememberEntities(true),
                extractor,
                new ParallelLeastShardAllocationStrategy(
                        100,
                        3,
                        Optional.empty()),
                PoisonPill.getInstance());
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private Configuration provideFreemarkerConfig(final Config config) throws IOException {
        final Configuration cfg = new Configuration(Configuration.VERSION_2_3_27);
        cfg.setDirectoryForTemplateLoading(new File(config.getString("alerts.templateDirectory")));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        return cfg;
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private Session provideMailSession(final Config config) {
        final Properties props = new Properties();
        final Config mailConfig = config.getConfig("mail.properties");
        mailConfig.entrySet().forEach(entry -> props.put(entry.getKey(), entry.getValue().render()));

        if (!config.getIsNull("mail.user") || !config.getIsNull("mail.password")) {
            final Authenticator authenticator = new ConfiguredAuthenticated(config);
            return Session.getInstance(props, authenticator);
        } else {
            return Session.getInstance(props);
        }
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private PeriodicMetrics providePeriodicMetrics(final MetricsFactory metricsFactory, final ActorSystem actorSystem) {
        final TsdPeriodicMetrics periodicMetrics = new TsdPeriodicMetrics.Builder()
                .setMetricsFactory(metricsFactory)
                .setPollingExecutor(actorSystem.dispatcher())
                .build();
        final FiniteDuration delay = FiniteDuration.apply(500, TimeUnit.MILLISECONDS);
        actorSystem.scheduler().schedule(delay, delay, periodicMetrics, actorSystem.dispatcher());
        return periodicMetrics;
    }

    private static final class OrganizationProviderProvider implements Provider<OrganizationProvider> {
        @Inject
        OrganizationProviderProvider(
                final Injector injector,
                final Environment environment,
                final Config configuration) {
            _injector = injector;
            _environment = environment;
            _configuration = configuration;
        }

        @Override
        public OrganizationProvider get() {
            return _injector.getInstance(
                    ConfigurationHelper.<OrganizationProvider>getType(_environment, _configuration, "organizationProvider.type"));
        }

        private final Injector _injector;
        private final Environment _environment;
        private final Config _configuration;
    }


    private static final class HealthProviderProvider implements Provider<HealthProvider> {

        @Inject
        HealthProviderProvider(
                final Injector injector,
                final Environment environment,
                final Config configuration) {
            _injector = injector;
            _environment = environment;
            _configuration = configuration;
        }

        @Override
        public HealthProvider get() {
            return _injector.getInstance(
                    ConfigurationHelper.<HealthProvider>getType(_environment, _configuration, "http.healthProvider.type"));
        }

        private final Injector _injector;
        private final Environment _environment;
        private final Config _configuration;
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

    private static final class NotificationRepositoryProvider implements Provider<NotificationRepository> {

        @Inject
        NotificationRepositoryProvider(
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
        public NotificationRepository get() {
            final NotificationRepository repository = _injector.getInstance(
                    ConfigurationHelper.<NotificationRepository>getType(_environment, _configuration, "notificationRepository.type"));
            repository.open();
            _lifecycle.addStopHook(
                    () -> {
                        repository.close();
                        return CompletableFuture.completedFuture(null);
                    });
            return repository;
        }

        private final Injector _injector;
        private final Environment _environment;
        private final Config _configuration;
        private final ApplicationLifecycle _lifecycle;
    }

    private static final class ExpressionRepositoryProvider implements Provider<ExpressionRepository> {

        @Inject
        ExpressionRepositoryProvider(
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
        public ExpressionRepository get() {
            final ExpressionRepository expressionRepository = _injector.getInstance(
                    ConfigurationHelper.<ExpressionRepository>getType(_environment, _configuration, "expressionRepository.type"));
            expressionRepository.open();
            _lifecycle.addStopHook(
                    () -> {
                        expressionRepository.close();
                        return CompletableFuture.completedFuture(null);
                    });
            return expressionRepository;
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

    private static final class HostProviderProvider implements Provider<ActorRef> {
        @Inject
        HostProviderProvider(
                final ActorSystem system,
                @Named("HostProviderProps")
                final Props hostProviderProps) {
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

    private static final class AlertExecutionSchedulerProvider implements Provider<ActorRef> {
        @Inject
        AlertExecutionSchedulerProvider(
                final ActorSystem system,
                final Injector injector) {
            _system = system;
            _injector = injector;
        }

        @Override
        public ActorRef get() {
            final Cluster cluster = Cluster.get(_system);
            // Start a singleton instance of the scheduler on a "alert_scheduler" node in the cluster.
            if (cluster.selfRoles().contains(ALERT_SCHEDULER_ROLE)) {
                return _system.actorOf(ClusterSingletonManager.props(
                        GuiceActorCreator.props(_injector, AlertScheduler.class),
                        PoisonPill.getInstance(),
                        ClusterSingletonManagerSettings.create(_system).withRole(ALERT_SCHEDULER_ROLE)),
                        "alert-execution-scheduler");
            }
            return null;
        }

        private final ActorSystem _system;
        private final Injector _injector;

        private static final String ALERT_SCHEDULER_ROLE = "alert_scheduler";
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

    private static class ConfiguredAuthenticated extends Authenticator {
        ConfiguredAuthenticated(final Config config) {
            _config = config;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(_config.getString("mail.user"), _config.getString("mail.password"));
        }

        private final Config _config;
    }
}
