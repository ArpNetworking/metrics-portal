package com.arpnetworking.metrics.portal.scheduling.mocks;

import akka.actor.ActorRef;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.scheduling.Job;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DummyJob<T> implements Job<T> {
    private final UUID _uuid;
    private final Schedule _schedule;
    private final Optional<T> _result;
    private final Optional<Throwable> _error;
    private final CompletionStage<?> _blocker;

    private DummyJob(final Builder<T> builder) {
        _uuid = builder._uuid;
        _schedule = builder._schedule;
        _result = builder._result;
        _error = builder._error;
        _blocker = builder._blocker;
    }

    @Override
    public UUID getId() {
        return _uuid;
    }

    @Override
    public String getETag() {
        return _uuid.toString();
    }

    @Override
    public Schedule getSchedule() {
        return _schedule;
    }

    public Optional<T> getResult() {
        return _result;
    }

    public Optional<Throwable> getError() {
        return _error;
    }

    @Override
    public CompletionStage<T> execute(ActorRef scheduler, Instant scheduled) {
        return _blocker.thenCompose(whatever -> {
            CompletableFuture<T> future = new CompletableFuture<>();
            if (_result.isPresent()) {
                future.complete(_result.get());
            } else {
                future.completeExceptionally(_error.get());
            }
            return future;
        });
    }


    /**
     * Implementation of builder pattern for {@link DummyJob}.
     *
     * @param <T> The type of result of the job run by the recipient actor.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder<T> extends OvalBuilder<DummyJob<T>> {
        @NotNull
        private UUID _uuid = UUID.randomUUID();
        @NotNull
        private Schedule _schedule;
        @ValidateWithMethod(methodName = "validateResultXorError", parameterType = Object.class)
        private Optional<T> _result = Optional.empty();
        private Optional<Throwable> _error = Optional.empty();
        private CompletionStage<?> _blocker = CompletableFuture.completedFuture(null);

        /**
         * Public constructor.
         */
        public Builder() {
            super(DummyJob<T>::new);
        }

        public Builder<T> setId(final UUID uuid) {
            _uuid = uuid;
            return this;
        }

        public Builder<T> setSchedule(final Schedule schedule) {
            _schedule = schedule;
            return this;
        }

        public Builder<T> setOneOffSchedule(final Instant runAt) {
            _schedule = new OneOffSchedule.Builder().setRunAtAndAfter(runAt).build();
            return this;
        }

        public Builder<T> setResult(final T result) {
            _result = Optional.of(result);
            _error = Optional.empty();
            return this;
        }

        public Builder<T> setError(final Throwable error) {
            _result = Optional.empty();
            _error = Optional.of(error);
            return this;
        }

        public Builder<T> setBlocker(final CompletionStage<?> blocker) {
            _blocker = blocker;
            return this;
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by @ValidateWithMethod")
        private boolean validateResultXorError(final Object result) {
            return _result.isPresent() ^ _error.isPresent();
        }
    }
}
