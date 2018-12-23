package de.fearnixx.t3.teamspeak.query;

import de.fearnixx.t3.event.IRawQueryEvent;
import de.fearnixx.t3.reflect.IInjectionService;
import de.fearnixx.t3.reflect.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class QueryConnectionAccessor extends AbstractQueryConnection implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(QueryConnectionAccessor.class);

    @Inject
    public IInjectionService injectionService;

    private QueryEventDispatcher dispatcher;
    private TS3Connection connection;

    private boolean terminated = false;

    public void initialize(InputStream in, OutputStream out) {
        if (connection != null) {
            throw new IllegalStateException("Cannot re-run already used connection!");
        }

        dispatcher = injectionService.injectInto(new QueryEventDispatcher());
        connection = new TS3Connection(in, out, this::onAnswer, this::onNotification);
        injectionService.injectInto(connection);
    }

    @Override
    public void run() {
        sendRequest(whoAmIRequest);

        try {
            read(connection);
        } catch (Exception e) {
            logger.error("Fatal error occurred while reading the connection!", e);
        }
    }

    private void read(TS3Connection connection) {
        while (!terminated) {
            try {
                connection.read();
            } catch (IOException e) {
                logger.error("Failed to read from connection.", e);
                return;
            }
        }
    }

    private void onAnswer(IRawQueryEvent.IMessage.IAnswer event) {
        dispatcher.dispatchAnswer(event);
    }

    private void onNotification(IRawQueryEvent.IMessage.INotification event) {
        dispatcher.dispatchNotification(event);
    }

    @Override
    public void sendRequest(IQueryRequest request) {
        connection.sendRequest(request);
    }

    public void shutdown() {
        try {
            terminated = true;
            connection.close();
        } catch (IOException e) {
            logger.warn("Error while trying to close connection.", e);
        }
    }
}
