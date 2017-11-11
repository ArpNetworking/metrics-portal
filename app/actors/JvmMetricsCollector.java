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
package actors;

import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.dispatch.Dispatcher;
import com.arpnetworking.logback.annotations.LogValue;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.jvm.ExecutorServiceMetricsRunnable;
import com.arpnetworking.metrics.jvm.JvmMetricsRunnable;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.LogValueMapFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.duration.FiniteDuration;
import scala.concurrent.forkjoin.ForkJoinPool;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Actor responsible for collecting JVM metrics on a periodic basis.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class JvmMetricsCollector extends AbstractActor {

    /**
     * Public constructor.
     *
     * @param configuration Play app configuration.
     * @param metricsFactory An instance of <code>MetricsFactory</code>.
     */
    @Inject
    public JvmMetricsCollector(
            final Config configuration,
            final MetricsFactory metricsFactory) {
        _interval = ConfigurationHelper.getFiniteDuration(configuration, "metrics.jvm.interval");
        _jvmMetricsRunnable = new JvmMetricsRunnable.Builder()
                .setMetricsFactory(metricsFactory)
                .setSwallowException(false) // Relying on the default akka supervisor strategy here.
                .build();
        _executorServiceMetricsRunnable = new ExecutorServiceMetricsRunnable.Builder()
                .setMetricsFactory(metricsFactory)
                .setSwallowException(false) // Relying on the default akka supervisor strategy here.
                .setExecutorServices(createExecutorServiceMap(context().system(), configuration))
                .build();
    }

    @Override
    public void preStart() {
        LOGGER.info()
                .setMessage("Starting JVM metrics collector actor.")
                .addData("actor", self())
                .log();
        _cancellable = getContext().system().scheduler().schedule(
                INITIAL_DELAY,
                _interval,
                self(),
                new CollectJvmMetrics(),
                getContext().dispatcher(),
                self());
    }

    @Override
    public void postStop() {
        LOGGER.info().setMessage("Stopping JVM metrics collection.").log();
        _cancellable.cancel();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CollectJvmMetrics.class, ignored -> {
                    _jvmMetricsRunnable.run();
                    _executorServiceMetricsRunnable.run();
                })
                .build();
    }

    /**
     * Generate a Steno log compatible representation.
     *
     * @return Steno log compatible representation.
     */
    @LogValue
    public Object toLogValue() {
        return LogValueMapFactory.builder(this)
                .put("interval", _interval)
                .put("jvmMetricsRunnable", _jvmMetricsRunnable)
                .put("executorServiceMetricsRunnable", _executorServiceMetricsRunnable)
                .build();
    }

    @Override
    public String toString() {
        return toLogValue().toString();
    }

    private Map<String, ExecutorService> createExecutorServiceMap(
            final ActorSystem actorSystem,
            final Config configuration) {
        final Map<String, ExecutorService> executorServices = Maps.newHashMap();

        // Add the default dispatcher
        if (configuration.getBoolean("metrics.jvm.dispatchers.includeDefaultDispatcher")) {
            addExecutorServiceFromExecutionContextExecutor(
                    executorServices,
                    "akka/default_dispatcher",
                    actorSystem.dispatcher());
        }

        // Add any other configured dispatchers
        for (final Object dispatcherName : configuration.getList("metrics.jvm.dispatchers.includeAdditionalDispatchers")) {
            if (dispatcherName instanceof String) {
                final String dispatcherNameAsString = (String) dispatcherName;
                addExecutorServiceFromExecutionContextExecutor(
                        executorServices,
                        "akka/" + dispatcherNameAsString.replaceAll("-", "_"),
                        actorSystem.dispatchers().lookup(dispatcherNameAsString)
                );
            } else {
                throw new IllegalArgumentException("Invalid dispatcher name: " + dispatcherName);
            }
        }

        return executorServices;
    }

    private void addExecutorServiceFromExecutionContextExecutor(
            final Map<String, ExecutorService> executorServices,
            final String name,
            final ExecutionContextExecutor executionContextExecutor) {
        if (executionContextExecutor instanceof Dispatcher) {
            final Dispatcher dispatcher = (Dispatcher) executionContextExecutor;
            addExecutorService(
                    executorServices,
                    name,
                    dispatcher.executorService().executor());
            // TODO(ville): Support other ExecutionContextExecutor types as appropriate
        } else {
            throw new IllegalArgumentException(
                    "Unsupported ExecutionContextExecutor type: " + executionContextExecutor.getClass().getName());
        }
    }

    private void addExecutorService(
            final Map<String, ExecutorService> executorServices,
            final String name,
            final ExecutorService executorService) {
        if (executorService instanceof scala.concurrent.forkjoin.ForkJoinPool) {
            // NOTE: This is deprecated in Scala 2.12 which will be adopted by Play 2.6 (maybe)
            // ^ Scala and hopefully Play will use Java's ForkJoinPool natively instead
            final scala.concurrent.forkjoin.ForkJoinPool scalaForkJoinPool =
                    (scala.concurrent.forkjoin.ForkJoinPool) executorService;
            executorServices.put(name, new ForkJoinPoolAdapter(scalaForkJoinPool));
        } else if (executorService instanceof java.util.concurrent.ForkJoinPool
                || executorService instanceof java.util.concurrent.ThreadPoolExecutor) {
            executorServices.put(name, executorService);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported ExecutorService type: " + executorService.getClass().getName());
        }
    }

    private Cancellable _cancellable;

    private final FiniteDuration _interval;
    private final Runnable _jvmMetricsRunnable;
    private final Runnable _executorServiceMetricsRunnable;

    private static final FiniteDuration INITIAL_DELAY = FiniteDuration.Zero();
    private static final Logger LOGGER = LoggerFactory.getLogger(JvmMetricsCollector.class);

    /**
     * Message class to collect JVM metrics.
     *
     * @author Deepika Misra (deepika at groupon dot com)
     */
    private static final class CollectJvmMetrics {

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", Integer.toHexString(System.identityHashCode(this)))
                    .add("class", this.getClass())
                    .toString();
        }

        private CollectJvmMetrics() { }
    }

    /**
     * This is a partial adapter to enable instrumentation of Scala's
     * <code>ForkJoinPool</code> as if it were a Java <code>ForkJoinPool</code>.
     */
    private static final class ForkJoinPoolAdapter extends java.util.concurrent.ForkJoinPool {

        ForkJoinPoolAdapter(final ForkJoinPool scalaForkJoinPool) {
            _scalaForkJoinPool = scalaForkJoinPool;
        }

        private final ForkJoinPool _scalaForkJoinPool;

        @Override
        public <T> T invoke(final ForkJoinTask<T> task) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public void execute(final ForkJoinTask<?> task) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public void execute(final Runnable task) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public <T> ForkJoinTask<T> submit(final ForkJoinTask<T> task) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public <T> ForkJoinTask<T> submit(final Callable<T> task) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public <T> T invokeAny(
                final Collection<? extends Callable<T>> tasks,
                final long timeout,
                final TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public <T> ForkJoinTask<T> submit(final Runnable task, final T result) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public ForkJoinTask<?> submit(final Runnable task) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public <T> List<Future<T>> invokeAll(
                final Collection<? extends Callable<T>> tasks,
                final long timeout,
                final TimeUnit unit)
                throws InterruptedException {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public ForkJoinWorkerThreadFactory getFactory() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public int getParallelism() {
            return _scalaForkJoinPool.getParallelism();
        }

        @Override
        public int getPoolSize() {
            return _scalaForkJoinPool.getPoolSize();
        }

        @Override
        public boolean getAsyncMode() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public int getRunningThreadCount() {
            return _scalaForkJoinPool.getRunningThreadCount();
        }

        @Override
        public int getActiveThreadCount() {
            return _scalaForkJoinPool.getActiveThreadCount();
        }

        @Override
        public boolean isQuiescent() {
            return _scalaForkJoinPool.isQuiescent();
        }

        @Override
        public long getStealCount() {
            return _scalaForkJoinPool.getStealCount();
        }

        @Override
        public long getQueuedTaskCount() {
            return _scalaForkJoinPool.getQueuedTaskCount();
        }

        @Override
        public int getQueuedSubmissionCount() {
            return _scalaForkJoinPool.getQueuedSubmissionCount();
        }

        @Override
        public boolean hasQueuedSubmissions() {
            return _scalaForkJoinPool.hasQueuedSubmissions();
        }

        @Override
        protected ForkJoinTask<?> pollSubmission() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        protected int drainTasksTo(final Collection<? super ForkJoinTask<?>> c) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public String toString() {
            return _scalaForkJoinPool.toString();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public boolean isTerminating() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public boolean isShutdown() {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        public boolean awaitQuiescence(final long timeout, final TimeUnit unit) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(final Callable<T> callable) {
            throw new UnsupportedOperationException("This adapter only supports instrumentation");
        }
    }
}
