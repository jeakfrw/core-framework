package de.fearnixx.jeak.teamspeak.query;

import de.fearnixx.jeak.Main;
import de.fearnixx.jeak.event.IQueryEvent;
import de.fearnixx.jeak.event.IRawQueryEvent;
import de.fearnixx.jeak.event.query.QueryEvent;
import de.fearnixx.jeak.event.query.RawQueryEvent;
import de.fearnixx.jeak.service.teamspeak.IUserService;
import de.fearnixx.jeak.teamspeak.EventCaptions;
import de.fearnixx.jeak.teamspeak.PropertyKeys;
import de.fearnixx.jeak.teamspeak.data.IDataHolder;
import de.fearnixx.jeak.teamspeak.except.ConsistencyViolationException;
import de.fearnixx.jeak.teamspeak.except.QueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static de.fearnixx.jeak.event.IRawQueryEvent.IMessage;

public class StandardMessageMarshaller {

    private static final int FAILED_PERM_ID = Main.getProperty("jeak.queryDispatcher.insuffClientPermId", 2568);
    private static final Logger logger = LoggerFactory.getLogger(StandardMessageMarshaller.class);

    private final List<String> permissionFails = new LinkedList<>();
    private final AtomicInteger lastNotificationHash = new AtomicInteger();
    private final AtomicReference<IMessage.INotification> lastEditEvent = new AtomicReference<>();
    private final IUserService userSvc;

    private static final List<String> STD_CHANNELEDIT_PROPS = Arrays.asList(
            PropertyKeys.Channel.ID,
            PropertyKeys.TextMessage.SOURCE_ID,
            PropertyKeys.TextMessage.SOURCE_NICKNAME,
            PropertyKeys.TextMessage.SOURCE_UID,
            "reasonid"
    );

    public StandardMessageMarshaller(IUserService userSvc) {
        this.userSvc = userSvc;
    }

    public IQueryEvent.IAnswer marshall(RawQueryEvent.Message.Answer event) {
        IQueryRequest request = event.getRequest();

        QueryEvent.Answer answer = new QueryEvent.Answer();
        answer.setConnection(event.getConnection());
        answer.setRequest(request);
        answer.setError(event.getError());
        answer.setRawReference(event);

        List<IDataHolder> dataHolders = new ArrayList<>(event.toList());
        answer.setChain(Collections.unmodifiableList(dataHolders));

        if (answer.getErrorCode() == FAILED_PERM_ID) {
            logger.warn("================================================================================");
            logger.warn("[PERMS] Insufficient permission error detected: {} - {}",
                    answer.getErrorCode(), answer.getErrorMessage());
            logger.warn("================================================================================");
            final var timeStamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            permissionFails.add(
                    String.format("\t- %s | %s -> %s (%s)", timeStamp, request.getCommand(), answer.getErrorMessage(), answer.getErrorCode())
            );
        }

        return answer;
    }

    public List<IQueryEvent.INotification> marshall(RawQueryEvent.Message.Notification event) {
        QueryEvent.Notification notification;
        String caption = event.getCaption().toLowerCase();
        int hashCode = event.getHashCode();

        boolean checkHash = true;

        switch (caption) {
            case EventCaptions.CLIENT_ENTER:
                notification = new QueryEvent.ClientEnter();
                break;

            case EventCaptions.CLIENT_LEFT:
                notification = new QueryEvent.ClientLeave();
                break;

            case EventCaptions.CLIENT_MOVED:
                notification = new QueryEvent.ClientMoved();
                break;

            case EventCaptions.CHANNEL_CREATED:
                notification = new QueryEvent.ChannelCreate();
                break;

            case EventCaptions.CHANNEL_EDITED:
                notification = checkEditSuspend(event);
                break;

            case EventCaptions.CHANNEL_EDITED_DESCR:
                checkHash = false;
                if (editResume(event)) {
                    return Collections.emptyList();
                } else {
                    notification = new QueryEvent.ChannelEditDescr();
                    break;
                }

            case EventCaptions.CHANNEL_EDITED_PASSWORD:
                checkHash = false;
                if (editResume(event)) {
                    return Collections.emptyList();
                } else {
                    notification = new QueryEvent.ChannelPasswordChanged();
                    break;
                }

            case EventCaptions.CHANNEL_MOVED:
                checkHash = false;
                notification = new QueryEvent.ChannelMoved();
                break;

            case EventCaptions.CHANNEL_DELETED:
                notification = new QueryEvent.ChannelDelete();
                break;

            case EventCaptions.TEXT_MESSAGE:
                checkHash = false;
                String strMode =
                        event.getProperty(PropertyKeys.TextMessage.TARGET_TYPE)
                                .orElseThrow(() -> new ConsistencyViolationException("TextMessage event without mode ID received!"));

                int mode = Integer.parseInt(strMode);
                switch (mode) {
                    case 1:
                        notification = new QueryEvent.ClientTextMessage(userSvc);
                        break;
                    case 2:
                        notification = new QueryEvent.ChannelTextMessage(userSvc);
                        break;
                    case 3:
                        notification = new QueryEvent.ServerTextMessage(userSvc);
                        break;
                    default:
                        throw new QueryException("Unknown message targetMode: " + mode);
                }
                break;

            default:
                notification = null;
                logger.warn("Unknown event encountered: {}", caption);
        }

        // === Possible valid skips === //
        if (notification == null) {
            logger.debug("No event type determined. Skipping dispatching.");
            return Collections.emptyList();
        } else if (checkHash && hashCode == lastNotificationHash.get()) {
            logger.debug("Dropping duplicate {}", caption);
            return Collections.emptyList();
        }
        lastNotificationHash.set(hashCode);

        notification.setConnection(event.getConnection());
        notification.setCaption(caption);

        final List<IQueryEvent.INotification> notifications = new LinkedList<>();
        // Loop through chained notifications.
        // Handling inside plugins will be simpler if we do the loop.
        // (Note for future: This may not work asynchronously)
        RawQueryEvent.Message msg = event;
        do {
            notification.merge(msg);
            notifications.add(notification);
        } while ((msg = msg.getNext()) != null);
        return notifications;
    }

    private QueryEvent.ChannelEdit checkEditSuspend(IRawQueryEvent.IMessage.INotification event) {
        final Map<String, String> deltas = new HashMap<>();
        boolean deltaFound = false;
        for (String key : event.getValues().keySet()) {
            if (!STD_CHANNELEDIT_PROPS.contains(key)) {
                logger.debug("ChannelEdit delta found: {}", key);
                deltaFound = true;
                deltas.put(key, event.getValues().get(key));
            }
        }

        lastEditEvent.set(event);
        if (!deltaFound) {
            logger.debug("Intermitting channelEdit event. No delta found.");
            return null;
        } else {
            return new QueryEvent.ChannelEdit(deltas);
        }
    }

    private boolean editResume(IRawQueryEvent.IMessage.INotification event) {
        if (lastEditEvent.get() == null) {
            logger.error("Failed to resume channelEdit event! No pending event found!");
            return true;
        } else {
            // Merge with last event
            // This allows us to capture multi-edits across normal properties and descr/password
            event.merge(lastEditEvent.get());
            return false;
        }
    }

    public List<String> getPermissionFails() {
        return Collections.unmodifiableList(permissionFails);
    }
}
