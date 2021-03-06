package de.fearnixx.jeak.teamspeak.cache;

import de.fearnixx.jeak.event.IQueryEvent;
import de.fearnixx.jeak.reflect.Listener;
import de.fearnixx.jeak.teamspeak.data.TS3Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelUpdateWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ChannelUpdateWatcher.class);

    private final Object LOCK;
    private final DataCache cache;

    public ChannelUpdateWatcher(Object lock, DataCache cache) {
        this.LOCK = lock;
        this.cache = cache;
    }

    @Listener(order = Listener.Orders.LATEST)
    public void afterChannelEdited(IQueryEvent.INotification.IChannelEdited event) {
        Integer channelId = event.getTarget().getID();

        synchronized (LOCK) {
            TS3Channel target = cache.unsafeGetChannels().getOrDefault(channelId, null);

            if (target == null) {
                logger.info("Cannot update channel after edit: Not yet cached.");
                return;
            }

            event.getChanges().forEach((key, value) -> {
                String oldValue = target.getProperty(key).orElse(null);
                logger.debug("Updating property \"{}\": \"{}\" -> \"{}\"", key, oldValue, value);
                target.setProperty(key, value);
            });
        }
    }
}
