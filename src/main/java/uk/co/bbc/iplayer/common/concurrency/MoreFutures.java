package uk.co.bbc.iplayer.common.concurrency;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Patch for 'aggregated futures' (called within Guava's successfulAsList) that never return by enforcing timeout.
 * If timeout or interrupt exceptions are thrown an attempt will be made to cancel any futures
 * that are still running.
 */
public class MoreFutures {

    private final static boolean INTERRUPT_TASK = true;
    private static final Logger LOG = LoggerFactory.getLogger(MoreFutures.class);

    private MoreFutures() {
        throw new AssertionError();
    }

    public static <T> T await(ListenableFuture<? extends T> future, Duration timeOutCriteria) throws MoreFuturesException {

        try {
            return future.get(timeOutCriteria.getLength(), timeOutCriteria.getTimeUnit());

        } catch (InterruptedException e) {
            log("await", e);
            throw new MoreFuturesException("Future interrupted", e);

        } catch (ExecutionException e) {
            log("await", e);
            throw new MoreFuturesException("Future Execution Exception", e);
        } catch (TimeoutException e) {
            log("await", e);
            throw new MoreFuturesException("Timed out", e);
        } finally {
            if (!future.isDone()) {
                future.cancel(INTERRUPT_TASK);
            }
        }
    }

    public static <T> List<T> aggregate(Duration timeout, ListenableFuture<? extends T>... futures) throws MoreFuturesException {
        Iterable<? extends ListenableFuture<? extends T>> futuresList = Lists.newArrayList(futures);
        return aggregate(futuresList, timeout);
    }


    public static <T> List<T> aggregate(Iterable<? extends ListenableFuture<? extends T>> futures, Duration timeout) throws MoreFuturesException {
        try {
            return Futures.successfulAsList(futures).get(timeout.getLength(), timeout.getTimeUnit());

        } catch (InterruptedException e) {
            log("aggregate InterruptedException", e);
            throw new MoreFuturesException("Future interrupted", e);
        } catch (ExecutionException e) {
            log("aggregate ExecutionException", e);
            throw new MoreFuturesException("Execution exception", e);
        } catch (TimeoutException e) {
            log("aggregate TimeoutException", e);
            // Extract the successful futures and cancel futures that are stilling running
            return filterCompleteTasks(futures);
        } finally {
            cancelActiveFutures(futures);
        }
    }

    private static void log(String method, Exception e) {
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

    private static <T> List<T> filterCompleteTasks(Iterable<? extends ListenableFuture<? extends T>> futures) {

        List<T> completedTasks = Lists.newArrayList();
        for (ListenableFuture<? extends T> future : futures) {
            // completed and has not been terminated
            if (future.isDone() && !future.isCancelled()) {
                T value = null;
                try {
                    value = MoreFutures.await(future, Duration.inMilliSeconds(10));
                } catch (MoreFuturesException moreFuturesException) {
                    log("filterCompleteTasks", moreFuturesException);
                }
                completedTasks.add(value);
            }
        }

        return completedTasks;
    }

}