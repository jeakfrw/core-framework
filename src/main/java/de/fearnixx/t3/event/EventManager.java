package de.fearnixx.t3.event;

import de.fearnixx.t3.reflect.listeners.ListenerContainer;
import de.mlessmann.logging.ILogReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MarkL4YG on 01.06.17.
 */
public class EventManager implements IEventManager {

    private ILogReceiver log;
    private final List<ListenerContainer> addedContainers;
    private final List<ListenerContainer> listeners;

    public EventManager(ILogReceiver log) {
        this.log = log;
        addedContainers = new ArrayList<>();
        listeners = new ArrayList<>();
    }

    @Override
    public void fireEvent(IEvent event) {
        // Run on a copy so adding new listeners during an event doesn't cause a dead-lock!
        // Also run on a temporary copy so firing events during events doesn't cause a dead-lock!
        final List<ListenerContainer> listeners2 = new ArrayList<>();
        synchronized (addedContainers) {
            listeners2.addAll(addedContainers);
        }
        synchronized (listeners) {
            listeners2.addAll(listeners);
        }
        log.finest("Sending event:", event.getClass().getSimpleName(), "to", listeners2.size(), "listeners");
        for (int i = 0; i < listeners2.size(); i++) {
            // Catch exceptions for each listener so a broken one doesn't break the whole event
            try {
                listeners2.get(i).fireEvent(event);
            } catch (Throwable e) {
                // Skip the invocation exception for readability
                if (e.getCause() != null) e = e.getCause();
                log.severe("Failed to pass event ", event.getClass().getSimpleName(), " to ", listeners2.get(i).getVictim().getClass().toGenericString(), e);
            }
        }
    }

    public void addContainer(ListenerContainer c) {
        synchronized (addedContainers) {
            addedContainers.add(c);
        }
    }

    public void registerListeners(Object... l) {
        for (Object o : l)
            registerListener(o);
    }

    public void registerListener(Object o) {
        synchronized (listeners) {
            if (listeners.stream().anyMatch(c -> c.getVictim() == o)) return;
            listeners.add(new ListenerContainer(o, log));
        }
    }
}
