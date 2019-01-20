package de.fearnixx.jeak.teamspeak.query;

import de.fearnixx.jeak.event.IRawQueryEvent;

import java.util.concurrent.TimeUnit;

/**
 * Created by MarkL4YG on 07-Jan-18
 */
public class PromisedRequest implements IQueryPromise {

    private IQueryRequest request;
    private IRawQueryEvent.IMessage.IAnswer answer;

    public PromisedRequest(IQueryRequest request) {
        this.request = request;
    }

    public IQueryRequest getRequest() {
        return request;
    }

    public synchronized IRawQueryEvent.IMessage.IAnswer getAnswer() {
        return answer;
    }

    @Override
    public synchronized boolean isDone() {
        return answer != null;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException("Query requests cannot be cancelled!");
    }

    @Override
    public IRawQueryEvent.IMessage.IAnswer get() {
        throw new UnsupportedOperationException("Query requests may not be blocking! If you really need to do this use #get(long, TimeUnit)");
    }

    @Override
    public IRawQueryEvent.IMessage.IAnswer get(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout == 0 || unit == null)
            return getAnswer();

        long cTimeout = unit.toMillis(timeout) * 4;
        while (!isDone() && cTimeout > 0) {
            Thread.sleep(250);
            cTimeout -= 250;
        }

        return getAnswer();
    }

    protected synchronized void setAnswer(IRawQueryEvent.IMessage.IAnswer event) {
        this.answer = event;
    }
}