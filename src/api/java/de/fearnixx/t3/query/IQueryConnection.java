package de.fearnixx.t3.query;

import de.fearnixx.t3.event.query.IQueryEvent;

import java.util.function.Consumer;

/**
 * Created by MarkL4YG on 10.06.17.
 */
public interface IQueryConnection {

    void sendRequest(IQueryRequest req);
    void sendRequest(IQueryRequest req, Consumer<IQueryEvent.IMessage> onDone);
}
