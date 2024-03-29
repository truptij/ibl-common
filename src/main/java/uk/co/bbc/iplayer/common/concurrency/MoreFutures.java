package uk.co.bbc.iplayer.common.concurrency;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Lists.newArrayList;
import static uk.co.bbc.iplayer.common.concurrency.Duration.inMilliSeconds;

public final class MoreFutures {

    public static final boolean INTERRUPT_TASK = true;
    public static final Duration DEFAULT_DURATION = Duration.create();
    private static final Logger LOG = LoggerFactory.getLogger(MoreFutures.class);

    private MoreFutures() {
        throw new AssertionError();
    }

    public static <T> PipeableFuture<T> pipe(ListenableFuture<T> future) {
        return PipeableFutureTask.create(future);
    }

    public static <T> T await(ListenableFuture<? extends T> future, Duration duration) throws MoreFuturesException {

        checkNotNull(future, "future must not be null");
        checkNotNull(duration, "duration must not be null");

        try {
            return future.get(duration.getLength(), duration.getTimeUnit());

        } catch (InterruptedException e) {
            return handleException(future, e, "Interrupted");

        } catch (ExecutionException e) {
            if(e.getCause() instanceof SocketTimeoutException) {
                return handleException(future, e, "Time out");
            }
            return handleException(future, e, "Execution Exception");

        } catch (TimeoutException e) {
            return handleException(future, e, "Time out");
        } finally {
            if (!future.isDone()) {
                future.cancel(INTERRUPT_TASK);
            }
        }
    }

    private static <T> T handleException(ListenableFuture<? extends T> future, Exception e, String exceptionDescription) throws MoreFuturesException {
        logExceptionMessage("await " + exceptionDescription, e);

        if (future instanceof IdentifyingFuture) {
            IdentifyingFuture identifyingFuture = (IdentifyingFuture) future;
            LOG.error("Future failed to complete: " + identifyingFuture.getDescriptor());
            identifyingFuture.getStatsDClient().increment("exception." + identifyingFuture.getStatsDescriptor() + "." + exceptionDescription.toLowerCase().replace(" ", ""));
            throw new MoreFuturesException(exceptionDescription + ": " + identifyingFuture.getDescriptor(), e);
        }

        throw new MoreFuturesException(exceptionDescription, e);
    }

    public static <T> T await(ListenableFuture<? extends T> future) throws MoreFuturesException {
        return await(future, DEFAULT_DURATION);
    }

    public static <I, O> ListenableFuture<O> transformIdentifying(ListenableFuture<I> input,
                                                       final Function<? super I, ? extends O> function) {
        ListenableFuture<O> transformedFuture = Futures.transform(input, function, MoreExecutors.sameThreadExecutor());

        if(input instanceof IdentifyingFuture) {
            IdentifyingFuture identifying = (IdentifyingFuture) input;
            return new IdentifyingFuture<O>(transformedFuture, identifying.getDescriptor(), identifying.getStatsDClient(), identifying.getStatsDescriptor());
        }

        return transformedFuture;
    }

    public static <T> List<T> aggregate(Iterable<? extends ListenableFuture<? extends T>> futures) throws MoreFuturesException {
        return aggregate(futures);
    }

    public static <T> List<T> aggregate(Iterable<? extends ListenableFuture<? extends T>> futures, Duration timeout) throws MoreFuturesException {
        return aggregate(futures, timeout, false);
    }

    public static <T> List<T> aggregateAll(Iterable<? extends ListenableFuture<? extends T>> futures, Duration timeout) throws MoreFuturesException {
        return aggregate(futures, timeout, true);
    }

    private static <T> List<T> aggregate(Iterable<? extends ListenableFuture<? extends T>> futures, Duration timeout, Boolean requireAllToCompleteWithinTimeout) throws MoreFuturesException {
        List<T> results = Collections.EMPTY_LIST;
        try {
            results = Futures.successfulAsList(futures).get(timeout.getLength(), timeout.getTimeUnit());
        } catch (InterruptedException e) {
            logExceptionMessage("aggregate InterruptedException", e);
            throw new MoreFuturesException("Future interrupted", e);

        } catch (ExecutionException e) {
            logExceptionMessage("aggregate ExecutionException", e);
            throw new MoreFuturesException("Execution exception", e);

        } catch (TimeoutException e) {
            logExceptionMessage("aggregate TimeoutException", e);
            if (requireAllToCompleteWithinTimeout) {
                throw new MoreFuturesException("Timeout exception", e);
            } else {
                // Extract the successful futures and cancel futures that are stilling running
                return filterCompleteTasks(futures);
            }
        } finally {
            cancelActiveFutures(futures);
            results = newArrayList(filter(results, notNull()));
        }

        return results;
    }

    private static void log(String message, Exception e) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(message , e);
        }
    }

    private static <T> List<T> filterCompleteTasks(Iterable<? extends ListenableFuture<? extends T>> futures) {

        List<T> completedTasks = Lists.newArrayList();
        for (ListenableFuture<? extends T> future : futures) {
            // completed and has not been terminated
            if (future.isDone() && !future.isCancelled()) {
                T value = null;
                try {
                    value = MoreFutures.await(future, inMilliSeconds(10));
                } catch (MoreFuturesException moreFuturesException) {
                    log("filterCompleteTasks", moreFuturesException);
                }
                completedTasks.add(value);
            }
        }

        return completedTasks;
    }

    public static <T, EX extends Exception> T awaitOrThrow(ListenableFuture<T> future, Class<EX> toThrow) throws EX {
        try {
            return await(future, DEFAULT_DURATION);

        } catch (MoreFuturesException e) {
            EX instance = initException(toThrow, e);
            throw instance;
        }
    }

    private static <EX> EX initException(Class<EX> toThrow, Throwable sourceException) {

        Constructor[] allConstructors = toThrow.getDeclaredConstructors();
        for (Constructor constructor : allConstructors) {

            List<Object> constructorParameters = Lists.newArrayList();

            Class<?>[] parameterTypes = constructor.getParameterTypes();

            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i].equals(String.class)) {
                    constructorParameters.add(sourceException.getMessage());

                } else if (parameterTypes[i].equals(Throwable.class)) {
                    constructorParameters.add(sourceException);
                }
            }

            try {
                return (EX) constructor.newInstance(constructorParameters.toArray());
            } catch (InstantiationException e) {
                continue;
            } catch (IllegalAccessException e) {
                continue;
            } catch (InvocationTargetException e) {
                continue;
            }
        }

        throw new IllegalArgumentException("Can't find constructor for " + toThrow);
    }

    private static void logExceptionMessage(String method, Exception e) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(method + "," + ExceptionUtils.getFullStackTrace(e));
        }
    }

    private static <T> void cancelActiveFutures(final Iterable<? extends ListenableFuture<? extends T>> futures) {
        synchronized (futures) {
            for (ListenableFuture future : futures) {
                if (!future.isDone()) {
                    future.cancel(INTERRUPT_TASK);
                }
            }
        }
    }
}