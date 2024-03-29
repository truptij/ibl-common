package uk.co.bbc.iplayer.common.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.bbc.iplayer.common.utils.ExceptionUtil;

import java.util.concurrent.Callable;


public abstract class CheckedCallable<V> implements Callable<V> {
    static Logger LOG = LoggerFactory.getLogger(CheckedCallable.class);

    private String taskDescription;

    public CheckedCallable(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    @Override
    public final V call() throws Exception {
        try {
            V result = checkedCall();
            onSuccess();
            LOG.debug("Success: {}", taskDescription);
            return result;
        } catch (Exception e) {
            ExceptionUtil.logExceptionSummary(LOG, taskDescription, e);
            onFailure();
            throw e;
        }
    }

    public abstract V checkedCall() throws Exception;


    /**
     * override this method if you want to do anything extra once the thread completes successfully
     */
    public void onSuccess() {}

    /**
     * override this method if you want to do anything extra when the thread fails due to some exception
     */
    public void onFailure() {}
}
