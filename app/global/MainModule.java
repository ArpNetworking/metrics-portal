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
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import com.arpnetworking.commons.akka.GuiceActorCreator;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.impl.ApacheHttpSink;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.expressions.ExpressionRepository;
import com.arpnetworking.metrics.portal.health.HealthProvider;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.metrics.portal.hosts.impl.HostProviderFactory;
import com.arpnetworking.metrics.portal.organizations.OrganizationProvider;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import com.arpnetworking.metrics.portal.reports.impl.JobScheduler;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.utility.ConfigTypedProvider;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.extras.codecs.enums.EnumNameCodec;
import com.datastax.driver.extras.codecs.joda.InstantCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.Context;
import models.internal.Features;
import models.internal.Operator;
import models.internal.impl.DefaultFeatures;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;
import play.Environment;
import play.api.libs.json.jackson.PlayJsonModule$;
import play.inject.ApplicationLifecycle;
import play.libs.Json;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

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
                .toProvider(ConfigTypedProvider.provider("http.healthProvider.type"))
                .in(Scopes.SINGLETON);
        bind(OrganizationProvider.class)
                .toProvider(ConfigTypedProvider.provider("organizationProvider.type"))
                .in(Scopes.SINGLETON);
        bind(ActorRef.class)
                .annotatedWith(Names.named("JvmMetricsCollector"))
                .toProvider(JvmMetricsCollectorProvider.class)
                .asEagerSingleton();
        bind(HostRepository.class)
                .toProvider(HostRepositoryProvider.class)
                .asEagerSingleton();
        bind(JobRepository.class)
                .toProvider(ReportRepositoryProvider.class)
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
        bind(ActorRef.class)
                .annotatedWith(Names.named("JobScheduler"))
                .toProvider(JobSchedulerProvider.class)
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
    private Mailer getEmailTransport() {
        String host = System.getProperty("mail.smtp.host", "localhost");
        Integer port = Integer.parseInt(System.getProperty("mail.smtp.port", "25"));
        return MailerBuilder
                .withSMTPServer(host, port)
                .buildMailer();
    }

    @Provides
    @Singleton
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Guice
    private Features getFeatures(final Config configuration) {
        return new DefaultFeatures(configuration);
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

    private static final class ReportRepositoryProvider implements Provider<JobRepository> {

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
        public JobRepository get() {
            final JobRepository jobRepository = _injector.getInstance(
                    ConfigurationHelper.<JobRepository>getType(_environment, _configuration, "jobRepository.type"));
            jobRepository.open();
            _lifecycle.addStopHook(
                    () -> {
                        jobRepository.close();
                        return CompletableFuture.completedFuture(null);
                    });
            return jobRepository;
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

    private static final class JobSchedulerProvider implements Provider<ActorRef> {
        @Inject
        JobSchedulerProvider(
                final ActorSystem system,
                final Injector injector) {
            _system = system;
            _injector = injector;
        }
        @Override
        public ActorRef get() {
            final Cluster cluster = Cluster.get(_system);
            // Start a singleton instance of the scheduler on a "report_scheduler" node in the cluster.
            if (cluster.selfRoles().contains(REPORT_SCHEDULER_ROLE)) { // TODO(spencerpearson): file an issue for the wrong implementation of this that's already committed
                _system.actorOf(ClusterSingletonManager.props(
                        GuiceActorCreator.props(_injector, JobScheduler.class),
                        PoisonPill.getInstance(),
                        ClusterSingletonManagerSettings.create(_system).withRole(REPORT_SCHEDULER_ROLE)),
                        "report-execution-scheduler");
                return _system.actorOf(ClusterSingletonProxy.props(
                        "/user/report-execution-scheduler",
                        ClusterSingletonProxySettings.create(_system).withRole(REPORT_SCHEDULER_ROLE)));
            }
            return null;
        }
        private final ActorSystem _system;
        private final Injector _injector;
        private static final String REPORT_SCHEDULER_ROLE = "report_scheduler";
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
}
